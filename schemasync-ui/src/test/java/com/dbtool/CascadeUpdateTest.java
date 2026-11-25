package com.dbtool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise automated unit tests for SchemaSync's Multi-Table Cascade Update Engine.
 * Verifies relational dependency graph traversal, column mapping detection,
 * transaction execution, and rollback safeguards.
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class CascadeUpdateTest {

    private DatabaseManager dbManager;
    private Connection rawConn;

    @BeforeEach
    void setUp() throws Exception {
        dbManager = new DatabaseManager();
        // Use in-memory SQLite database
        rawConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        dbManager.connection = rawConn;

        try (Statement st = rawConn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");

            // 1. Root Users table
            st.execute("CREATE TABLE users (" +
                    "id INTEGER PRIMARY KEY, " +
                    "first_name TEXT, " +
                    "last_name TEXT, " +
                    "email TEXT" +
                    ");");

            // 2. Profiles table with direct exact match 'first_name' and foreign key to users.id
            st.execute("CREATE TABLE profiles (" +
                    "id INTEGER PRIMARY KEY, " +
                    "user_id INTEGER, " +
                    "first_name TEXT, " +
                    "bio TEXT, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id)" +
                    ");");

            // 3. Orders table with suffix match 'customer_first_name' and foreign key customer_id -> users.id
            st.execute("CREATE TABLE orders (" +
                    "id INTEGER PRIMARY KEY, " +
                    "customer_id INTEGER, " +
                    "customer_first_name TEXT, " +
                    "amount REAL, " +
                    "FOREIGN KEY (customer_id) REFERENCES users(id)" +
                    ");");

            // 4. Addresses table with no first_name column
            st.execute("CREATE TABLE addresses (" +
                    "id INTEGER PRIMARY KEY, " +
                    "user_id INTEGER, " +
                    "city TEXT, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id)" +
                    ");");

            // Seed sample data
            st.execute("INSERT INTO users (id, first_name, last_name, email) VALUES (1, 'John', 'Doe', 'john@example.com');");
            st.execute("INSERT INTO users (id, first_name, last_name, email) VALUES (2, 'Jane', 'Smith', 'jane@example.com');");

            st.execute("INSERT INTO profiles (id, user_id, first_name, bio) VALUES (101, 1, 'John', 'Software Architect');");
            st.execute("INSERT INTO profiles (id, user_id, first_name, bio) VALUES (102, 2, 'Jane', 'Data Scientist');");

            st.execute("INSERT INTO orders (id, customer_id, customer_first_name, amount) VALUES (201, 1, 'John', 149.99);");
            st.execute("INSERT INTO orders (id, customer_id, customer_first_name, amount) VALUES (202, 1, 'John', 89.50);");
            st.execute("INSERT INTO orders (id, customer_id, customer_first_name, amount) VALUES (203, 2, 'Jane', 29.99);");

            st.execute("INSERT INTO addresses (id, user_id, city) VALUES (301, 1, 'San Francisco');");
            st.execute("INSERT INTO addresses (id, user_id, city) VALUES (302, 2, 'Seattle');");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (rawConn != null && !rawConn.isClosed()) {
            rawConn.close();
        }
    }

    @Test
    void testBuildCascadeUpdatePlanDiscoversRelatedTablesAndColumns() {
        Map<String, Object> targetRow = new HashMap<>();
        targetRow.put("id", 1);
        targetRow.put("first_name", "John");

        Map<String, Object> fieldUpdates = new HashMap<>();
        fieldUpdates.put("first_name", "Jonathan");

        DatabaseManager.CascadeUpdatePlan plan = dbManager.buildCascadeUpdatePlan(
                "users",
                Collections.singletonList(targetRow),
                fieldUpdates
        );

        assertNotNull(plan);
        assertEquals("users", plan.rootTable);
        assertEquals(1, plan.rootNodes.size());

        // Users mapping
        DatabaseManager.CascadeUpdateTableMapping userMapping = plan.tableMappings.get("users");
        assertNotNull(userMapping);
        assertTrue(userMapping.isIncluded);
        assertEquals("first_name", userMapping.targetColumn);
        assertEquals("Jonathan", userMapping.newValue);

        // Profiles mapping (exact column match)
        DatabaseManager.CascadeUpdateTableMapping profileMapping = plan.tableMappings.get("profiles");
        assertNotNull(profileMapping);
        assertTrue(profileMapping.isIncluded);
        assertEquals("first_name", profileMapping.targetColumn);
        assertEquals("Jonathan", profileMapping.newValue);

        // Orders mapping (suffix column match: customer_first_name)
        DatabaseManager.CascadeUpdateTableMapping orderMapping = plan.tableMappings.get("orders");
        assertNotNull(orderMapping);
        assertTrue(orderMapping.isIncluded);
        assertEquals("customer_first_name", orderMapping.targetColumn);
        assertEquals("Jonathan", orderMapping.newValue);

        // SQL Script should be generated
        assertNotNull(plan.fullScript);
        assertTrue(plan.fullScript.contains("UPDATE"));
        assertTrue(plan.fullScript.contains("Jonathan"));
    }

    @Test
    void testExecuteCascadeUpdatePlanAtomicallyUpdatesAllLinkedTables() throws Exception {
        Map<String, Object> targetRow = new HashMap<>();
        targetRow.put("id", 1);
        targetRow.put("first_name", "John");

        Map<String, Object> fieldUpdates = new HashMap<>();
        fieldUpdates.put("first_name", "Jonathan");

        DatabaseManager.CascadeUpdatePlan plan = dbManager.buildCascadeUpdatePlan(
                "users",
                Collections.singletonList(targetRow),
                fieldUpdates
        );

        // Execute safe cascade update
        dbManager.executeCascadeUpdatePlan(plan);

        // Verify root users table updated
        try (Statement st = rawConn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT first_name FROM users WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("Jonathan", rs.getString("first_name"));
            }
            // User 2 should NOT be updated
            try (ResultSet rs = st.executeQuery("SELECT first_name FROM users WHERE id = 2")) {
                assertTrue(rs.next());
                assertEquals("Jane", rs.getString("first_name"));
            }

            // Verify profiles table updated for user 1
            try (ResultSet rs = st.executeQuery("SELECT first_name FROM profiles WHERE user_id = 1")) {
                assertTrue(rs.next());
                assertEquals("Jonathan", rs.getString("first_name"));
            }
            // Profile for user 2 should remain Jane
            try (ResultSet rs = st.executeQuery("SELECT first_name FROM profiles WHERE user_id = 2")) {
                assertTrue(rs.next());
                assertEquals("Jane", rs.getString("first_name"));
            }

            // Verify orders table updated for customer_id = 1
            try (ResultSet rs = st.executeQuery("SELECT customer_first_name FROM orders WHERE customer_id = 1")) {
                int count = 0;
                while (rs.next()) {
                    assertEquals("Jonathan", rs.getString("customer_first_name"));
                    count++;
                }
                assertEquals(2, count);
            }
            // Order for customer_id = 2 should remain Jane
            try (ResultSet rs = st.executeQuery("SELECT customer_first_name FROM orders WHERE customer_id = 2")) {
                assertTrue(rs.next());
                assertEquals("Jane", rs.getString("customer_first_name"));
            }

            // Addresses should remain intact
            try (ResultSet rs = st.executeQuery("SELECT city FROM addresses WHERE user_id = 1")) {
                assertTrue(rs.next());
                assertEquals("San Francisco", rs.getString("city"));
            }
        }
    }

    @Test
    void testTableExclusionPreventsUpdateOnExcludedTable() throws Exception {
        Map<String, Object> targetRow = new HashMap<>();
        targetRow.put("id", 1);
        targetRow.put("first_name", "John");

        Map<String, Object> fieldUpdates = new HashMap<>();
        fieldUpdates.put("first_name", "Jonathan");

        Set<String> excluded = new HashSet<>();
        excluded.add("orders");

        DatabaseManager.CascadeUpdatePlan plan = dbManager.buildCascadeUpdatePlan(
                "users",
                Collections.singletonList(targetRow),
                fieldUpdates,
                null,
                null,
                excluded
        );

        // Verify orders is excluded
        DatabaseManager.CascadeUpdateTableMapping orderMapping = plan.tableMappings.get("orders");
        assertNotNull(orderMapping);
        assertFalse(orderMapping.isIncluded);

        dbManager.executeCascadeUpdatePlan(plan);

        // Users and profiles are updated, but orders is NOT
        try (Statement st = rawConn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT first_name FROM users WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("Jonathan", rs.getString("first_name"));
            }
            try (ResultSet rs = st.executeQuery("SELECT first_name FROM profiles WHERE user_id = 1")) {
                assertTrue(rs.next());
                assertEquals("Jonathan", rs.getString("first_name"));
            }
            try (ResultSet rs = st.executeQuery("SELECT customer_first_name FROM orders WHERE customer_id = 1")) {
                while (rs.next()) {
                    assertEquals("John", rs.getString("customer_first_name"), "Excluded table must retain original value");
                }
            }
        }
    }

    @Test
    void testMultiRowTargetUpdatesLinkedRecordsForAllTargetIds() throws Exception {
        List<Map<String, Object>> targetRows = new ArrayList<>();
        Map<String, Object> u1 = new HashMap<>();
        u1.put("id", 1);
        u1.put("first_name", "John");
        targetRows.add(u1);

        Map<String, Object> u2 = new HashMap<>();
        u2.put("id", 2);
        u2.put("first_name", "Jane");
        targetRows.add(u2);

        Map<String, Object> fieldUpdates = new HashMap<>();
        fieldUpdates.put("first_name", "GlobalName");

        DatabaseManager.CascadeUpdatePlan plan = dbManager.buildCascadeUpdatePlan(
                "users",
                targetRows,
                fieldUpdates
        );

        dbManager.executeCascadeUpdatePlan(plan);

        // Both user 1 and user 2 should be updated
        try (Statement st = rawConn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE first_name = 'GlobalName'")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM profiles WHERE first_name = 'GlobalName'")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM orders WHERE customer_first_name = 'GlobalName'")) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1));
            }
        }
    }
}
