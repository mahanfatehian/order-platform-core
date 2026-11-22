package com.orderprocessing.notificationservice.mail;

public interface MailSink {
    void send(String recipient, String subject, String body);
}
