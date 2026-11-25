package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.shop.PublicCatalog;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PublicShopController {
    private final PublicCatalog catalog = new PublicCatalog();

    @GetMapping("/shop")
    public String shop() {
        return "shop/index";
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        return catalog.robots();
    }

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
