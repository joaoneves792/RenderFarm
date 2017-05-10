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
    }

    static public int getMetrics(int sc, int sr, int wc, int wr, int coff, int roff) {
        // 1. Should we only check metrics for the same file?
        // 2. If no metrics exists for current file, do we check the other files?
        ScanSpec scanSpec = new ScanSpec()
                .withFilterExpression("" +
                        "sc = :sc and " +
                        "sr = :sr and " +
                        "wc = :wc and " +
                        "wr = :wr and " +
                        "coff = :coff and " +
                        "roff = :roff"
                        )
                .withValueMap(
                        new ValueMap()
                                .withInt(":sc", sc)
                                .withInt(":sr", sr)
                                .withInt(":wc", wc)
                                .withInt(":wr", wr)
                                .withInt(":coff", coff)
                                .withInt(":roff", roff)
                );
        try {
            ItemCollection<ScanOutcome> items = metricsTable.scan(scanSpec);
            Iterator<Item> iter = items.iterator();
            if (iter.hasNext()) {
                Item item = iter.next();
                return item.getInt("mc");
            }
        }

        catch (Exception e) {
            System.err.println("Unable to scan the table:");
            System.err.println(e.getMessage());
        }
        return -1;
    }

    static public void saveMetrics(String fileName, int sc, int sr, int wc, int wr, int coff, int roff, int mc) {
        int existingMetrics = MetricsManager.getMetrics(sc, sr, wc, wr, coff, roff);
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
        item.withInt("mc", mc);
        metricsTable.putItem(item);
    }

    static public double estimateCost(int methodCount) {
        return 7 * Math.pow(10, -5) * methodCount;
    }

    public static void main(String[] args) {
        // This is just for testing.
        // Will remove when done.
        MetricsManager.init();
        try {
            metricsTable.waitForActive();
            int methodCount = MetricsManager.getMetrics(1000, 1000, 200, 250, 0,0);
            System.out.println(methodCount);
        }
        catch(Exception e) {
            System.out.println(e);
        }
    }

}
