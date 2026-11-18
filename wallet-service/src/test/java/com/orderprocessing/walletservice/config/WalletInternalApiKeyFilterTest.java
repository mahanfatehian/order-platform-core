package com.orderprocessing.walletservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WalletInternalApiKeyFilterTest {
    @Test
    void aCustomerRequestWithoutTheInternalKeyCannotCapture() throws Exception {
        WalletInternalSecurityProperties properties = new WalletInternalSecurityProperties();
        properties.setApiKey("wallet-key");
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        WalletInternalApiKeyFilter filter = new WalletInternalApiKeyFilter(properties, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/api/wallets/internal/holds/" + UUID.randomUUID() + "/capture");
        request.addHeader("Authorization", "Bearer customer-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((jakarta.servlet.http.HttpServletResponse) servletResponse).setStatus(204));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_INTERNAL_API_KEY");
    }
}
