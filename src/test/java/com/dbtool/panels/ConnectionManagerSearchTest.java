package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionManagerSearchTest {

    private DatabaseManager dbManager;
    private final String p1 = "tstuser1@192.168.254.101:5432/uniquedb_alpha";
    private final String p2 = "tstuser1@192.168.254.102:5432/uniquedb_beta";
    private final String p3 = "tstuser2@10.254.254.103:5432/otherdb_gamma";

    @BeforeEach
    void setUp() {
        dbManager = new DatabaseManager();
        dbManager.saveConnectionProfile(p1, "192.168.254.101", "5432", "tstuser1", "pass1", "uniquedb_alpha");
        dbManager.saveConnectionProfile(p2, "192.168.254.102", "5432", "tstuser1", "pass2", "uniquedb_beta");
        dbManager.saveConnectionProfile(p3, "10.254.254.103", "5432", "tstuser2", "pass3", "otherdb_gamma");
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.deleteConnectionProfile(p1);
            dbManager.deleteConnectionProfile(p2);
            dbManager.deleteConnectionProfile(p3);
        }
    }

    @Test
    void testConnectionProfileSearchAndFiltering() {
        ConnectionManagerDialog dialog = new ConnectionManagerDialog(null, dbManager);
        DefaultListModel<String> model = dialog.getProfileListModel();

        // Initially all profiles are loaded
        assertTrue(model.size() >= 3, "Expected at least 3 profiles");
        assertTrue(model.contains(p1));
        assertTrue(model.contains(p2));
        assertTrue(model.contains(p3));

        // Filter by unique database name "uniquedb_alpha"
        dialog.setSearchQuery("uniquedb_alpha");
        assertEquals(1, model.size());
        assertEquals(p1, model.get(0));

        // Filter by unique subnet host IP "192.168.254" (matches p1 and p2)
        dialog.setSearchQuery("192.168.254");
        assertEquals(2, model.size());
        assertTrue(model.contains(p1));
        assertTrue(model.contains(p2));
        assertFalse(model.contains(p3));

        // Filter by unique username "tstuser2"
        dialog.setSearchQuery("tstuser2");
        assertEquals(1, model.size());
        assertTrue(model.contains(p3));
        assertFalse(model.contains(p1));

        // Filter by non-matching query
        dialog.setSearchQuery("non_existent_xyz_987654");
        assertEquals(0, model.size());

        // Clear filter
        dialog.setSearchQuery("");
        assertTrue(model.size() >= 3);
        assertTrue(model.contains(p1));
        assertTrue(model.contains(p2));
        assertTrue(model.contains(p3));

        dialog.dispose();
    }
}
