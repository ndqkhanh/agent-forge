package com.agentforge.common.json;

import java.util.Map;

/**
 * Serializes a JsonValue tree to a JSON string.
 */
public final class JsonWriter {

    private JsonWriter() {}

    public static String write(JsonValue value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    public static String writePretty(JsonValue value) {
        StringBuilder sb = new StringBuilder();
        writeValuePretty(value, sb, 0);
        return sb.toString();
    }

    private static void writeValue(JsonValue value, StringBuilder sb) {
        switch (value) {
            case JsonValue.JsonString s -> {
                sb.append('"');
                appendEscaped(s.value(), sb);
                sb.append('"');
            }
            case JsonValue.JsonNumber n -> {
                double d = n.value();
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                    sb.append((long) d);
                } else {
                    sb.append(d);
                }
            }
            case JsonValue.JsonBool b -> sb.append(b.value());
            case JsonValue.JsonNull ignored -> sb.append("null");
            case JsonValue.JsonArray a -> {
                sb.append('[');
                boolean first = true;
                for (JsonValue elem : a.elements()) {
                    if (!first) sb.append(',');
                    writeValue(elem, sb);
                    first = false;
                }
                sb.append(']');
            }
            case JsonValue.JsonObject o -> {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, JsonValue> entry : o.fields().entrySet()) {
                    if (!first) sb.append(',');
                    sb.append('"');
                    appendEscaped(entry.getKey(), sb);
                    sb.append("\":");
                    writeValue(entry.getValue(), sb);
                    first = false;
                }
                sb.append('}');
            }
        }
    }

    private static void writeValuePretty(JsonValue value, StringBuilder sb, int indent) {
        switch (value) {
            case JsonValue.JsonString s -> {
                sb.append('"');
                appendEscaped(s.value(), sb);
                sb.append('"');
            }
            case JsonValue.JsonNumber n -> {
                double d = n.value();
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                    sb.append((long) d);
                } else {
                    sb.append(d);
                }
            }
            case JsonValue.JsonBool b -> sb.append(b.value());
            case JsonValue.JsonNull ignored -> sb.append("null");
            case JsonValue.JsonArray a -> {
                if (a.elements().isEmpty()) {
                    sb.append("[]");
                    return;
                }
                sb.append("[\n");
                boolean first = true;
                for (JsonValue elem : a.elements()) {
                    if (!first) sb.append(",\n");
                    appendIndent(sb, indent + 1);
                    writeValuePretty(elem, sb, indent + 1);
                    first = false;
                }
                sb.append('\n');
                appendIndent(sb, indent);
                sb.append(']');
            }
            case JsonValue.JsonObject o -> {
                if (o.fields().isEmpty()) {
                    sb.append("{}");
                    return;
                }
                sb.append("{\n");
                boolean first = true;
                for (Map.Entry<String, JsonValue> entry : o.fields().entrySet()) {
                    if (!first) sb.append(",\n");
                    appendIndent(sb, indent + 1);
                    sb.append('"');
                    appendEscaped(entry.getKey(), sb);
                    sb.append("\": ");
                    writeValuePretty(entry.getValue(), sb, indent + 1);
                    first = false;
                }
                sb.append('\n');
                appendIndent(sb, indent);
                sb.append('}');
            }
        }
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent));
    }

    private static void appendEscaped(String s, StringBuilder sb) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
    }
}
