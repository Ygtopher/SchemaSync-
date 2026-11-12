package com.dbtool.script;

import com.dbtool.core.script.SqlStatementSplitter;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class SqlStatementSplitterTest {
    @Test
    void testStandardSplits() {
        String sql = "SELECT 1; UPDATE users SET name='O''Reilly;test'; DELETE FROM logs;";
        List<String> stmts = SqlStatementSplitter.splitStatements(sql);
        assertEquals(3, stmts.size());
        assertEquals("SELECT 1", stmts.get(0));
        assertEquals("UPDATE users SET name='O''Reilly;test'", stmts.get(1));
        assertEquals("DELETE FROM logs", stmts.get(2));
    }

    @Test
    void testLineAndBlockCommentsWithSemicolons() {
        String sql = "-- header; comment\n" +
                     "SELECT /* semi; inside block */ 'a;b' FROM \"my\"\"tbl\";\n" +
                     "/* multi-line;\n" +
                     "   comment; */ INSERT INTO t VALUES (1);";
        List<String> stmts = SqlStatementSplitter.splitStatements(sql);
        assertEquals(2, stmts.size());
        assertTrue(stmts.get(0).contains("SELECT /* semi; inside block */ 'a;b' FROM \"my\"\"tbl\""));
        assertTrue(stmts.get(1).contains("INSERT INTO t VALUES (1)"));
    }

    @Test
    void testNullAndBlankInput() {
        assertTrue(SqlStatementSplitter.splitStatements(null).isEmpty());
        assertTrue(SqlStatementSplitter.splitStatements("   \n\t  ").isEmpty());
    }
}
