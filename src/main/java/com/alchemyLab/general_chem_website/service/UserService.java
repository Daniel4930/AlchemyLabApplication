package com.alchemyLab.general_chem_website.service;

import com.alchemyLab.general_chem_website.model.ResponseUserInfo;
import com.alchemyLab.general_chem_website.model.SignUpInfo;
import com.alchemyLab.general_chem_website.model.User;
import com.alchemyLab.general_chem_website.util.ResponseUtil;
import com.google.api.client.auth.openidconnect.IdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {
    private static final String tableName = "GenChemWebsite_userInfo";
    private static final Region region = Region.US_EAST_1;
    private static final String keyName = "email";

    private DynamoDbClient startDatabaseClient() {
        return DynamoDbClient.builder().region(region).build();
    }

    public ResponseEntity<Map<String, Object>> registerUser(SignUpInfo payload) {
        try (DynamoDbClient db = startDatabaseClient()) {
            Map<String, Object> existResponse = checkUserExist(db, payload.getEmail());

            if ("exist".equals(existResponse.get("message"))) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ResponseUtil.createResponse("Email already exists"));
            }

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            String hashedPassword = encoder.encode(payload.getPassword());

            Map<String, AttributeValue> itemValues = new HashMap<>();
            itemValues.put(keyName, AttributeValue.builder().s(payload.getEmail()).build());
            itemValues.put("name", AttributeValue.builder().s(payload.getName()).build());
            itemValues.put("password", AttributeValue.builder().s(hashedPassword).build());

            PutItemRequest request = PutItemRequest.builder().tableName(tableName).item(itemValues).build();
            db.putItem(request);

            User user = new User();
            user.setEmail(payload.getEmail());
            user.setName(payload.getName());

            Map<String, Object> response = ResponseUtil.createResponse("User created successfully");
            response.put("data", user);
            return ResponseEntity.ok(response);
        }
    }

    private Map<String, Object> checkUserExist(DynamoDbClient db, String emailVal) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put(keyName, AttributeValue.builder().s(emailVal).build());

        GetItemRequest request = GetItemRequest.builder().tableName(tableName).key(key).build();
        Map<String, Object> response = new HashMap<>();

        Map<String, AttributeValue> item = db.getItem(request).item();
        response.put("message", item.isEmpty() ? "not exist" : "exist");

        return response;
    }

    public ResponseEntity<Map<String, Object>> loginUser(ResponseUserInfo payload) {
        try (DynamoDbClient db = startDatabaseClient()) {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put(keyName, AttributeValue.builder().s(payload.getEmail()).build());

            GetItemRequest request = GetItemRequest.builder().tableName(tableName).key(key).build();
            Map<String, AttributeValue> item = db.getItem(request).item();

            if (item == null || item.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ResponseUtil.createResponse("User doesn't exist"));
            }

            String storedHashedPassword = item.get("password").s();
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

            if (!encoder.matches(payload.getPassword(), storedHashedPassword)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ResponseUtil.createResponse("Incorrect password or email. Please try again"));
            }

            User user = new User();
            user.setEmail(payload.getEmail());
            user.setName(item.get("name").s());

            Map<String, Object> response = ResponseUtil.createResponse("Found user");
            response.put("data", user);
            return ResponseEntity.ok(response);
        }
    }

    public ResponseEntity<Map<String, Object>> handleGoogleOAuthLogin(String credentialToken) {
        try {
            User user = verifyGoogleToken(credentialToken);

            try (DynamoDbClient db = startDatabaseClient()) {
                Map<String, Object> existResponse = checkUserExist(db, user.getEmail());

                if ("not exist".equals(existResponse.get("message"))) {
                    // Register new user
                    Map<String, AttributeValue> itemValues = new HashMap<>();
                    itemValues.put(keyName, AttributeValue.builder().s(user.getEmail()).build());
                    itemValues.put("name", AttributeValue.builder().s(user.getName()).build());
                    itemValues.put("password", AttributeValue.builder().s("GOOGLE_AUTH").build()); // placeholder

                    PutItemRequest request = PutItemRequest.builder().tableName(tableName).item(itemValues).build();
                    db.putItem(request);
                }

                Map<String, Object> response = ResponseUtil.createResponse("Credential received");
                response.put("data", user);
                return ResponseEntity.ok(response);
            }

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ResponseUtil.createResponse(ex.getMessage()));
        }
    }

    private User verifyGoogleToken(String token) throws Exception {
        NetHttpTransport transport = new NetHttpTransport();
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList("892978279204-it8457gcn5vipos11epkeemnpfpv49vk.apps.googleusercontent.com"))
                .build();

        GoogleIdToken idToken = verifier.verify(token);

        if (idToken == null) {
            throw new RuntimeException("Invalid ID Token.");
        }

        Payload payload = idToken.getPayload();
        User user = new User();
        user.setEmail((String) payload.get("email"));
        user.setName((String) payload.get("name"));
        return user;
    }

}