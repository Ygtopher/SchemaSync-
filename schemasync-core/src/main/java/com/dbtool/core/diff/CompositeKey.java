package com.dbtool.core.diff;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable, canonicalized composite key supporting cross-engine equality, hashing,
 * and deterministic ordering for keyset pagination and 2-phase chunked diffs.
 */
public class CompositeKey implements Serializable, Comparable<CompositeKey> {
    private static final long serialVersionUID = 2L;

    private final Object[] rawComponents;
    private final Object[] canonicalComponents;

    public CompositeKey(Object... components) {
        if (components == null) {
            this.rawComponents = new Object[0];
            this.canonicalComponents = new Object[0];
        } else {
            this.rawComponents = Arrays.copyOf(components, components.length);
            this.canonicalComponents = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                this.canonicalComponents[i] = CanonicalValueNormalizer.canonicalizeKeyValue(components[i]);
            }
        }
    }

    public Object[] getComponents() {
        return Arrays.copyOf(rawComponents, rawComponents.length);
    }

    public Object getComponent(int index) {
        return (index >= 0 && index < rawComponents.length) ? rawComponents[index] : null;
    }

    public int size() {
        return rawComponents.length;
    }

    public Object toDisplayValue() {
        if (rawComponents.length == 1) {
            return rawComponents[0];
        }
        return toDisplayString();
    }

    public String toDisplayString() {
        if (rawComponents.length == 1) {
            return Objects.toString(rawComponents[0], "NULL");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rawComponents.length; i++) {
            if (i > 0) sb.append(" | ");
            sb.append(Objects.toString(rawComponents[i], "NULL"));
        }
        return sb.toString();
    }

    @Override
    public int compareTo(CompositeKey other) {
        if (other == null) return 1;
        int len = Math.min(this.canonicalComponents.length, other.canonicalComponents.length);
        for (int i = 0; i < len; i++) {
            int cmp = CanonicalValueNormalizer.compareCanonical(this.canonicalComponents[i], other.canonicalComponents[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(this.canonicalComponents.length, other.canonicalComponents.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompositeKey that = (CompositeKey) o;
        return Arrays.equals(canonicalComponents, that.canonicalComponents);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(canonicalComponents);
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
