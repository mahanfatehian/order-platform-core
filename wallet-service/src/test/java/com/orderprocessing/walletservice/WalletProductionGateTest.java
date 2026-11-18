package com.orderprocessing.walletservice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WalletProductionGateTest {

    @Test
    void migrationsDoNotInsertLedgerRows() throws IOException {
        Path migration = repoRoot().resolve("wallet-service/src/main/resources/db/migration");
        assertThat(migration).isDirectory();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(migration)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String text = read(path).toLowerCase(Locale.ROOT);
                if (text.contains("insert into") && text.contains("ledger_entries")) {
                    offenders.add(path.getFileName().toString());
                }
            });
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void internalApiKeyComesFromTheEnvironmentWithoutAQuotedFallback() {
        String yml = read(repoRoot().resolve("wallet-service/src/main/resources/application.yml"));
        assertThat(yml).containsPattern("(?m)^\\s*api-key:\\s*\\$\\{WALLET_SERVICE_INTERNAL_API_KEY}\\s*$");
        assertThat(yml).doesNotContainPattern("\\$\\{[^}\\n]+:\\s*[\"'][^\"'\\n]+[\"']");
        assertThat(yml).doesNotContainPattern("(?m)^\\s*api-key:\\s*[\"']");
    }

    @Test
    void composeWaitsForWalletHealthBeforeOrders() {
        String compose = read(repoRoot().resolve("docker-compose.yml"));
        String wallet = serviceBlock(compose, "wallet-service");
        assertThat(lines(wallet)).anyMatch(line -> line.stripLeading().equals("healthcheck:"));
        assertThat(dependsOnCondition(serviceBlock(compose, "order-service"), "wallet-service"))
                .isEqualTo("service_healthy");
    }

    @Test
    void gatewayHasNoPaymentProviderRoute() {
        String yml = read(repoRoot().resolve("gateway-service/src/main/resources/application.yml"));
        List<String> paths = routePaths(yml);
        assertThat(paths).isNotEmpty();
        assertThat(paths).allSatisfy(path -> assertThat(path.toLowerCase(Locale.ROOT))
                .doesNotContain("zarinpal", "idpay", "stripe", "payment-gateway"));
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("docker-compose.yml"))
                    && Files.isDirectory(dir.resolve("wallet-service"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found from " + Path.of("").toAbsolutePath());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> lines(String text) {
        return List.of(text.split("\n", -1));
    }

    private static String serviceBlock(String compose, String name) {
        String[] file = compose.split("\n", -1);
        int servicesAt = indexOf(file, 0, line -> line.stripLeading().equals("services:"));
        if (servicesAt < 0) {
            throw new IllegalStateException("services missing");
        }
        int start = indexOf(file, servicesAt + 1, line -> line.equals("  " + name + ":"));
        if (start < 0) {
            throw new IllegalStateException("service missing: " + name);
        }
        int end = file.length;
        for (int i = start + 1; i < file.length; i++) {
            if (file[i].matches("  \\S.*") || (!file[i].isBlank() && !file[i].startsWith(" "))) {
                end = i;
                break;
            }
        }
        return String.join("\n", java.util.Arrays.copyOfRange(file, start, end));
    }

    private static String dependsOnCondition(String block, String dependency) {
        String[] file = block.split("\n", -1);
        int dependsAt = indexOf(file, 0, line -> line.stripLeading().equals("depends_on:"));
        if (dependsAt < 0) {
            return "";
        }
        int dependsIndent = indent(file[dependsAt]);
        int dependencyAt = -1;
        for (int i = dependsAt + 1; i < file.length; i++) {
            if (file[i].isBlank()) {
                continue;
            }
            if (indent(file[i]) <= dependsIndent) {
                break;
            }
            if (file[i].stripLeading().equals(dependency + ":")) {
                dependencyAt = i;
                break;
            }
        }
        if (dependencyAt < 0) {
            return "";
        }
        int dependencyIndent = indent(file[dependencyAt]);
        for (int i = dependencyAt + 1; i < file.length; i++) {
            if (file[i].isBlank()) {
                continue;
            }
            if (indent(file[i]) <= dependencyIndent) {
                break;
            }
            String stripped = file[i].stripLeading();
            if (stripped.startsWith("condition:")) {
                return stripped.substring("condition:".length()).trim();
            }
        }
        return "";
    }

    private static List<String> routePaths(String yml) {
        String[] file = yml.split("\n", -1);
        int routesAt = indexOf(file, 0, line -> line.stripLeading().equals("routes:"));
        if (routesAt < 0) {
            throw new IllegalStateException("gateway routes missing");
        }
        int routesIndent = indent(file[routesAt]);
        List<String> paths = new ArrayList<>();
        for (int i = routesAt + 1; i < file.length; i++) {
            if (file[i].isBlank()) {
                continue;
            }
            if (indent(file[i]) <= routesIndent) {
                break;
            }
            int pathAt = file[i].indexOf("Path=");
            if (pathAt >= 0) {
                paths.add(file[i].substring(pathAt + "Path=".length()).trim());
            }
        }
        return paths;
    }

    private static int indexOf(String[] lines, int from, java.util.function.Predicate<String> match) {
        for (int i = from; i < lines.length; i++) {
            if (match.test(lines[i])) {
                return i;
            }
        }
        return -1;
    }

    private static int indent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }
}
