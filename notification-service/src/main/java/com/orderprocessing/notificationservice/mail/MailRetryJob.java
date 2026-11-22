package com.orderprocessing.notificationservice.mail;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MailRetryJob {
    private final MailService mailService;

    public MailRetryJob(MailService mailService) {
        this.mailService = mailService;
    }

    @Scheduled(fixedDelayString = "${mail.retry-delay:60000}")
    public void retryFailed() {
        mailService.retryFailed();
    }
}
