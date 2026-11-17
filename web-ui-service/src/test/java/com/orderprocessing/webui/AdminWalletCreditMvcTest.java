package com.orderprocessing.webui;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.dto.PageResponse;
import com.orderprocessing.webui.dto.UserView;
import com.orderprocessing.webui.dto.WalletBalanceView;
import com.orderprocessing.webui.dto.WalletEntryView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.session.store-type=none",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "app.security.jwt-secret=test-only-jwt-secret-that-is-long-enough-012345678901234567890123456789",
        "app.services.auth-url=http://localhost:18081",
        "app.services.user-url=http://localhost:18082",
        "app.services.store-url=http://localhost:18083",
        "app.services.order-url=http://localhost:18084",
        "app.services.wallet-url=http://localhost:18086",
        "app.services.store-internal-api-key=test-only-store-internal-key-0123456789",
        "spring.data.redis.password=test-only"
})
@AutoConfigureMockMvc
class AdminWalletCreditMvcTest {
    @Autowired MockMvc mvc;
    @MockBean AuthenticatedPlatformClient client;
    @MockBean PlatformClient platformClient;

    @Test
    void theCreditFormPostsTheCommandAndRendersTheReturnedEntry() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        when(client.adminUsers(0, 100, "")).thenReturn(PageResponse.empty(0, 100));
        when(client.walletEntries(userId)).thenReturn(List.of());
        when(client.creditWallet(eq(userId), eq(2500L), eq("opening float"), eq("credit-key")))
                .thenReturn(new WalletEntryView(entryId, "CREDIT", 2500, "IRR", "opening float", "ADMIN", Instant.now()));

        mvc.perform(post("/admin/wallets").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("userId", userId.toString())
                        .param("amount", "2500")
                        .param("reason", "opening float")
                        .param("idempotencyKey", "credit-key"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(entryId.toString())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2500")));

        verify(client).creditWallet(userId, 2500, "opening float", "credit-key");
    }

    @Test
    void thePageShowsCalculatedBalanceAndNoEditableBalanceField() throws Exception {
        UUID userId = UUID.randomUUID();
        when(client.adminUsers(0, 100, "")).thenReturn(PageResponse.empty(0, 100));
        when(client.walletEntries(userId)).thenReturn(List.of(
                new WalletEntryView(UUID.randomUUID(), "CREDIT", 2500, "IRR", "opening float", "ADMIN", Instant.now())));
        when(client.walletBalance(userId)).thenReturn(new WalletBalanceView(userId, "IRR", 2500, 2500, 0));

        mvc.perform(get("/admin/wallets").with(user("admin").roles("ADMIN")).param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"wallet-balance\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2500")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"balance\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"available\""))));
    }

    @Test
    void aZeroAmountStaysOnTheFormAndDoesNotPost() throws Exception {
        UUID userId = UUID.randomUUID();
        when(client.adminUsers(0, 100, "")).thenReturn(PageResponse.empty(0, 100));
        when(client.walletEntries(userId)).thenReturn(List.of());
        when(client.walletBalance(userId)).thenReturn(null);

        mvc.perform(post("/admin/wallets").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("userId", userId.toString())
                        .param("amount", "0")
                        .param("reason", "opening float")
                        .param("idempotencyKey", "credit-key"))
                .andExpect(status().isOk());

        verify(client, org.mockito.Mockito.never()).creditWallet(any(), any(long.class), any(), any());
    }

    @Test
    void aCustomerCannotOpenTheCreditPage() throws Exception {
        mvc.perform(post("/admin/wallets").with(user("johndoe").roles("USER")).with(csrf())
                        .param("userId", UUID.randomUUID().toString())
                        .param("amount", "1")
                        .param("reason", "no")
                        .param("idempotencyKey", "k"))
                .andExpect(status().isForbidden());
        verify(client, org.mockito.Mockito.never()).creditWallet(any(), any(long.class), any(), any());
    }
}
