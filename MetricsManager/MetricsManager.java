import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.*;

import java.util.*;

public class MetricsManager {
    private static DynamoDB dynamoDB;
    private static Table metricsTable;

    public static void init() {
        // This requires a "credentials" files in your ~/.aws folder
        // containing aws-access-id and aws-secret
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder
                .standard()
                .withRegion(Regions.EU_WEST_1)
                .build();
		
        DynamoDB dynamoDB = new DynamoDB(client);
        String tableName = "metrics";
        
        try {
            // Check if table exists
            Table table = dynamoDB.getTable(tableName);
            table.describe();
            metricsTable = table;
            
        } catch (ResourceNotFoundException e) {
            // Create table with id as primary key,
            // Set fileName as sort-key to improve lookup
            metricsTable = dynamoDB.createTable(tableName,
                Arrays.asList(
                        new KeySchemaElement("id", KeyType.HASH),
                        new KeySchemaElement("fileName", KeyType.RANGE)
                ),
                Arrays.asList(
                        new AttributeDefinition("id", ScalarAttributeType.S),
                        new AttributeDefinition("fileName", ScalarAttributeType.S)
                ),
                new ProvisionedThroughput(10L, 10L)
            );
        }
        
        try {
            metricsTable.waitForActive();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    //Assuming that whomever uses this sets sc and sr similar to wc and wr
    static private double overlapRatio(int wcA, int wrA, int coffA, int roffA, int wcB, int wrB, int coffB, int roffB){
        int XA1 = coffA;
        int YA1 = roffA;
        int XA2 = XA1 + wcA;
        int YA2 = YA1 + wrA;

        int XB1 = coffB;
        int YB1 = roffB;
        int XB2 = XB1 + wcB;
        int YB2 = YB1 + wrB;

        double SA = wcA*wrA;
        double SB = wcB*wrB;

        double SI = Math.max(0, Math.min(XA2, XB2) - Math.max(XA1, XB1)) * Math.max(0, Math.min(YA2, YB2) - Math.max(YA1, YB1));

        double S = SA+SB-SI;

        return SI/S;
    }

    static public long getMetrics(String filename, int sc, int sr, int wc, int wr, int coff, int roff) {
        final double ACCEPT_OVERLAP_RATIO = 0.75;
        // 1. Should we only check metrics for the same file?
        // 2. If no metrics exists for current file, do we check the other files?
        ScanSpec scanSpec = new ScanSpec()
                .withFilterExpression("fileName = :fileName")
                .withValueMap(
                        new ValueMap()
                                .withString(":fileName", filename)
                );
                
        try {
            ItemCollection<ScanOutcome> items = metricsTable.scan(scanSpec);
            double maxOverlap = 0.0;
            Item bestMatch = null;

            Iterator<Item> iter = items.iterator();
            while(iter.hasNext()) {
                Item item = iter.next();
                double overlap = overlapRatio(wc, wr, coff, roff, item.getInt("wc"),
                        item.getInt("wr"), item.getInt("coff"), item.getInt("roff"));
                if(overlap > maxOverlap){
                    maxOverlap = overlap;
                    bestMatch = item;
                }
            }
            if(maxOverlap > ACCEPT_OVERLAP_RATIO && null != bestMatch)
                return bestMatch.getLong("mc");
        } catch (Exception e) {
            System.err.println("Unable to scan the table:");
            System.err.println(e.getMessage());
        }
        
        return -1;
    }

    //No need to synchronize, Table is ThreadSafe
    static public void saveMetrics(String fileName, int sc, int sr, int wc, int wr, int coff, int roff, long mc) {

        //Check for existing metrics
        //TODO dont just eliminate duplicates, but also similar results
        long existingMetrics = MetricsManager.getMetrics(fileName, sc, sr, wc, wr, coff, roff);
        if(existingMetrics > -1) {
            return;
        }
        
        String id = UUID.randomUUID().toString();
        Item item = new Item();
        item.withPrimaryKey("id", id);
        item.withString("fileName", fileName);
        item.withInt("sc", sc);
        item.withInt("sr", sr);
        item.withInt("wc", wc);
        item.withInt("wr", wr);
        item.withInt("coff", coff);
        item.withInt("roff", roff);
        item.withLong("mc", mc);
        metricsTable.putItem(item);
    }

    static public double estimateCost(long methodCount) {
        return 7 * Math.pow(10, -5) * methodCount;
    }

    public static void main(String[] args) {
        // This is just for testing.
        // Will remove when done.
        MetricsManager.init();
        try {
            long methodCount = MetricsManager.getMetrics("test01.txt",1000, 1000, 200, 250, 0,0);
            System.out.println(methodCount);
			
        } catch(Exception e) {
            System.out.println(e);
        }
    }

}
