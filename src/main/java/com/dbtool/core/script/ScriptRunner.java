package com.dbtool.core.script;

import com.dbtool.core.query.QueryResult;
import com.dbtool.core.query.StatementRunner;
import java.sql.Statement;
import java.util.List;

public class ScriptRunner {
    private final StatementRunner statementRunner = new StatementRunner();

    public void runScript(String script, ScriptExecutionContext ctx) throws Exception {
        ScriptTokenizer tokenizer = new ScriptTokenizer();
        List<ScriptToken> tokens = tokenizer.tokenize(script);

        String lastSql = null;
        for (ScriptToken token : tokens) {
            if (token.getType() == TokenType.META_COMMAND) {
                ParsedMetaCommand cmd = PsqlMetaCommandParser.parse(token.getContent());
                if (cmd != null) {
                    if (cmd.getCommand() == PsqlMetaCommand.SET) {
                        ctx.getVariableContext().set(cmd.getArgument(), cmd.getValue());
                    } else if (cmd.getCommand() == PsqlMetaCommand.ECHO) {
                        String msg = VariableReplacer.replaceVariables(cmd.getValue(), ctx.getVariableContext());
                        ctx.getOutputLogger().log(msg);
                    } else if (cmd.getCommand() == PsqlMetaCommand.GSET && lastSql != null) {
                        QueryResult qr = statementRunner.executeQuery(ctx.getConnection(), lastSql);
                        GsetHandler.applyGset(qr, cmd.getArgument(), ctx.getVariableContext());
                    }
                }
            } else if (token.getType() == TokenType.SQL_STATEMENT) {
                String sql = VariableReplacer.replaceVariables(token.getContent(), ctx.getVariableContext());
                lastSql = sql;
                try (Statement stmt = ctx.getConnection().createStatement()) {
                    stmt.execute(sql);
                }
            }
        }
    }
}
