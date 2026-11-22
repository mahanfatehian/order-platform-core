package com.orderprocessing.notificationservice.repository;

import com.orderprocessing.notificationservice.model.MailSend;
import com.orderprocessing.notificationservice.model.MailStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailSendRepository extends JpaRepository<MailSend, UUID> {
    Optional<MailSend> findByEventId(UUID eventId);

    List<MailSend> findByStatus(MailStatus status);
}
