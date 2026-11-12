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
        assertTrue(stmts.get(0).contains("BEGIN SELECT 1; END;"));
        assertEquals("SELECT 2", stmts.get(1));
    }

    @Test
    void testNamedDollarTagAndNestedPlpgsql() {
        String sql = "CREATE OR REPLACE FUNCTION calc_total(p_id int) RETURNS int AS $func_body$\n" +
                     "DECLARE v_sum int := 0;\n" +
                     "BEGIN\n" +
                     "  -- inner ; comment\n" +
                     "  SELECT COALESCE(SUM(amount), 0) INTO v_sum FROM orders WHERE user_id = p_id;\n" +
                     "  IF v_sum > 0 THEN RETURN v_sum; END IF;\n" +
                     "  RETURN 0;\n" +
                     "END;\n" +
                     "$func_body$ LANGUAGE plpgsql;\n" +
                     "SELECT calc_total(42);";
        List<String> stmts = SqlStatementSplitter.splitStatements(sql);
        assertEquals(2, stmts.size());
        assertTrue(stmts.get(0).contains("$func_body$"));
        assertEquals("SELECT calc_total(42)", stmts.get(1));
    }
}
