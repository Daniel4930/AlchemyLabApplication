package com.alchemyLab.general_chem_website.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.alchemyLab.general_chem_website.model.ResponseUserInfo;
import com.alchemyLab.general_chem_website.model.User;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

@RestController
public class DatabaseController {

    @PostMapping("/auth/signin")
    public ResponseEntity<Map<String, Object>> checkUserLoginInfo(@RequestBody ResponseUserInfo payload) {
        String tableName = "GenChemWebsite_userInfo";
        HashMap<String, AttributeValue> key = new HashMap<>();
        key.put("email", AttributeValue.builder().s(payload.getEmail()).build());
        
        Region region = Region.US_EAST_1;
        final DynamoDbClient db = DynamoDbClient.builder().region(region).build();

        return getDynamoDBItem(db, tableName, key, payload.getPassword());
    }

    public static ResponseEntity<Map<String, Object>> getDynamoDBItem(DynamoDbClient db, String tableName, HashMap<String, AttributeValue> key, String password) {
        Map<String, Object> response = new HashMap<>();

        GetItemRequest request = GetItemRequest.builder()
            .key(key)
            .tableName(tableName)
            .build();

        try {
            Map<String, AttributeValue> returnedItem = db.getItem(request).item();
            if (returnedItem.isEmpty()) { //User doesn't exist in database
                System.out.format("\"No item found with the key %s!\n",key);
                response.put("status", "notFound");
                response.put("message", "User doesn't exist");
            } else {
                String storedHashedPassword = returnedItem.get("password").s();
                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
                boolean passwordMatch = encoder.matches(password, storedHashedPassword);

                if (!passwordMatch) {
                    response.put("status", "notFound");
                    response.put("message", "Incorrect password or email. Please try again");
                } else {
                    User info = new User();
                    info.setEmail(key.get("email").s());
                    info.setName(returnedItem.get("name").s());

                    response.put("status", "ok");
                    response.put("message", "Found user");
                    response.put("userData", info);
                }
            }
        } catch (DynamoDbException error) {
            System.err.println(error.getMessage());
            response.put("status", "Internal error");
            response.put("message", "Server is experience an internal error. Please try again");
        }
        db.close();
        return ResponseEntity.ok(response);
    }
}