package com.orderprocessing.webui.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @Valid
    private final Captcha captcha = new Captcha();
    @Valid
    private final RateLimit rateLimit = new RateLimit();

    public Services getServices() { return services; }
    public Security getSecurity() { return security; }
    public Cart getCart() { return cart; }
    public Features getFeatures() { return features; }
    public Captcha getCaptcha() { return captcha; }
    public RateLimit getRateLimit() { return rateLimit; }

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
        private boolean demoMode;
        public boolean isRegistrationEnabled() { return registrationEnabled; }
        public void setRegistrationEnabled(boolean registrationEnabled) { this.registrationEnabled = registrationEnabled; }
        public boolean isDemoMode() { return demoMode; }
        public void setDemoMode(boolean demoMode) { this.demoMode = demoMode; }
    }

    /** Settings for the self-hosted sign-in captcha; see {@code com.orderprocessing.webui.captcha}. */
    public static class Captcha {
        private boolean enabled = true;
        /** Failed sign-ins tolerated from one address, or against one account, before a captcha is demanded. */
        @Min(1) private int failureThreshold = 3;
        /** Registration submissions tolerated from one address before a captcha is demanded. */
        @Min(1) private int registrationThreshold = 3;
        /** How long attempt counters survive without further activity. */
        @NotNull private Duration window = Duration.ofMinutes(15);
        /** How long an issued challenge stays solvable. */
        @NotNull private Duration ttl = Duration.ofMinutes(2);
        @Min(4) private int length = 6;
        @Min(120) private int width = 240;
        @Min(40) private int height = 72;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
        public int getRegistrationThreshold() { return registrationThreshold; }
        public void setRegistrationThreshold(int registrationThreshold) { this.registrationThreshold = registrationThreshold; }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }
        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
    }

    /**
     * Request ceilings for the endpoints an unauthenticated caller can reach. These bound volume regardless of
     * outcome, which the captcha thresholds deliberately do not: those only react to genuine credential failures.
     */
    public static class RateLimit {
        private boolean enabled = true;
        /** Sign-in and registration submissions allowed from one address per window, whatever the outcome. */
        @Min(1) private int submissionsPerWindow = 10;
        /** Captcha images allowed from one address per window; each page render costs exactly one. */
        @Min(1) private int challengesPerWindow = 30;
        @NotNull private Duration window = Duration.ofMinutes(1);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getSubmissionsPerWindow() { return submissionsPerWindow; }
        public void setSubmissionsPerWindow(int submissionsPerWindow) { this.submissionsPerWindow = submissionsPerWindow; }
        public int getChallengesPerWindow() { return challengesPerWindow; }
        public void setChallengesPerWindow(int challengesPerWindow) { this.challengesPerWindow = challengesPerWindow; }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
    }
}
