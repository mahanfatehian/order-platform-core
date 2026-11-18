package com.orderprocessing.walletservice.controller;

import com.orderprocessing.walletservice.exception.WalletExceptionHandler;
import com.orderprocessing.walletservice.service.WalletLedger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CustomerWalletController.class, AdminWalletController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({WalletExceptionHandler.class, CustomerWalletControllerTest.MethodSecurity.class})
class CustomerWalletControllerTest {
    @Autowired MockMvc mvc;
    @MockBean WalletLedger ledger;

    @Test
    void theBalanceReadUsesTheTokenOwnerNotACallerSuppliedId() throws Exception {
        UUID owner = UUID.randomUUID();
        when(ledger.balance(owner)).thenReturn(new WalletLedger.Balance(owner, "IRR", 5000, 3000, 2000));
        Jwt token = Jwt.withTokenValue("t").header("alg", "none").claim("userId", owner.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));

        mvc.perform(get("/api/wallets/me/balance"))
                .andExpect(status().isOk());

        verify(ledger).balance(owner);
    }

    @Test
    @WithMockUser(roles = "USER")
    void aCustomerCannotReadAnotherUsersEntries() throws Exception {
        mvc.perform(get("/api/wallets/{userId}/entries", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aHoldIsReportedAsOpenNotAsAReducedBookedBalance() throws Exception {
        UUID owner = UUID.randomUUID();
        when(ledger.balance(owner)).thenReturn(new WalletLedger.Balance(owner, "IRR", 5000, 3000, 2000));
        when(ledger.entries(owner)).thenReturn(List.of());
        Jwt token = Jwt.withTokenValue("t").header("alg", "none").claim("userId", owner.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));

        mvc.perform(get("/api/wallets/me/balance"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.booked").value(5000))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.available").value(3000))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.openHolds").value(2000));
    }

    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class MethodSecurity {
    }
}
