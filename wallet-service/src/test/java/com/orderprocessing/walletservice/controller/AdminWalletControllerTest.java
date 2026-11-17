package com.orderprocessing.walletservice.controller;

import com.orderprocessing.walletservice.exception.WalletExceptionHandler;
import com.orderprocessing.walletservice.service.WalletLedger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminWalletController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WalletExceptionHandler.class, AdminWalletControllerTest.MethodSecurity.class})
class AdminWalletControllerTest {
    @Autowired MockMvc mvc;
    @MockBean WalletLedger ledger;

    @Test
    @WithMockUser(roles = "USER")
    void aCustomerTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/wallets/{userId}/credits", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10,\"reason\":\"float\",\"idempotencyKey\":\"k\"}"))
                .andExpect(status().isForbidden());
        verify(ledger, never()).credit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE")
    void aWarehouseTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/wallets/{userId}/credits", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10,\"reason\":\"float\",\"idempotencyKey\":\"k\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aZeroAmountIsRejected() throws Exception {
        mvc.perform(post("/api/wallets/{userId}/credits", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"reason\":\"float\",\"idempotencyKey\":\"k\"}"))
                .andExpect(status().isBadRequest());
        verify(ledger, never()).credit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void aCustomerCannotReadTheLedger() throws Exception {
        mvc.perform(get("/api/wallets/{userId}/entries", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void historyCannotBeReplacedOrDeleted() throws Exception {
        UUID userId = UUID.randomUUID();
        mvc.perform(put("/api/wallets/{userId}/entries", userId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/wallets/{userId}/credits", userId))
                .andExpect(status().isMethodNotAllowed());
        verify(ledger, never()).credit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aMissingReasonIsRejected() throws Exception {
        mvc.perform(post("/api/wallets/{userId}/credits", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10,\"reason\":\"\",\"idempotencyKey\":\"k\"}"))
                .andExpect(status().isBadRequest());
    }

    @EnableMethodSecurity
    static class MethodSecurity {
    }
}
