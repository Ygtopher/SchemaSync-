package com.dbtool.core.diff;

import java.io.Serializable;

public class DiffOptions implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean caseSensitive = false;
    private boolean ignoreDefaultValues = false;
    private boolean ignoreIndices = false;

    public boolean isCaseSensitive() { return caseSensitive; }
    public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }

    public boolean isIgnoreDefaultValues() { return ignoreDefaultValues; }
    public void setIgnoreDefaultValues(boolean ignoreDefaultValues) { this.ignoreDefaultValues = ignoreDefaultValues; }

    public boolean isIgnoreIndices() { return ignoreIndices; }
    public void setIgnoreIndices(boolean ignoreIndices) { this.ignoreIndices = ignoreIndices; }
}
