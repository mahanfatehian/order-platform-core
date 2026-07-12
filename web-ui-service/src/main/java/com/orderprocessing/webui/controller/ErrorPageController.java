package com.orderprocessing.webui.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class ErrorPageController {
    @GetMapping("/error/403") @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden() { return "error/403"; }

    @GetMapping("/error/404") @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() { return "error/404"; }
}
