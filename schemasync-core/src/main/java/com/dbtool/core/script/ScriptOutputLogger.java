package com.dbtool.core.script;

import java.util.ArrayList;
import java.util.List;

public class ScriptOutputLogger {
    private final List<String> logs = new ArrayList<>();

    public void log(String msg) {
        logs.add(msg);
    }

    public List<String> getLogs() { return logs; }
}
