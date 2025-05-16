package com.alchemyLab.general_chem_website.controller;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.alchemyLab.general_chem_website.model.ResponseToken;
import com.alchemyLab.general_chem_website.model.User;
import com.google.api.client.auth.openidconnect.IdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

@RestController
public class GoogleOAuthController {
    private static final String client_id = "892978279204-it8457gcn5vipos11epkeemnpfpv49vk.apps.googleusercontent.com";

    @PostMapping("/auth/google_credential")
    public ResponseEntity<Map<String, Object>> getUserCredential(@RequestBody ResponseToken payload) {
        Map<String, Object> response = new HashMap<>();

        try {
            User getDataFromToken = tokenVerifier(payload.getCredential());
            response.put("status", "ok");
            response.put("message", "Credential received");
            response.put("userData", getDataFromToken);
            return ResponseEntity.ok(response);

        } catch(Exception error){
            System.out.println(error.getMessage());
            response.put("status", "error");
            response.put("message", error.getMessage());
            return ResponseEntity.status(401).body(response);
        }
    }

    public static User tokenVerifier(String token) throws Exception {
        NetHttpTransport transport = new NetHttpTransport();

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
            transport,
            GsonFactory.getDefaultInstance()
        )
        .setAudience(Collections.singletonList(client_id))
        .build();

        GoogleIdToken idToken = verifier.verify(token);

        if (idToken != null) {
            Payload payload = idToken.getPayload();
            var data = new User();

            data.setName((String)payload.get("name"));
            data.setEmail((String)payload.get("email"));

            return data;
        } else {
            throw new RuntimeException("Invalid ID Token.");
        }
    }
}
