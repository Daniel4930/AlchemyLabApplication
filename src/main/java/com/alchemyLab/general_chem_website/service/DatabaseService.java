package com.alchemyLab.general_chem_website.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Service
public class DatabaseService {    
    @Value("${aws.service.region}")
    private String awsRegion;
    private Region region;

    @PostConstruct
    public void init() {
        region = Region.of(awsRegion);
    }

    @Value("${aws.dynamodb.tableName}")
    private String tableName;

    public String getTableName() { return tableName; }

    public DynamoDbClient startDatabaseClient() {
        return DynamoDbClient.builder().region(region).build();
    }

    public void writeDataToDynamoDb(PutItemRequest request) throws DynamoDbException {
        try (DynamoDbClient db = startDatabaseClient()) {
            db.putItem(request);
        } catch (DynamoDbException error) {
            System.err.printf("Error from %s: %s\n", error.getClass().getName(), error.getMessage());
        }
    }

    public Map<String, AttributeValue> getDataFromDynamoDb(GetItemRequest request) throws DynamoDbException {
        try (DynamoDbClient db = startDatabaseClient()) {
            Map<String, AttributeValue> item = db.getItem(request).item();
            return item != null ? item : new HashMap<>();
        } catch (DynamoDbException error) {
            System.err.printf("Get item failed (%s): %s\n", error.getClass().getName(), error.getMessage());
            return new HashMap<>();
        }
    }

    public void updateDataFromDynamoDb(UpdateItemRequest request) throws DynamoDbException {
        try (DynamoDbClient db = startDatabaseClient()) {
            db.updateItem(request);
        } catch (DynamoDbException error) {
            System.err.printf("Update item failed (%s): %s\n", error.getClass().getName(), error.getMessage());
        }
    }

    public Map<String, AttributeValue> createItemsForRequest(Map<String, Object> items) {
        Map<String, AttributeValue> itemMap = new HashMap<>();

        for (Map.Entry<String, Object> item : items.entrySet()) {
            String key = item.getKey();
            Object value = item.getValue();

            if (value instanceof String) {
                itemMap.put(key, AttributeValue.builder().s(value.toString()).build());
            }
        }
        return itemMap;
    }
}
