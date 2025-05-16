package com.alchemyLab.general_chem_website.controller;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index.html";
    }

    @GetMapping("/contact")
    public String contact() {
        return "index.html";
    }

    @GetMapping("/lesson")
    public String lesson() {
        return "index.html";
    }

    @GetMapping("/login")
    public String login() {
        return "index.html";
    }

    @GetMapping("/user")
    public String user() {
        return "index.html";
    }
}
