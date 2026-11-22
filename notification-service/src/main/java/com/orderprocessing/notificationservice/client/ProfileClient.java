package com.orderprocessing.notificationservice.client;

import java.util.UUID;

public interface ProfileClient {
    String email(UUID userId);
}
