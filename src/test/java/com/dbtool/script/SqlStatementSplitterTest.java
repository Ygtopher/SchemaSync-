package com.dbtool.script;

import com.dbtool.core.script.SqlStatementSplitter;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class SqlStatementSplitterTest {
    @Test
    void testStandardSplits() {
        String sql = "SELECT 1; UPDATE users SET name='O''Reilly'; DELETE FROM logs;";
        List<String> stmts = SqlStatementSplitter.splitStatements(sql);
        assertEquals(3, stmts.size());
        assertEquals("SELECT 1", stmts.get(0));
        assertEquals("UPDATE users SET name='O''Reilly'", stmts.get(1));
    }
}
