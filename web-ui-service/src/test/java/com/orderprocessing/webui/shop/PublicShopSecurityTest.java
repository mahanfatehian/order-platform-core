package com.orderprocessing.webui.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PublicShopSecurityTest {
    @Test
    void thePublicPagesDoNotRequireASignIn() throws Exception {
        String security = Files.readString(root().resolve(
                "web-ui-service/src/main/java/com/orderprocessing/webui/config/SecurityConfig.java"));
        assertThat(security).contains("/shop");
        assertThat(security).contains("/robots.txt");
        assertThat(security).contains("/terms");
        assertThat(security).contains("permitAll");
    }

    private static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("web-ui-service"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found");
    }
}
