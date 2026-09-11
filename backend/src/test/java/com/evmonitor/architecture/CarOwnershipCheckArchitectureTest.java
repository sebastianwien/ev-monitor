package com.evmonitor.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seit der DSGVO-Anonymisierung kann Car.userId NULL sein. Ein direktes
 * {@code car.getUserId().equals(...)} wirft dann eine NPE (HTTP 500 statt 403).
 * Ownership-Checks müssen über {@code Car.isOwnedBy(...)} laufen.
 */
class CarOwnershipCheckArchitectureTest {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b\\w*[cC]ar\\.getUserId\\(\\)\\s*\\.equals\\(|\\bc\\.getUserId\\(\\)\\s*\\.equals\\(");

    @Test
    void noDirectEqualsOnCarUserId() throws IOException {
        Path root = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            List<String> offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> {
                        try {
                            List<String> lines = Files.readAllLines(p);
                            return java.util.stream.IntStream.range(0, lines.size())
                                    .filter(i -> FORBIDDEN.matcher(lines.get(i)).find())
                                    .mapToObj(i -> root.relativize(p) + ":" + (i + 1) + "  " + lines.get(i).trim());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
            assertTrue(offenders.isEmpty(),
                    "Car.userId kann NULL sein - Car.isOwnedBy(...) verwenden statt:\n" + String.join("\n", offenders));
        }
    }
}
