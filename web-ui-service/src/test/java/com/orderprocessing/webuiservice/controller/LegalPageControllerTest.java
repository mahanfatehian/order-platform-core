package com.orderprocessing.webuiservice.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegalPageControllerTest {
    @Test
    void pagesDoNotInventACompanyOrALaw() throws Exception {
        for (String name : new String[] {"terms.html", "privacy.html", "contact.html"}) {
            String text = Files.readString(page(name));
            assertThat(text).contains("operator fills");
            assertThat(text.toLowerCase()).doesNotContain("vat");
            assertThat(text).doesNotContain("Tehran");
            assertThat(text).doesNotContain("limited company");
        }
    }

    @Test
    void routesMatchTheThreePages() {
        LegalPageController controller = new LegalPageController();
        assertThat(controller.terms()).isEqualTo("legal/terms");
        assertThat(controller.privacy()).isEqualTo("legal/privacy");
        assertThat(controller.contact()).isEqualTo("legal/contact");
    }

    private static Path page(String name) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("web-ui-service/src/main/resources/templates/legal").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("page not found: " + name);
    }
}
