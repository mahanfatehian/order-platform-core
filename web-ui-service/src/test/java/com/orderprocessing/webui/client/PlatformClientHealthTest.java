package com.orderprocessing.webui.client;

import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.dto.ServiceStatusView;
import com.orderprocessing.webui.service.CartQuoteValidator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The admin dashboard renders serviceHealth() on every load. Four probes run one after another cost the operator
 * the sum of four timeouts whenever a backend is wedged, so they have to overlap.
 */
class PlatformClientHealthTest {
    private static final Duration PROBE_DELAY = Duration.ofMillis(400);

    private HttpServer server;
    private final AtomicInteger probeCount = new AtomicInteger();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/actuator/health", exchange -> {
            probeCount.incrementAndGet();
            try {
                Thread.sleep(PROBE_DELAY.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        // A single-threaded executor would serialise the probes on the server side and hide the very thing under
        // test, so the stub answers each one on its own thread.
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theFourBackendProbesOverlap() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        WebUiProperties properties = new WebUiProperties();
        properties.getServices().setAuthUrl(base);
        properties.getServices().setUserUrl(base);
        properties.getServices().setStoreUrl(base);
        properties.getServices().setOrderUrl(base);
        PlatformClient client = new PlatformClient(RestClient.builder(), properties,
                mock(ObjectProvider.class), new CartQuoteValidator());

        long startedAt = System.nanoTime();
        List<ServiceStatusView> statuses = client.serviceHealth();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(probeCount.get()).isEqualTo(4);
        assertThat(statuses).hasSize(6);
        assertThat(statuses.stream().filter(ServiceStatusView::available).count())
                .describedAs("every backend answered UP")
                .isEqualTo(5);
        assertThat(elapsed)
                .describedAs("four %s probes run in parallel, not one after another", PROBE_DELAY)
                .isLessThan(PROBE_DELAY.multipliedBy(3));
    }
}
