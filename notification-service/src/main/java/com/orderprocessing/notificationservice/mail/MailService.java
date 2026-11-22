package com.orderprocessing.notificationservice.mail;

import com.orderprocessing.notificationservice.model.MailKind;
import com.orderprocessing.notificationservice.model.MailSend;
import com.orderprocessing.notificationservice.model.MailStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class MailService {
    private final MailSendStore store;
    private final MailSink sink;

    public MailService(MailSendStore store, MailSink sink) {
        this.store = store;
        this.sink = sink;
    }

    public MailSend send(UUID eventId, MailKind kind, String recipient, String subject, String body) {
        Optional<MailSend> existing = store.findByEventId(eventId);
        if (existing.isPresent()) {
            return sameBodyOrConflict(existing.get(), body);
        }
        MailSend row = newRow(eventId, kind, recipient, subject, body);
        try {
            sink.send(recipient, subject, body);
            row.setStatus(MailStatus.SENT);
            row.setSentAt(Instant.now());
            row.setAttempts(1);
        } catch (RuntimeException ex) {
            row.setStatus(MailStatus.FAILED);
            row.setSentAt(null);
            row.setAttempts(1);
        }
        return saveNew(row, body);
    }

    public MailSend fail(UUID eventId, MailKind kind, String recipient, String subject, String body) {
        Optional<MailSend> existing = store.findByEventId(eventId);
        if (existing.isPresent()) {
            MailSend row = existing.get();
            row.setStatus(MailStatus.FAILED);
            row.setAttempts(row.getAttempts() + 1);
            return store.save(row);
        }
        MailSend row = newRow(eventId, kind, recipient == null ? "" : recipient, subject, body);
        row.setStatus(MailStatus.FAILED);
        row.setAttempts(1);
        return saveNew(row, body);
    }

    public int retryFailed() {
        int sent = 0;
        for (MailSend row : store.findByStatus(MailStatus.FAILED)) {
            try {
                sink.send(row.getRecipient(), row.getSubject(), row.getBody());
                row.setStatus(MailStatus.SENT);
                row.setSentAt(Instant.now());
                sent++;
            } catch (RuntimeException ex) {
                row.setStatus(MailStatus.FAILED);
                row.setSentAt(null);
            }
            row.setAttempts(row.getAttempts() + 1);
            store.save(row);
        }
        return sent;
    }

    private MailSend saveNew(MailSend row, String body) {
        try {
            return store.save(row);
        } catch (DataIntegrityViolationException ex) {
            MailSend winner = store.findByEventId(row.getEventId()).orElseThrow(() -> ex);
            return sameBodyOrConflict(winner, body);
        }
    }

    private MailSend sameBodyOrConflict(MailSend row, String body) {
        if (!Objects.equals(row.getBody(), body)) {
            throw new MailConflictException(row.getEventId());
        }
        return row;
    }

    private MailSend newRow(UUID eventId, MailKind kind, String recipient, String subject, String body) {
        MailSend row = new MailSend();
        row.setId(UUID.randomUUID());
        row.setEventId(eventId);
        row.setKind(kind);
        row.setRecipient(recipient == null ? "" : recipient);
        row.setSubject(subject == null ? "" : subject);
        row.setBody(body == null ? "" : body);
        row.setCreatedAt(Instant.now());
        row.setAttempts(0);
        row.setStatus(MailStatus.FAILED);
        return row;
    }
}
