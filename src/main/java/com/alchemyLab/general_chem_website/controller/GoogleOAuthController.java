package com.alchemyLab.general_chem_website.controller;

import com.alchemyLab.general_chem_website.model.ResponseGoogleCredential;
import com.alchemyLab.general_chem_website.service.GoogleOAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class GoogleOAuthController {

    @Autowired
    private GoogleOAuthService googleService;

    @PostMapping("/api/auth/google_credential")
    public ResponseEntity<Map<String, Object>> getUserCredential(@RequestBody ResponseGoogleCredential payload) {
        return googleService.handleGoogleOAuthLogin(payload.getCredential());
    }
}
