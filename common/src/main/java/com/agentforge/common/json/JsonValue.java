package com.agentforge.common.json;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public sealed interface JsonValue
        permits JsonValue.JsonString, JsonValue.JsonNumber, JsonValue.JsonBool,
                JsonValue.JsonNull, JsonValue.JsonArray, JsonValue.JsonObject {

    record JsonString(String value) implements JsonValue {}

    record JsonNumber(double value) implements JsonValue {}

    record JsonBool(boolean value) implements JsonValue {}

    record JsonNull() implements JsonValue {}

    record JsonArray(List<JsonValue> elements) implements JsonValue {
        public JsonArray {
            elements = List.copyOf(elements);
        }
    }

    record JsonObject(Map<String, JsonValue> fields) implements JsonValue {
        public JsonObject {
            fields = Map.copyOf(fields);
        }

        public Optional<String> getString(String key) {
            JsonValue v = fields.get(key);
            if (v instanceof JsonString s) return Optional.of(s.value());
            return Optional.empty();
        }

        public Optional<Double> getNumber(String key) {
            JsonValue v = fields.get(key);
            if (v instanceof JsonNumber n) return Optional.of(n.value());
            return Optional.empty();
        }

        public Optional<Boolean> getBool(String key) {
            JsonValue v = fields.get(key);
            if (v instanceof JsonBool b) return Optional.of(b.value());
            return Optional.empty();
        }

        public Optional<JsonArray> getArray(String key) {
            JsonValue v = fields.get(key);
            if (v instanceof JsonArray a) return Optional.of(a);
            return Optional.empty();
        }

        public Optional<JsonObject> getObject(String key) {
            JsonValue v = fields.get(key);
            if (v instanceof JsonObject o) return Optional.of(o);
            return Optional.empty();
        }
    }
}
