package com.orderprocessing.webui;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.dto.PageResponse;
import com.orderprocessing.webui.dto.ServiceStatusView;
import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.exception.SessionExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "app.services.store-internal-api-key=test-only-store-internal-key-0123456789",
        "spring.data.redis.password=test-only"
})
@AutoConfigureMockMvc
class AdminDashboardMvcTest {
    @Autowired MockMvc mvc;
    @MockBean AuthenticatedPlatformClient client;
    @MockBean PlatformClient platformClient;

    @BeforeEach
    void healthAlwaysRenders() {
        // serviceHealth catches each probe internally, so the page renders even when every metric call fails.
        when(client.serviceHealth()).thenReturn(List.of(new ServiceStatusView("Orders", false, "Unavailable")));
    }

    @Test
    void showsAMetricAsUnreadableRatherThanZeroWhenItsBackendFails() throws Exception {
        when(client.adminUsers(anyInt(), anyInt(), anyString()))
                .thenThrow(new BackendClientException(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "down", Map.of()));
        when(client.adminProducts(anyInt(), anyInt(), anyString())).thenReturn(PageResponse.empty(0, 50));
        when(client.inventory(anyInt(), anyInt(), anyString())).thenReturn(PageResponse.empty(0, 50));
        when(client.adminOrders(anyInt(), anyInt(), any(), anyString())).thenReturn(PageResponse.empty(0, 1));

        mvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Could not be read")));
    }

    @Test
    void keepsRenderingRealNumbersWhenEveryBackendAnswers() throws Exception {
        when(client.adminUsers(anyInt(), anyInt(), anyString())).thenReturn(PageResponse.empty(0, 1));
        when(client.adminProducts(anyInt(), anyInt(), anyString())).thenReturn(PageResponse.empty(0, 50));
        when(client.inventory(anyInt(), anyInt(), anyString())).thenReturn(PageResponse.empty(0, 50));
        when(client.adminOrders(anyInt(), anyInt(), any(), anyString())).thenReturn(PageResponse.empty(0, 1));

        mvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Could not be read"))))
                .andExpect(content().string(containsString("Registered accounts")));
    }

    @Test
    void sendsAnExpiredSessionBackToSignInInsteadOfADashboardOfZeros() throws Exception {
        when(client.adminUsers(anyInt(), anyInt(), anyString()))
                .thenThrow(new SessionExpiredException("Your session expired", null));

        mvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection());
    }
}
