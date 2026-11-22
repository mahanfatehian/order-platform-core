package com.orderprocessing.notificationservice.mail;

import com.orderprocessing.notificationservice.model.MailSend;
import com.orderprocessing.notificationservice.model.MailStatus;
import com.orderprocessing.notificationservice.repository.MailSendRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaMailSendStore implements MailSendStore {
    private final MailSendRepository repository;

    public JpaMailSendStore(MailSendRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<MailSend> findByEventId(UUID eventId) {
        return repository.findByEventId(eventId);
    }

    @Override
    public MailSend save(MailSend row) {
        return repository.save(row);
    }

    @Override
    public List<MailSend> findByStatus(MailStatus status) {
        return repository.findByStatus(status);
    }
}
