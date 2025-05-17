package com.alchemyLab.general_chem_website.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.alchemyLab.general_chem_website.util.ResponseUtil;

import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DynamoDbException.class)
    public ResponseEntity<Map<String, Object>> handleDynamoDbException(DynamoDbException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResponseUtil.createResponse("Unexpected error: " + ex.getMessage()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseUtil.createResponse("Unexpected error: " + ex.getMessage()));
    }
    
}
