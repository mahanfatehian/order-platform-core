package com.orderprocessing.notificationservice.mail;

import java.util.UUID;

public class MailConflictException extends RuntimeException {
    public MailConflictException(UUID eventId) {
        super("Mail event " + eventId + " was already recorded with a different body");
    }
}
