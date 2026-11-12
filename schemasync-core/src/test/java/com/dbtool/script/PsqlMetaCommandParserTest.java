package com.dbtool.script;

import com.dbtool.core.script.ParsedMetaCommand;
import com.dbtool.core.script.PsqlMetaCommand;
import com.dbtool.core.script.PsqlMetaCommandParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PsqlMetaCommandParserTest {
    @Test
    void testParseSet() {
        ParsedMetaCommand cmd = PsqlMetaCommandParser.parse("\\set user_id = 42");
        assertNotNull(cmd);
        assertEquals(PsqlMetaCommand.SET, cmd.getCommand());
        assertEquals("user_id", cmd.getArgument());
        assertEquals("42", cmd.getValue());
    }

    @Test
    void testParseEcho() {
        ParsedMetaCommand cmd = PsqlMetaCommandParser.parse("\\echo Current user count: 5");
        assertNotNull(cmd);
        assertEquals(PsqlMetaCommand.ECHO, cmd.getCommand());
        assertEquals("Current user count: 5", cmd.getValue());
    }
}
