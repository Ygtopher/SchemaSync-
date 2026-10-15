package com.dbtool.connection;

import com.dbtool.core.connection.ConnectionUtil;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionUtilTest {

    @Test
    void testIsTimeZoneErrorDetection() {
        SQLException ex1 = new SQLException("Connection Failed: FATAL: invalid value for parameter \"TimeZone\": \"Africa/Kigali\"");
        assertTrue(ConnectionUtil.isTimeZoneError(ex1));

        SQLException ex2 = new SQLException("FATAL: invalid value for parameter 'TimeZone': 'Asia/Calcutta'");
        assertTrue(ConnectionUtil.isTimeZoneError(ex2));

        Exception wrapped = new Exception("Outer wrapper", new RuntimeException("Inner", ex1));
        assertTrue(ConnectionUtil.isTimeZoneError(wrapped));

        SQLException normalErr = new SQLException("FATAL: password authentication failed for user \"postgres\"");
        assertFalse(ConnectionUtil.isTimeZoneError(normalErr));

        SQLException connErr = new SQLException("Connection refused: connect");
        assertFalse(ConnectionUtil.isTimeZoneError(connErr));
    }

    @Test
    void testPotentiallyProblematicZones() {
        assertTrue(ConnectionUtil.isPotentiallyProblematicZone("Africa/Kigali"));
        assertTrue(ConnectionUtil.isPotentiallyProblematicZone("Asia/Calcutta"));
        assertTrue(ConnectionUtil.isPotentiallyProblematicZone("Asia/Saigon"));
        assertTrue(ConnectionUtil.isPotentiallyProblematicZone("Europe/Kiev"));
        assertFalse(ConnectionUtil.isPotentiallyProblematicZone("UTC"));
        assertFalse(ConnectionUtil.isPotentiallyProblematicZone("America/New_York"));
        assertFalse(ConnectionUtil.isPotentiallyProblematicZone(null));
    }

    @Test
    void testEnsureSafeTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Africa/Kigali"));
            assertEquals("Africa/Kigali", TimeZone.getDefault().getID());

            ConnectionUtil.ensureSafeTimeZone();
            assertEquals("UTC", TimeZone.getDefault().getID());
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
