package com.orderprocessing.walletservice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WalletDatasourceOwnershipTest {

    @Test
    void onlyThisModuleConfiguresTheWalletDatabase() throws IOException {
        Path root = Path.of("").toAbsolutePath().getParent();
        List<String> owners = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.getFileName().toString().equals("application.yml"))
                    .filter(path -> !path.toString().contains("target"))
                    .forEach(path -> {
                        try {
                            if (Files.readString(path).contains("walletdb")) {
                                owners.add(root.relativize(path).toString().replace('\\', '/'));
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
        assertThat(owners).containsExactly("wallet-service/src/main/resources/application.yml");
    }
}
