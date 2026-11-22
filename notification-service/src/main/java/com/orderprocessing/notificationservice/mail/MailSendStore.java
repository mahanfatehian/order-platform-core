package com.orderprocessing.notificationservice.mail;

import com.orderprocessing.notificationservice.model.MailSend;
import com.orderprocessing.notificationservice.model.MailStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailSendStore {
    Optional<MailSend> findByEventId(UUID eventId);

    MailSend save(MailSend row);

    List<MailSend> findByStatus(MailStatus status);
}
