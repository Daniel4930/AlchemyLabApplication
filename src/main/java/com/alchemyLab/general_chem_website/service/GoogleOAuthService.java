package com.alchemyLab.general_chem_website.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.alchemyLab.general_chem_website.model.User;
import com.alchemyLab.general_chem_website.util.ResponseUtil;
import com.google.api.client.auth.openidconnect.IdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Service
public class GoogleOAuthService {

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private UserService userService;

    private final String clientId = "892978279204-it8457gcn5vipos11epkeemnpfpv49vk.apps.googleusercontent.com";

    private User verifyGoogleToken(String token) throws IOException, GeneralSecurityException {
        NetHttpTransport transport = new NetHttpTransport();
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
        GoogleIdToken idToken = verifier.verify(token);

        Payload payload = idToken.getPayload();
        User user = new User();
        user.setEmail((String) payload.get("email"));
        user.setName((String) payload.get("name"));
        return user;
    }

    public ResponseEntity<Map<String, Object>> handleGoogleOAuthLogin(String credentialToken) {
        try {
            User user = verifyGoogleToken(credentialToken);

            try (DynamoDbClient db = databaseService.startDatabaseClient()) {
                boolean emailExist = userService.checkUserExist(user.getEmail());

                if (!emailExist) {
                    // Register new user

                    Map<String, Object> items = new HashMap<>();
                    items.put("email", user.getEmail());
                    items.put("name", user.getEmail());
                    items.put("password","GOOGLE_AUTH"); //Placeholder

                    Map<String, AttributeValue> itemMap = databaseService.createItemsForRequest(items);

                    PutItemRequest request = PutItemRequest.builder().tableName(databaseService.getTableName()).item(itemMap).build();
                    databaseService.writeDataToDynamoDb(request);
                }

                Map<String, Object> response = ResponseUtil.createResponse("Credential received");
                response.put("data", user);
                return ResponseEntity.ok(response);
            }
        } catch (GeneralSecurityException error) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ResponseUtil.createResponse("Invalid token or permission error"));
        } catch (IOException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ResponseUtil.createResponse("Network error or service unavailable"));
        }
    }
}
