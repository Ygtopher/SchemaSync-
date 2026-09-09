package com.dbtool.script;

import com.dbtool.core.script.SqlStatementSplitter;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class SqlStatementSplitterDollarQuotesTest {
    @Test
    void testDollarQuotes() {
        String sql = "CREATE FUNCTION foo() RETURNS void AS $$ BEGIN SELECT 1; END; $$ LANGUAGE plpgsql; SELECT 2;";
        List<String> stmts = SqlStatementSplitter.splitStatements(sql);
        assertEquals(2, stmts.size());
    }
}
