package com.orderprocessing.kafkacommon.event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderConfirmedEvent extends DomainEvent {
    private UUID orderId;
    private UUID userId;
    private boolean success;
    private Long capturedMinor;
    private Long goodsMinor;
    private Integer rateBps;
    private Long taxMinor;
    private UUID walletCaptureEntryId;
    private List<Line> lines = new ArrayList<>();
    private String shipRecipient;
    private String shipLine1;
    private String shipLine2;
    private String shipCity;
    private String shipRegion;
    private String shipPostalCode;
    private String shipPhone;
    private String shipCountry;

    @Data
    public static class Line {
        private UUID productId;
        private int quantity;
    }
}
