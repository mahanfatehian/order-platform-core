package com.orderprocessing.webui;

import com.orderprocessing.webui.config.WebUiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WebUiProperties.class)
public class WebUiApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebUiApplication.class, args);
    }
}
