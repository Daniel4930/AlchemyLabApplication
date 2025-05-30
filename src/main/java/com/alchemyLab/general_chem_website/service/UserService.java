package com.alchemyLab.general_chem_website.service;

import com.alchemyLab.general_chem_website.model.ResetPasswordLink;
import com.alchemyLab.general_chem_website.model.ResponseForgotPassword;
import com.alchemyLab.general_chem_website.model.ResponseResetPassword;
import com.alchemyLab.general_chem_website.model.ResponseUserInfo;
import com.alchemyLab.general_chem_website.model.SignUpInfo;
import com.alchemyLab.general_chem_website.model.User;
import com.alchemyLab.general_chem_website.util.ResponseUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private EmailService emailService;

    @Autowired
    private DatabaseService databaseService;

    private String keyName = "email";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public ResponseEntity<Map<String, Object>> registerUser(SignUpInfo payload) {
        boolean emailExist = checkUserExist(payload.getEmail());

        if (emailExist) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ResponseUtil.createResponse("Email already exists"));
        }

        String hashedPassword = encoder.encode(payload.getPassword());

        Map<String, Object> items = new HashMap<>();
        items.put(keyName, payload.getEmail());
        items.put("name", payload.getName());
        items.put("password", hashedPassword);

        Map<String, AttributeValue> itemValues = databaseService.createItemsForRequest(items);

        PutItemRequest request = PutItemRequest.builder()
                .tableName(databaseService.getTableName())
                .item(itemValues)
                .build();
        databaseService.writeDataToDynamoDb(request);

        User user = new User();
        user.setEmail(payload.getEmail());
        user.setName(payload.getName());

        Map<String, Object> response = ResponseUtil.createResponse("User created successfully");
        response.put("data", user);
        return ResponseEntity.ok(response);
    }

    public boolean checkUserExist(String emailVal) {
        Map<String, Object> items = new HashMap<>();
        items.put(keyName, emailVal);

        Map<String, AttributeValue> key = databaseService.createItemsForRequest(items);

        GetItemRequest request = GetItemRequest.builder()
                .tableName(databaseService.getTableName())
                .key(key)
                .build();

        Map<String, AttributeValue> item = databaseService.getDataFromDynamoDb(request);
        if (item == null || item.isEmpty()) {
            return false;
        }
        return true;
    }

    public ResponseEntity<Map<String, Object>> loginUser(ResponseUserInfo payload) {
        Map<String, Object> items = new HashMap<>();
        items.put(keyName, payload.getEmail());
        Map<String, AttributeValue> key = databaseService.createItemsForRequest(items);

        GetItemRequest request = GetItemRequest.builder()
                .tableName(databaseService.getTableName())
                .key(key)
                .build();
        Map<String, AttributeValue> item = databaseService.getDataFromDynamoDb(request);

        if (item == null || item.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ResponseUtil.createResponse("User doesn't exist"));
        }

        String storedHashedPassword = item.get("password").s();

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

    public ResponseEntity<Map<String, Object>> forgotPasswordRequest(ResponseForgotPassword payload) {
        try(DynamoDbClient db = databaseService.startDatabaseClient()) {
            boolean emailExist = checkUserExist(payload.getEmail());

            if (emailExist) {
                ResetPasswordLink data = generateResetPasswordLink("email", payload.getEmail());
                return emailService.sendEmail(payload.getEmail(), data.getLink());
            } else {
                Map<String, Object> response = ResponseUtil.createResponse("Email does not exist");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        }
    }

    private ResetPasswordLink generateResetPasswordLink(String keyName, String keyValue) {
        UUID token = UUID.randomUUID();
        final String originalLink = "https://chemlesson.cc/reset?token=";
        LocalDateTime expireDate = LocalDateTime.now().plusMinutes(30);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        ResetPasswordLink data = new ResetPasswordLink();
        data.setLink(originalLink + token + "&email=" + keyValue);
        data.setExpireTime(formatter.format(expireDate));

        Map<String, Object> items = new HashMap<>();
        items.put(keyName, keyValue);
        Map<String, AttributeValue> keyItem = databaseService.createItemsForRequest(items);

        items.clear();
        items.put("token", token.toString());
        items.put("expireDate", data.getExpireTime());
        Map<String, AttributeValue> dataObject = databaseService.createItemsForRequest(items);

        Map<String, AttributeValueUpdate> updatedValue = new HashMap<>();
        updatedValue.put("resetPassword", AttributeValueUpdate.builder()
                .value(AttributeValue.builder().m(dataObject).build())
                .action(AttributeAction.PUT)
                .build());

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(databaseService.getTableName())
                .key(keyItem)
                .attributeUpdates(updatedValue)
                .build();
        databaseService.updateDataFromDynamoDb(request);

        return data;
    }

    public ResponseEntity<Map<String, Object>> resetPassword(ResponseResetPassword payload) {
        Map<String, Object> items = new HashMap<>();
        items.put(keyName, payload.getEmail());

        Map<String, AttributeValue> keyItem = databaseService.createItemsForRequest(items);

        GetItemRequest getRequest = GetItemRequest
                .builder()
                .tableName(databaseService.getTableName())
                .key(keyItem)
                .build();

        Map<String, AttributeValue> result = databaseService.getDataFromDynamoDb(getRequest);        
        String token = result.get("resetPassword").m().get("token").s();
        String expireDate = result.get("resetPassword").m().get("expireDate").s();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime expireTime = LocalDateTime.parse(expireDate, formatter);
        LocalDateTime currentTime = LocalDateTime.now();
        Duration duration = Duration.between(expireTime, currentTime);
        long minutes = duration.toMinutes();
        
        //Link expires after 30 minutes
        if (!token.equals(payload.getToken()) || minutes > 30) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ResponseUtil.createResponse("Link already expired or not valid"));
        }

        String hashedPassword = encoder.encode(payload.getPassword());

        Map<String, AttributeValueUpdate> updatedValues = new HashMap<>();
        updatedValues.put("password", AttributeValueUpdate.builder()
                .value(AttributeValue.builder().s(hashedPassword).build())
                .action(AttributeAction.PUT)
                .build());
        
        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName(databaseService.getTableName())
                .key(keyItem)
                .attributeUpdates(updatedValues)
                .build();
        databaseService.updateDataFromDynamoDb(updateRequest);

        return ResponseEntity.ok(ResponseUtil.createResponse("Password has been reset"));
    }
}