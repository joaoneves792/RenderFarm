import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.model.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

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
            // Chech if table exists
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

    static public void getMetrics(String fileName) {
        // 1. Should we only check metrics for the same file?
        // 2. If no metrics exists for current file, do we check the other files?
        HashMap<String, String> nameMap = new HashMap<String, String>();
        HashMap<String, Object> valueMap = new HashMap<String, Object>();
        nameMap.put("#fN", "fileName");
        valueMap.put(":fileName", fileName);

        QuerySpec querySpec = new QuerySpec()
                .withKeyConditionExpression("#fn = :fileName")
                .withNameMap(nameMap)
                .withValueMap(valueMap);

        ItemCollection<QueryOutcome> items = null;
        Iterator<Item> iterator = null;
        Item item = null;

        try {
            items = metricsTable.query(querySpec);

            iterator = items.iterator();
            while (iterator.hasNext()) {
                item = iterator.next();
                System.out.println(item.getNumber("wr") + ": " + item.getString("wc"));
            }

        }
        catch (Exception e) {
            System.err.println("Unable to query movies from 1985");
            System.err.println(e.getMessage());
        }
    }

    static public void saveMetrics(String fileName, int sc, int sr, int wc, int wr, int coff, int roff, int mc) {
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
            MetricsManager.getMetrics("file1");
        }
        catch(Exception e) {
            System.out.println(e);
        }

    }

}
