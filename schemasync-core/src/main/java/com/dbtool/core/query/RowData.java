package com.dbtool.core.query;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RowData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Object[] values;

    public RowData(Object[] values) {
        this.values = values != null ? Arrays.copyOf(values, values.length) : new Object[0];
    }

    public RowData(List<Object> values) {
        this.values = values != null ? values.toArray() : new Object[0];
    }

    public Object getValue(int index) {
        return (index >= 0 && index < values.length) ? values[index] : null;
    }

    public int size() { return values.length; }
    public List<Object> getValues() { return Arrays.asList(values); }
}
