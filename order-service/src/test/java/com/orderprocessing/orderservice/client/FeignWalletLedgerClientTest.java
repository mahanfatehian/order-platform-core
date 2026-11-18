package com.orderprocessing.orderservice.client;

import com.orderprocessing.orderservice.exception.ServiceUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeignWalletLedgerClientTest {
    private final WalletServiceFeignClient feign = mock(WalletServiceFeignClient.class);
    private final FeignWalletLedgerClient client = new FeignWalletLedgerClient(feign);

    @Test
    void refundReturnsTheEntryId() {
        UUID captureEntryId = UUID.randomUUID();
        UUID refundEntryId = UUID.randomUUID();
        when(feign.refund(captureEntryId, new WalletServiceFeignClient.WalletKeyRequest("order:refund")))
                .thenReturn(new WalletServiceFeignClient.WalletEntryResponse(
                        refundEntryId, UUID.randomUUID(), "REFUND", 30, "IRR", UUID.randomUUID()));

        assertThat(client.refundCapture(captureEntryId, "order:refund", "corr")).isEqualTo(refundEntryId);
        verify(feign).refund(captureEntryId, new WalletServiceFeignClient.WalletKeyRequest("order:refund"));
    }

    @Test
    void alreadyRefundedDoesNotFailTheCaller() {
        when(feign.refund(any(), any()))
                .thenThrow(conflict("{\"code\":\"ALREADY_REFUNDED\"}"));

        assertThat(client.refundCapture(UUID.randomUUID(), "order:refund", "corr")).isNull();
    }

    @Test
    void anotherConflictFailsClosed() {
        when(feign.refund(any(), any()))
                .thenThrow(conflict("{\"code\":\"NOT_A_CAPTURE\"}"));

        assertThatThrownBy(() -> client.refundCapture(UUID.randomUUID(), "order:refund", "corr"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void aMissingOrInvalidBodyFailsClosed() {
        UUID captureEntryId = UUID.randomUUID();
        when(feign.refund(captureEntryId, new WalletServiceFeignClient.WalletKeyRequest("order:refund")))
                .thenReturn(null);

        assertThatThrownBy(() -> client.refundCapture(captureEntryId, "order:refund", "corr"))
                .isInstanceOf(ServiceUnavailableException.class);

        when(feign.refund(captureEntryId, new WalletServiceFeignClient.WalletKeyRequest("order:refund")))
                .thenReturn(new WalletServiceFeignClient.WalletEntryResponse(null, null, "REFUND", 30, "IRR", null));

        assertThatThrownBy(() -> client.refundCapture(captureEntryId, "order:refund", "corr"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void anyOtherFeignErrorFailsClosed() {
        Request request = Request.create(Request.HttpMethod.POST, "/refund", Map.of(), new byte[0],
                StandardCharsets.UTF_8, new RequestTemplate());
        when(feign.refund(any(), any()))
                .thenThrow(new FeignException.ServiceUnavailable("down", request, null, Map.of()));

        assertThatThrownBy(() -> client.refundCapture(UUID.randomUUID(), "order:refund", "corr"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    private static FeignException.Conflict conflict(String body) {
        Request request = Request.create(Request.HttpMethod.POST, "/refund", Map.of(), new byte[0],
                StandardCharsets.UTF_8, new RequestTemplate());
        return new FeignException.Conflict("conflict", request, body.getBytes(StandardCharsets.UTF_8), Map.of());
    }
}
