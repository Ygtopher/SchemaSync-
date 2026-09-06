package com.dbtool.core.query;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RowData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<Object> values;

    public RowData() {
        this.values = new ArrayList<>();
    }

    public RowData(List<Object> values) {
        this.values = values != null ? new ArrayList<>(values) : new ArrayList<>();
    }

    public void addValue(Object val) { values.add(val); }
    public Object getValue(int index) {
        return (index >= 0 && index < values.size()) ? values.get(index) : null;
    }
    public int size() { return values.size(); }
    public List<Object> getValues() { return values; }
}
