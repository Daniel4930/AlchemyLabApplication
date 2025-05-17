package com.alchemyLab.general_chem_website.controller;

import com.alchemyLab.general_chem_website.model.ResponseUserInfo;
import com.alchemyLab.general_chem_website.model.SignUpInfo;
import com.alchemyLab.general_chem_website.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DatabaseController {

    @Autowired
    private UserService userService;

    @PostMapping("/auth/signup")
    public ResponseEntity<Map<String, Object>> signUpUser(@RequestBody SignUpInfo payload) {
        return userService.registerUser(payload);
    }

    @PostMapping("/auth/signin")
    public ResponseEntity<Map<String, Object>> signInUser(@RequestBody ResponseUserInfo payload) {
        return userService.loginUser(payload);
    }
}