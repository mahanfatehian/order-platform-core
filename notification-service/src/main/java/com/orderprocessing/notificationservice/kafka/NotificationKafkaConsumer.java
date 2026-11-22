package com.orderprocessing.notificationservice.kafka;

import com.orderprocessing.kafkacommon.event.OrderConfirmedEvent;
import com.orderprocessing.kafkacommon.event.OrderDeliveredEvent;
import com.orderprocessing.kafkacommon.event.OrderFailedEvent;
import com.orderprocessing.kafkacommon.event.OrderShippedEvent;
import com.orderprocessing.kafkacommon.event.RefundPostedEvent;
import com.orderprocessing.notificationservice.client.ProfileClient;
import com.orderprocessing.notificationservice.mail.MailConflictException;
import com.orderprocessing.notificationservice.mail.MailService;
import com.orderprocessing.notificationservice.mail.Receipts;
import com.orderprocessing.notificationservice.model.MailKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumer {
    private final MailService mailService;
    private final ProfileClient profiles;

    @KafkaListener(topics = "#{@kafkaTopicNames.orderEvents()}", groupId = "notification-service")
    public void handleOrderEvent(ConsumerRecord<String, Object> record) {
        try {
            dispatch(record.value());
        } catch (RuntimeException ex) {
            log.warn("Status mail did not leave the consumer: {}", ex.toString());
        }
    }

    private void dispatch(Object event) {
        if (event instanceof OrderConfirmedEvent confirmed) {
            mail(confirmed.getEventId(), MailKind.CONFIRMED, confirmed.getUserId(),
                    "Order confirmed", Receipts.confirmed(confirmed));
            return;
        }
        if (event instanceof OrderShippedEvent shipped) {
            mail(shipped.getEventId(), MailKind.SHIPPED, shipped.getUserId(),
                    "Order shipped", "Order " + shipped.getOrderId() + " shipped");
            return;
        }
        if (event instanceof OrderDeliveredEvent delivered) {
            mail(delivered.getEventId(), MailKind.DELIVERED, delivered.getUserId(),
                    "Order delivered", "Order " + delivered.getOrderId() + " delivered");
            return;
        }
        if (event instanceof OrderFailedEvent failed) {
            mail(failed.getEventId(), MailKind.FAILED, failed.getUserId(),
                    "Order failed", "Order " + failed.getOrderId() + " failed");
            return;
        }
        if (event instanceof RefundPostedEvent refund) {
            mail(refund.getEventId(), MailKind.REFUND, refund.getUserId(),
                    "Refund posted", "Refund " + refund.getRefundEntryId()
                            + " for order " + refund.getOrderId() + ": " + refund.getAmount() + " rials");
            return;
        }
        log.debug("Ignoring order event type not consumed by notification-service: {}",
                event == null ? "null" : event.getClass().getSimpleName());
    }

    private void mail(UUID eventId, MailKind kind, UUID userId, String subject, String body) {
        if (eventId == null) {
            return;
        }
        String recipient;
        try {
            recipient = profiles.email(userId);
        } catch (RuntimeException ex) {
            mailService.fail(eventId, kind, "", subject, body);
            log.warn("Profile email lookup failed for event {}", eventId);
            return;
        }
        try {
            mailService.send(eventId, kind, recipient, subject, body);
        } catch (MailConflictException ex) {
            log.warn("Mail conflict for event {}", eventId);
        }
    }
}
