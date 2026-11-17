package com.orderprocessing.webui.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public class WalletCreditForm {
    @NotNull
    private UUID userId;
    @Positive
    private long amount;
    @NotBlank
    private String reason;
    @NotBlank
    private String idempotencyKey;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
