package com.orderprocessing.webuiservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegalPageController {
    @GetMapping("/terms")
    public String terms() {
        return "legal/terms";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "legal/privacy";
    }

    @GetMapping("/contact")
    public String contact() {
        return "legal/contact";
    }
}
