package com.dbtool;

import com.dbtool.core.script.ScriptTokenizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EndToEndScriptTest {
    @Test
    void testScriptPipeline() {
        ScriptTokenizer tok = new ScriptTokenizer();
        assertNotNull(tok.tokenize("SELECT 1;"));
    }
}
