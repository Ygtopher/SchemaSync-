package com.dbtool.core.model;

import java.io.Serializable;

public class ConstraintMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String type;
    private String definition;

    public ConstraintMetadata() {}

    public ConstraintMetadata(String name, String type, String definition) {
        this.name = name;
        this.type = type;
        this.definition = definition;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public String getDefinition() { return definition; }
}
