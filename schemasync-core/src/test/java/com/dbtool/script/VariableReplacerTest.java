package com.dbtool.script;

import com.dbtool.core.script.VariableContext;
import com.dbtool.core.script.VariableReplacer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VariableReplacerTest {
    @Test
    void testReplacement() {
        VariableContext ctx = new VariableContext();
        ctx.set("tbl", "users");
        ctx.set("name", "Bob");

        String sql = "SELECT * FROM :tbl WHERE name = :'name'";
        String result = VariableReplacer.replaceVariables(sql, ctx);
        assertEquals("SELECT * FROM users WHERE name = 'Bob'", result);
    }
}
