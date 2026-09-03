package com.dbtool.core.dialect;

public enum DialectType {
    POSTGRESQL("PostgreSQL"),
    H2("H2"),
    ACCESS("MS Access"),
    GENERIC("Generic");

    private final String label;
    DialectType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
