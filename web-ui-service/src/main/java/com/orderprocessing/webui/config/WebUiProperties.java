package com.orderprocessing.webui.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app")
public class WebUiProperties {
    @Valid
    private final Services services = new Services();
    @Valid
    private final Security security = new Security();
    @Valid
    private final Cart cart = new Cart();
    @Valid
    private final Features features = new Features();

    public Services getServices() { return services; }
    public Security getSecurity() { return security; }
    public Cart getCart() { return cart; }
    public Features getFeatures() { return features; }

    public static class Services {
        @NotBlank private String authUrl;
        @NotBlank private String userUrl;
        @NotBlank private String storeUrl;
        @NotBlank private String orderUrl;
        @NotBlank @Size(min = 32) private String storeInternalApiKey;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);

        public String getAuthUrl() { return authUrl; }
        public void setAuthUrl(String authUrl) { this.authUrl = authUrl; }
        public String getUserUrl() { return userUrl; }
        public void setUserUrl(String userUrl) { this.userUrl = userUrl; }
        public String getStoreUrl() { return storeUrl; }
        public void setStoreUrl(String storeUrl) { this.storeUrl = storeUrl; }
        public String getOrderUrl() { return orderUrl; }
        public void setOrderUrl(String orderUrl) { this.orderUrl = orderUrl; }
        public String getStoreInternalApiKey() { return storeInternalApiKey; }
        public void setStoreInternalApiKey(String storeInternalApiKey) { this.storeInternalApiKey = storeInternalApiKey; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }

    public static class Security {
        @NotBlank @Size(min = 32) private String jwtSecret;
        private Duration refreshSkew = Duration.ofSeconds(45);

        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
        public Duration getRefreshSkew() { return refreshSkew; }
        public void setRefreshSkew(Duration refreshSkew) { this.refreshSkew = refreshSkew; }
    }

    public static class Cart {
        @Min(1) private int maximumQuantity = 99;
        public int getMaximumQuantity() { return maximumQuantity; }
        public void setMaximumQuantity(int maximumQuantity) { this.maximumQuantity = maximumQuantity; }
    }

    public static class Features {
        private boolean registrationEnabled = true;
        public boolean isRegistrationEnabled() { return registrationEnabled; }
        public void setRegistrationEnabled(boolean registrationEnabled) { this.registrationEnabled = registrationEnabled; }
    }
}
