package com.dbtool.script;

import com.dbtool.core.script.SqlCommentStripper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SqlCommentStripperTest {
    @Test
    void testStrip() {
        String sql = "-- header comment\nSELECT /* inline comment */ 1;";
        String stripped = SqlCommentStripper.stripComments(sql);
        assertTrue(stripped.contains("SELECT  1;"));
    }
}
