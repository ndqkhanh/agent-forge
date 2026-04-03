package com.agentforge.common.model;

public enum Role {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static Role fromWire(String value) {
        return valueOf(value.toUpperCase());
    }
}
