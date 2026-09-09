package com.dbtool.core.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VariableContext {
    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    public void set(String name, Object value) {
        if (name != null) variables.put(name, value);
    }

    public Object get(String name) {
        return name != null ? variables.get(name) : null;
    }

    public boolean has(String name) {
        return name != null && variables.containsKey(name);
    }

    public Map<String, Object> getAll() {
        return variables;
    }
}
