package com.alchemyLab.general_chem_website.service;

import com.alchemyLab.general_chem_website.model.ResetPasswordLink;
import com.alchemyLab.general_chem_website.model.ResponseForgotPassword;
import com.alchemyLab.general_chem_website.model.ResponseResetPassword;
import com.alchemyLab.general_chem_website.model.ResponseUserInfo;
import com.alchemyLab.general_chem_website.model.SignUpInfo;
import com.alchemyLab.general_chem_website.model.User;
import com.alchemyLab.general_chem_website.util.ResponseUtil;
import com.google.api.client.auth.openidconnect.IdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private EmailService emailService;

    @Value("${aws.dynamodb.tableName}")
    private String tableName;
    
    @Value("${aws.service.region}")
    private String awsRegion;
    
    private Region region;
    private String keyName = "email";

    @PostConstruct
    public void init() {
        region = Region.of(awsRegion);
    }

    private DynamoDbClient startDatabaseClient() {
        return DynamoDbClient.builder().region(region).build();
    }

    private void writeDataToDynamoDb(PutItemRequest request) {
        try (DynamoDbClient db = startDatabaseClient()) {
            db.putItem(request);
        } catch (DynamoDbException error) {
            System.err.printf("Error from %s: %s", error.getClass().getName(), error.getMessage());
        }
    }

    private Map<String, AttributeValue> getDataFromDynamoDb(GetItemRequest request) {
    try (DynamoDbClient db = startDatabaseClient()) {
        Map<String, AttributeValue> item = db.getItem(request).item();
        return item != null ? item : new HashMap<>();
    } catch (DynamoDbException error) {
        System.err.println("DynamoDB error: " + error.getMessage());
        return new HashMap<>();
    }
}


    public ResponseEntity<Map<String, Object>> registerUser(SignUpInfo payload) {
        boolean emailExist = checkUserExist(payload.getEmail());

        if (!emailExist) {
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
        writeDataToDynamoDb(request);

        User user = new User();
        user.setEmail(payload.getEmail());
        user.setName(payload.getName());

        Map<String, Object> response = ResponseUtil.createResponse("User created successfully");
        response.put("data", user);
        return ResponseEntity.ok(response);
    }

    private boolean checkUserExist(String emailVal) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put(keyName, AttributeValue.builder().s(emailVal).build());

        GetItemRequest request = GetItemRequest.builder().tableName(tableName).key(key).build();

        Map<String, AttributeValue> item = getDataFromDynamoDb(request);
        if (item == null || item.isEmpty()) {
            return false;
        }
        return true;
    }

    public ResponseEntity<Map<String, Object>> loginUser(ResponseUserInfo payload) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put(keyName, AttributeValue.builder().s(payload.getEmail()).build());
        System.out.println(payload.getEmail());
        System.out.println(payload.getPassword());

        GetItemRequest request = GetItemRequest.builder().tableName(tableName).key(key).build();
        Map<String, AttributeValue> item = getDataFromDynamoDb(request);

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

    public ResponseEntity<Map<String, Object>> handleGoogleOAuthLogin(String credentialToken) {
        try {
            User user = verifyGoogleToken(credentialToken);

            try (DynamoDbClient db = startDatabaseClient()) {
                boolean emailExist = checkUserExist(user.getEmail());

                if (!emailExist) {
                    // Register new user
                    Map<String, AttributeValue> itemValues = new HashMap<>();
                    itemValues.put(keyName, AttributeValue.builder().s(user.getEmail()).build());
                    itemValues.put("name", AttributeValue.builder().s(user.getName()).build());
                    itemValues.put("password", AttributeValue.builder().s("GOOGLE_AUTH").build()); // placeholder

                    PutItemRequest request = PutItemRequest.builder().tableName(tableName).item(itemValues).build();
                    writeDataToDynamoDb(request);
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

    private User verifyGoogleToken(String token) throws IOException, GeneralSecurityException {
        NetHttpTransport transport = new NetHttpTransport();
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList("892978279204-it8457gcn5vipos11epkeemnpfpv49vk.apps.googleusercontent.com"))
                .build();
        GoogleIdToken idToken = verifier.verify(token);

        Payload payload = idToken.getPayload();
        User user = new User();
        user.setEmail((String) payload.get("email"));
        user.setName((String) payload.get("name"));
        return user;
    }

    public ResponseEntity<Map<String, Object>> forgotPasswordRequest(ResponseForgotPassword payload) {
        try(DynamoDbClient db = startDatabaseClient()) {
            boolean emailExist = checkUserExist(payload.getEmail());

            if (emailExist) {
                Map<String, Object> response = ResponseUtil.createResponse("Email exists");

                ResetPasswordLink data = generateResetPasswordLink("email", payload.getEmail());
                emailService.sendEmail(payload.getEmail(), data.getLink());

                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = ResponseUtil.createResponse("Email does not exist");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        }
    }

    private ResetPasswordLink generateResetPasswordLink(String keyName, String keyValue) {
        UUID token = UUID.randomUUID();
        final String originalLink = "http://localhost:8080/reset?token=";
        LocalDateTime expireDate = LocalDateTime.now().plusMinutes(30);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        ResetPasswordLink data = new ResetPasswordLink();
        data.setLink(originalLink + token + "&email=" + keyValue);
        data.setExpireTime(formatter.format(expireDate));

        Map<String, AttributeValue> item = new HashMap<>();
        Map<String, AttributeValue> dataObject = new HashMap<>();

        dataObject.put("token", AttributeValue.builder().s(token.toString()).build());
        dataObject.put("expireDate", AttributeValue.builder().s(data.getExpireTime()).build());

        item.put(keyName, AttributeValue.builder().s(keyValue).build());
        item.put("resetPassword", AttributeValue.builder().m(dataObject).build());
        PutItemRequest request = PutItemRequest.builder().tableName(tableName).item(item).build();
        writeDataToDynamoDb(request);

        return data;
    }

    public ResponseEntity<Map<String, Object>> resetPassword(ResponseResetPassword payload) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("email", AttributeValue.builder().s(payload.getEmail()).build());

        GetItemRequest getRequest = GetItemRequest.builder().tableName(tableName).key(key).build();

        Map<String, AttributeValue> result = getDataFromDynamoDb(getRequest);        
        String token = result.get("resetPassword").m().get("token").s();
        String expireDate = result.get("resetPassword").m().get("expireDate").s();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime expireTime = LocalDateTime.parse(expireDate, formatter);
        LocalDateTime currentTime = LocalDateTime.now();
        Duration duration = Duration.between(currentTime, expireTime);
        long minutes = duration.toMinutes();
        
        //Link expires after 30 minutes
        if (!token.equals(payload.getToken()) || minutes > 30) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ResponseUtil.createResponse("Link already expired or not valid"));
        }
        
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hashedPassword = encoder.encode(payload.getPassword());
        Map<String, AttributeValue> dataObject = new HashMap<>();
        dataObject.put("token", AttributeValue.builder().s("").build());
        dataObject.put("expireDate", AttributeValue.builder().s("").build());

        key.put("password", AttributeValue.builder().s(hashedPassword).build());
        key.put("resetPassword", AttributeValue.builder().m(dataObject).build());
        PutItemRequest putRequest = PutItemRequest.builder().tableName(tableName).item(result).build();
        writeDataToDynamoDb(putRequest);

        return ResponseEntity.ok(ResponseUtil.createResponse("Password has been reset"));
    }
}