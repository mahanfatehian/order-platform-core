package com.orderprocessing.notificationservice.mail;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class CaptureMailSink implements MailSink {
    private final List<CapturedMail> captured = new CopyOnWriteArrayList<>();

    @Override
    public void send(String recipient, String subject, String body) {
        captured.add(new CapturedMail(recipient, subject, body, Instant.now()));
    }

    public List<CapturedMail> captured() {
        return List.copyOf(captured);
    }

    public record CapturedMail(String recipient, String subject, String body, Instant capturedAt) {
    }
}
