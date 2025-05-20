package com.alchemyLab.general_chem_website.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.alchemyLab.general_chem_website.util.ResponseUtil;

import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.util.Map;
import java.io.IOException;

@ControllerAdvice
public class GlobalExceptionHandler {

    //DynamoDb Errors handling
    @ExceptionHandler(DynamoDbException.class)
    public ResponseEntity<Map<String, Object>> handleDynamoDbException(DynamoDbException ex) {
        System.err.printf("DynamoException class: %s\nError message: %s\n", ex.getClass().getName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResponseUtil.createResponse("Server error: Unexpected error"));
    }
    @ExceptionHandler(SdkClientException.class)
    public ResponseEntity<Map<String, Object>> handleSdkFailure(SdkClientException ex) {
        System.err.printf("Sdk Exception: %s", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ResponseUtil.createResponse("Server error: Network failed"));
    }

    /////////////////////////////////
    
    //Base error case
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        System.err.printf("Exception error class: %s\nError message: %s\n", ex.getClass().getName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseUtil.createResponse("Server error: Unexpected error"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleNetworkError(IOException ex) {
        System.err.printf("Network error class: %s\nError message: %s\n", ex.getClass().getName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ResponseUtil.createResponse("Server error: Network failed"));
    }
}
