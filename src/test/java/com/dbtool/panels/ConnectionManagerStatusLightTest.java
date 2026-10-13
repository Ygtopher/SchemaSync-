package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.panels.ConnectionManagerDialog.ConnectionStatus;
import com.dbtool.panels.ConnectionManagerDialog.StatusInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionManagerStatusLightTest {

    private DatabaseManager dbManager;
    private final String pUnreachable = "unreach_user@192.0.2.1:5432/test_unreachable_db";
    private final String pLocalFile = "fileuser@:0/sample_test_local.accdb";
    private File tempDbFile;

    @BeforeEach
    void setUp() throws IOException {
        dbManager = new DatabaseManager();
        // 192.0.2.0/24 is TEST-NET-1 (RFC 5737), guaranteed to be unroutable/unreachable
        dbManager.saveConnectionProfile(pUnreachable, "192.0.2.1", "5432", "unreach_user", "pass", "test_unreachable_db");

        // Temporary file for file-based database check
        tempDbFile = File.createTempFile("sample_test_local", ".accdb");
        dbManager.saveConnectionProfile(pLocalFile, "", "0", "fileuser", "", tempDbFile.getAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.deleteConnectionProfile(pUnreachable);
            dbManager.deleteConnectionProfile(pLocalFile);
        }
        if (tempDbFile != null && tempDbFile.exists()) {
            tempDbFile.delete();
        }
    }

    @Test
    void testStatusDotIcons() {
        Icon onlineIcon = ConnectionManagerDialog.getStatusIcon(ConnectionStatus.ONLINE);
        Icon offlineIcon = ConnectionManagerDialog.getStatusIcon(ConnectionStatus.OFFLINE);
        Icon checkingIcon = ConnectionManagerDialog.getStatusIcon(ConnectionStatus.CHECKING);
        Icon unknownIcon = ConnectionManagerDialog.getStatusIcon(ConnectionStatus.UNKNOWN);

        assertNotNull(onlineIcon);
        assertNotNull(offlineIcon);
        assertNotNull(checkingIcon);
        assertNotNull(unknownIcon);

        assertTrue(onlineIcon.getIconWidth() > 0);
        assertTrue(onlineIcon.getIconHeight() > 0);
    }

    @Test
    void testConnectivityDetection() {
        ConnectionManagerDialog dialog = new ConnectionManagerDialog(null, dbManager);

        // 1. Test unreachable host returns OFFLINE (red light) with diagnostic message
        StatusInfo unreachableInfo = dialog.testProfileConnectivity(pUnreachable);
        assertNotNull(unreachableInfo);
        assertEquals(ConnectionStatus.OFFLINE, unreachableInfo.status, "Unreachable host should report OFFLINE");
        assertFalse(unreachableInfo.message.isEmpty(), "Error message should not be empty");

        // 2. Test local file that exists returns ONLINE (green light)
        StatusInfo localFileInfo = dialog.testProfileConnectivity(pLocalFile);
        assertNotNull(localFileInfo);
        assertEquals(ConnectionStatus.ONLINE, localFileInfo.status, "Existing DB file should report ONLINE");

        // 3. Test non-existent profile returns UNKNOWN
        StatusInfo notFound = dialog.testProfileConnectivity("bogus_non_existent_profile_xyz");
        assertNotNull(notFound);
        assertEquals(ConnectionStatus.UNKNOWN, notFound.status);

        dialog.dispose();
    }

    @Test
    void testStatusFilteringInSearch() {
        ConnectionManagerDialog dialog = new ConnectionManagerDialog(null, dbManager);

        // Set status in status map manually to test filtering
        dialog.getStatusMap().put(pUnreachable, new StatusInfo(ConnectionStatus.OFFLINE, "Connection timed out", 1500));
        dialog.getStatusMap().put(pLocalFile, new StatusInfo(ConnectionStatus.ONLINE, "Connected", 10));

        // Filter by "online"
        dialog.setSearchQuery("online");
        DefaultListModel<String> model = dialog.getProfileListModel();
        assertTrue(model.contains(pLocalFile));
        assertFalse(model.contains(pUnreachable));

        // Filter by "offline"
        dialog.setSearchQuery("offline");
        assertTrue(model.contains(pUnreachable));
        assertFalse(model.contains(pLocalFile));

        // Reset filter
        dialog.setSearchQuery("");
        assertTrue(model.contains(pLocalFile));
        assertTrue(model.contains(pUnreachable));

        dialog.dispose();
    }
}
