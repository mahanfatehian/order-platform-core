package com.orderprocessing.notificationservice.mail;

import com.orderprocessing.kafkacommon.event.OrderConfirmedEvent;
import com.orderprocessing.notificationservice.model.MailKind;
import com.orderprocessing.notificationservice.model.MailSend;
import com.orderprocessing.notificationservice.model.MailStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailServiceTest {

    @Test
    void eachKindSendsOneMailAndOneRow() {
        CaptureMailSink sink = new CaptureMailSink();
        InMemoryMailSendStore store = new InMemoryMailSendStore();
        MailService service = new MailService(store, sink);

        for (MailKind kind : MailKind.values()) {
            service.send(UUID.randomUUID(), kind, "customer@example.com", subject(kind), "body " + kind);
        }

        assertThat(MailKind.values()).containsExactly(
                MailKind.CONFIRMED, MailKind.SHIPPED, MailKind.DELIVERED, MailKind.FAILED, MailKind.REFUND);
        assertThat(sink.captured()).hasSize(5);
        assertThat(store.all()).hasSize(5);
        assertThat(store.all()).extracting(MailSend::getStatus).containsOnly(MailStatus.SENT);
        assertThat(store.all()).extracting(MailSend::getKind).containsExactlyInAnyOrder(MailKind.values());
    }

    @Test
    void replayingTheSameEventIdDoesNotCallTheSinkAgain() {
        CaptureMailSink sink = new CaptureMailSink();
        InMemoryMailSendStore store = new InMemoryMailSendStore();
        MailService service = new MailService(store, sink);
        UUID eventId = UUID.randomUUID();

        MailSend first = service.send(eventId, MailKind.SHIPPED, "customer@example.com", "Order shipped", "same");
        MailSend replay = service.send(eventId, MailKind.SHIPPED, "customer@example.com", "Order shipped", "same");

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(sink.captured()).hasSize(1);
        assertThat(store.all()).hasSize(1);
    }

    @Test
    void aDifferentBodyForTheSameEventIdDoesNotSendAgain() {
        CaptureMailSink sink = new CaptureMailSink();
        MailService service = new MailService(new InMemoryMailSendStore(), sink);
        UUID eventId = UUID.randomUUID();
        service.send(eventId, MailKind.DELIVERED, "customer@example.com", "Order delivered", "one");

        assertThatThrownBy(() -> service.send(eventId, MailKind.DELIVERED, "customer@example.com",
                "Order delivered", "two"))
                .isInstanceOf(MailConflictException.class);

        assertThat(sink.captured()).hasSize(1);
    }

    @Test
    void aThrowingSinkLeavesAFailedRowAndALaterSuccessMarksTheSameRow() {
        AtomicInteger calls = new AtomicInteger();
        MailSink failing = (recipient, subject, body) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("down");
        };
        InMemoryMailSendStore store = new InMemoryMailSendStore();
        MailService service = new MailService(store, failing);
        UUID eventId = UUID.randomUUID();

        MailSend failed = service.send(eventId, MailKind.CONFIRMED, "customer@example.com", "Order confirmed", "body");
        MailSend replay = service.send(eventId, MailKind.CONFIRMED, "customer@example.com", "Order confirmed", "body");

        assertThat(failed.getStatus()).isEqualTo(MailStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getSentAt()).isNull();
        assertThat(replay.getId()).isEqualTo(failed.getId());
        assertThat(calls).hasValue(1);
        assertThat(store.all()).hasSize(1);

        CaptureMailSink recovery = new CaptureMailSink();
        new MailService(store, recovery).retryFailed();

        assertThat(store.all()).hasSize(1);
        assertThat(store.all().get(0).getId()).isEqualTo(failed.getId());
        assertThat(store.all().get(0).getStatus()).isEqualTo(MailStatus.SENT);
        assertThat(store.all().get(0).getSentAt()).isNotNull();
        assertThat(recovery.captured()).hasSize(1);
    }

    @Test
    void confirmReceiptTotalEqualsCapturedMinorUnits() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID captureId = UUID.randomUUID();
        OrderConfirmedEvent event = new OrderConfirmedEvent();
        event.setOrderId(orderId);
        event.setCapturedMinor(2500L);
        event.setWalletCaptureEntryId(captureId);
        OrderConfirmedEvent.Line line = new OrderConfirmedEvent.Line();
        line.setProductId(productId);
        line.setQuantity(2);
        event.setLines(List.of(line));
        event.setShipRecipient("Ada");
        event.setShipLine1("1 Main");
        event.setShipLine2("Unit 2");
        event.setShipCity("Tehran");
        event.setShipRegion("Tehran");
        event.setShipPostalCode("12345");
        event.setShipPhone("09120000000");
        event.setShipCountry("IR");

        String body = Receipts.confirmed(event);

        assertThat(body).contains("Order " + orderId);
        assertThat(body).contains(productId + " 2");
        assertThat(body).contains("Total: 2500 rials");
        assertThat(body).doesNotContain("250000");
        assertThat(body).contains("Ada", "1 Main", "Unit 2", "Tehran", "12345", "09120000000", "IR");
        assertThat(body).contains("Wallet capture: " + captureId);
    }

    private static String subject(MailKind kind) {
        return switch (kind) {
            case CONFIRMED -> "Order confirmed";
            case SHIPPED -> "Order shipped";
            case DELIVERED -> "Order delivered";
            case FAILED -> "Order failed";
            case REFUND -> "Refund posted";
        };
    }

    private static final class InMemoryMailSendStore implements MailSendStore {
        private final List<MailSend> rows = new ArrayList<>();

        @Override
        public Optional<MailSend> findByEventId(UUID eventId) {
            return rows.stream().filter(row -> row.getEventId().equals(eventId)).findFirst();
        }

        @Override
        public MailSend save(MailSend row) {
            rows.removeIf(existing -> existing.getId().equals(row.getId()));
            rows.add(row);
            return row;
        }

        @Override
        public List<MailSend> findByStatus(MailStatus status) {
            return rows.stream().filter(row -> row.getStatus() == status).toList();
        }

        List<MailSend> all() {
            return List.copyOf(rows);
        }
    }
}
