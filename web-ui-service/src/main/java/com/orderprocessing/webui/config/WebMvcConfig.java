package com.orderprocessing.webui.config;

import com.orderprocessing.webui.support.AttemptCounterStore;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final AttemptCounterStore counters;
    private final WebUiProperties properties;

    public WebMvcConfig(AttemptCounterStore counters, WebUiProperties properties) {
        this.counters = counters;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthRateLimitInterceptor(counters, properties))
                .addPathPatterns("/login", "/register", AuthRateLimitInterceptor.CAPTCHA_IMAGE_PATH);
    }
}
