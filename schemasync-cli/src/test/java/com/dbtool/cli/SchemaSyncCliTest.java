package com.dbtool.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaSyncCliTest {

    @TempDir
    Path tempDir;

    @Test
    void testDriftCheckPassesAndFailsAppropriately() throws Exception {
        String db1 = "jdbc:sqlite:" + tempDir.resolve("src.db");
        String db2 = "jdbc:sqlite:" + tempDir.resolve("dst.db");

        try (Connection c1 = DriverManager.getConnection(db1); Statement s1 = c1.createStatement()) {
            s1.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, email TEXT)");
        }
        try (Connection c2 = DriverManager.getConnection(db2); Statement s2 = c2.createStatement()) {
            s2.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, email TEXT)");
        }

        int passCode = SchemaSyncCli.execute("drift-check", "--source", db1, "--target", db2);
        assertEquals(0, passCode, "Identical schemas must pass drift-check with exit code 0");

        // Introduce schema drift in db2
        try (Connection c2 = DriverManager.getConnection(db2); Statement s2 = c2.createStatement()) {
            s2.execute("ALTER TABLE users ADD COLUMN role TEXT");
        }

        int failCode = SchemaSyncCli.execute("drift-check", "--source", db1, "--target", db2);
        assertEquals(SchemaSyncCli.EXIT_DRIFT_DETECTED, failCode, "Drifted schemas must fail drift-check with exit code 2");

        int diffCode = SchemaSyncCli.execute("diff", "--source", db1, "--target", db2, "--ddl");
        assertEquals(0, diffCode, "diff without --fail-on-drift reports exit code 0");
    }

    @Test
    void testExportCsvAndJson() throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("export.db");
        try (Connection c = DriverManager.getConnection(dbUrl); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, customer TEXT, total INTEGER)");
            s.execute("INSERT INTO orders VALUES (1, 'Alice', 120), (2, 'Bob', 250)");
        }

        Path csvOut = tempDir.resolve("orders.csv");
        int csvCode = SchemaSyncCli.execute(
                "export", "--url", dbUrl, "--query", "SELECT id, customer, total FROM orders ORDER BY id",
                "--format", "csv", "--out", csvOut.toString()
        );
        assertEquals(0, csvCode);
        assertTrue(Files.exists(csvOut));
        String csvContent = Files.readString(csvOut, StandardCharsets.UTF_8);
        assertTrue(csvContent.contains("Alice"));
        assertTrue(csvContent.contains("250"));

        Path jsonOut = tempDir.resolve("orders.json");
        int jsonCode = SchemaSyncCli.execute(
                "export", "--url", dbUrl, "--query", "SELECT id, customer, total FROM orders ORDER BY id",
                "--format", "json", "--out", jsonOut.toString()
        );
        assertEquals(0, jsonCode);
        assertTrue(Files.exists(jsonOut));
        String jsonContent = Files.readString(jsonOut, StandardCharsets.UTF_8);
        assertTrue(jsonContent.contains("\"customer\""));
        assertTrue(jsonContent.contains("\"Bob\""));
    }
}
