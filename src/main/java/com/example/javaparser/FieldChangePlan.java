package com.example.javaparser;

import java.util.List;

public final class FieldChangePlan {
    private final String fromModifier;
    private final List<String> fieldNames;

    public FieldChangePlan(String fromModifier, List<String> fieldNames) {
        this.fromModifier = fromModifier;
        this.fieldNames = List.copyOf(fieldNames);
    }

    public String getFromModifier() {
        return fromModifier;
    }

    public List<String> getFieldNames() {
        return fieldNames;
    }

    public String describe() {
        return fromModifier + " -> public: " + String.join(", ", fieldNames);
    }
}
