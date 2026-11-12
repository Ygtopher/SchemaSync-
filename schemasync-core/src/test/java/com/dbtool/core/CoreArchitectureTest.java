package com.dbtool.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architectural guardrail enforcing that {@code schemasync-core} stays 100% free of
 * Swing/AWT UI toolkit dependencies so CLI and headless execution remain fast and portable.
 */
public class CoreArchitectureTest {

    @Test
    void testSchemasyncCoreHasZeroSwingOrAwtImports() throws Exception {
        Path coreSrcRoot = Paths.get("src/main/java").toAbsolutePath();
        assertTrue(Files.isDirectory(coreSrcRoot), "Expected core source directory at " + coreSrcRoot);

        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(coreSrcRoot)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String trimmed = lines.get(i).trim();
                        if (trimmed.contains("javax.swing.") || trimmed.contains("java.awt.")) {
                            violations.add(coreSrcRoot.relativize(file) + ":" + (i + 1) + " -> " + trimmed);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertTrue(violations.isEmpty(), "schemasync-core must contain zero Swing/AWT references, found:\n" + String.join("\n", violations));
    }
}
