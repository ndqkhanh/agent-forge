package com.agentforge.common.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-rolled recursive descent JSON parser.
 * Produces a JsonValue tree from a JSON string.
 */
public final class JsonParser {

    private final String input;
    private int pos;

    private JsonParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    public static JsonValue parse(String input) {
        if (input == null) {
            throw new JsonParseException("Input must not be null");
        }
        JsonParser parser = new JsonParser(input.trim());
        JsonValue value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < parser.input.length()) {
            throw new JsonParseException(
                    "Unexpected trailing characters at position " + parser.pos);
        }
        return value;
    }

    private JsonValue parseValue() {
        skipWhitespace();
        if (pos >= input.length()) {
            throw new JsonParseException("Unexpected end of input");
        }
        char c = input.charAt(pos);
        return switch (c) {
            case '"' -> parseString();
            case '{' -> parseObject();
            case '[' -> parseArray();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> {
                if (c == '-' || Character.isDigit(c)) {
                    yield parseNumber();
                }
                throw new JsonParseException("Unexpected character '" + c + "' at position " + pos);
            }
        };
    }

    private JsonValue.JsonString parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '"') {
                pos++;
                return new JsonValue.JsonString(sb.toString());
            }
            if (c == '\\') {
                pos++;
                if (pos >= input.length()) {
                    throw new JsonParseException("Unexpected end of input in string escape");
                }
                char esc = input.charAt(pos++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > input.length()) {
                            throw new JsonParseException("Incomplete unicode escape at position " + pos);
                        }
                        String hex = input.substring(pos, pos + 4);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw new JsonParseException("Invalid unicode escape: \\u" + hex);
                        }
                        pos += 4;
                    }
                    default -> throw new JsonParseException("Invalid escape sequence: \\" + esc);
                }
            } else {
                sb.append(c);
                pos++;
            }
        }
        throw new JsonParseException("Unterminated string");
    }

    private JsonValue.JsonNumber parseNumber() {
        int start = pos;
        if (pos < input.length() && input.charAt(pos) == '-') pos++;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        if (pos < input.length() && input.charAt(pos) == '.') {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        }
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        }
        String numStr = input.substring(start, pos);
        try {
            return new JsonValue.JsonNumber(Double.parseDouble(numStr));
        } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid number: " + numStr);
        }
    }

    private JsonValue.JsonBool parseBoolean() {
        if (input.startsWith("true", pos)) {
            pos += 4;
            return new JsonValue.JsonBool(true);
        }
        if (input.startsWith("false", pos)) {
            pos += 5;
            return new JsonValue.JsonBool(false);
        }
        throw new JsonParseException("Invalid boolean at position " + pos);
    }

    private JsonValue.JsonNull parseNull() {
        if (input.startsWith("null", pos)) {
            pos += 4;
            return new JsonValue.JsonNull();
        }
        throw new JsonParseException("Invalid null at position " + pos);
    }

    private JsonValue.JsonArray parseArray() {
        expect('[');
        List<JsonValue> elements = new ArrayList<>();
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == ']') {
            pos++;
            return new JsonValue.JsonArray(elements);
        }
        while (true) {
            elements.add(parseValue());
            skipWhitespace();
            if (pos >= input.length()) {
                throw new JsonParseException("Unterminated array");
            }
            char c = input.charAt(pos);
            if (c == ']') {
                pos++;
                return new JsonValue.JsonArray(elements);
            }
            if (c != ',') {
                throw new JsonParseException("Expected ',' or ']' in array at position " + pos);
            }
            pos++;
        }
    }

    private JsonValue.JsonObject parseObject() {
        expect('{');
        Map<String, JsonValue> fields = new LinkedHashMap<>();
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == '}') {
            pos++;
            return new JsonValue.JsonObject(fields);
        }
        while (true) {
            skipWhitespace();
            JsonValue.JsonString key = parseString();
            skipWhitespace();
            expect(':');
            JsonValue value = parseValue();
            fields.put(key.value(), value);
            skipWhitespace();
            if (pos >= input.length()) {
                throw new JsonParseException("Unterminated object");
            }
            char c = input.charAt(pos);
            if (c == '}') {
                pos++;
                return new JsonValue.JsonObject(fields);
            }
            if (c != ',') {
                throw new JsonParseException("Expected ',' or '}' in object at position " + pos);
            }
            pos++;
        }
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private void expect(char expected) {
        if (pos >= input.length()) {
            throw new JsonParseException("Expected '" + expected + "' but reached end of input");
        }
        char actual = input.charAt(pos);
        if (actual != expected) {
            throw new JsonParseException(
                    "Expected '" + expected + "' but found '" + actual + "' at position " + pos);
        }
        pos++;
    }
}
