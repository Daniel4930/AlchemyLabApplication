package com.alchemyLab.general_chem_website.controller;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping({"/", "/contact", "/lesson", "/login", "/user", "/signup", 
    "/forgot-password", "/reset", "/lesson/**", "/lesson/question/**"})
    public String handleRouting() {
        return "index.html";
    }
}
