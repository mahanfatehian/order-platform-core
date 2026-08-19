package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.captcha.CaptchaService;
import com.orderprocessing.webui.config.WebUiProperties;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class CaptchaController {
    private final CaptchaService captchaService;
    private final WebUiProperties properties;

    public CaptchaController(CaptchaService captchaService, WebUiProperties properties) {
        this.captchaService = captchaService;
        this.properties = properties;
    }

    /**
     * Serves the current challenge as a PNG. Each request mints a new challenge, so the response must never be
     * cached by the browser or an intermediary; a cached image would show a picture whose answer is already spent.
     */
    @GetMapping(value = "/captcha/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(HttpSession session) {
        if (!properties.getCaptcha().isEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(captchaService.issue(session));
    }
}
