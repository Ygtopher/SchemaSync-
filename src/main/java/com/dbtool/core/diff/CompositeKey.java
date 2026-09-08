package com.dbtool.core.diff;

import java.io.Serializable;
import java.util.Arrays;

public class CompositeKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Object[] components;

    public CompositeKey(Object... components) {
        this.components = components != null ? components : new Object[0];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompositeKey that = (CompositeKey) o;
        return Arrays.equals(components, that.components);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(components);
    }
}
