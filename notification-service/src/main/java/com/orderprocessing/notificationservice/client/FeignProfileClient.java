package com.orderprocessing.notificationservice.client;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FeignProfileClient implements ProfileClient {
    private final UserInternalClient client;

    public FeignProfileClient(UserInternalClient client) {
        this.client = client;
    }

    @Override
    public String email(UUID userId) {
        if (userId == null) {
            throw new IllegalStateException("order fact has no user id");
        }
        UserInternalClient.Profile profile = client.get(userId);
        if (profile == null || profile.email() == null || profile.email().isBlank()) {
            throw new IllegalStateException("profile has no email");
        }
        return profile.email();
    }
}
