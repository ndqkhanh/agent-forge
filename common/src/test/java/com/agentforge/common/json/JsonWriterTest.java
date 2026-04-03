package com.agentforge.common.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonWriterTest {

    @Test
    void writeString() {
        assertThat(JsonWriter.write(new JsonValue.JsonString("hello"))).isEqualTo("\"hello\"");
    }

    @Test
    void writeString_escapesQuote() {
        assertThat(JsonWriter.write(new JsonValue.JsonString("say \"hi\""))).isEqualTo("\"say \\\"hi\\\"\"");
    }

    @Test
    void writeString_escapesNewline() {
        assertThat(JsonWriter.write(new JsonValue.JsonString("a\nb"))).isEqualTo("\"a\\nb\"");
    }

    @Test
    void writeString_escapesBackslash() {
        assertThat(JsonWriter.write(new JsonValue.JsonString("a\\b"))).isEqualTo("\"a\\\\b\"");
    }

    @Test
    void writeInteger() {
        assertThat(JsonWriter.write(new JsonValue.JsonNumber(42))).isEqualTo("42");
    }

    @Test
    void writeFloat() {
        String result = JsonWriter.write(new JsonValue.JsonNumber(3.14));
        assertThat(result).isEqualTo("3.14");
    }

    @Test
    void writeTrue() {
        assertThat(JsonWriter.write(new JsonValue.JsonBool(true))).isEqualTo("true");
    }

    @Test
    void writeFalse() {
        assertThat(JsonWriter.write(new JsonValue.JsonBool(false))).isEqualTo("false");
    }

    @Test
    void writeNull() {
        assertThat(JsonWriter.write(new JsonValue.JsonNull())).isEqualTo("null");
    }

    @Test
    void writeEmptyArray() {
        assertThat(JsonWriter.write(new JsonValue.JsonArray(List.of()))).isEqualTo("[]");
    }

    @Test
    void writeArray_withElements() {
        JsonValue.JsonArray arr = new JsonValue.JsonArray(List.of(
                new JsonValue.JsonNumber(1),
                new JsonValue.JsonNumber(2)
        ));
        assertThat(JsonWriter.write(arr)).isEqualTo("[1,2]");
    }

    @Test
    void writeEmptyObject() {
        assertThat(JsonWriter.write(new JsonValue.JsonObject(Map.of()))).isEqualTo("{}");
    }

    @Test
    void writeObject_singleField() {
        JsonValue.JsonObject obj = new JsonValue.JsonObject(Map.of("key", new JsonValue.JsonString("val")));
        assertThat(JsonWriter.write(obj)).isEqualTo("{\"key\":\"val\"}");
    }

    @Test
    void roundTrip_parseAndWrite() {
        String original = "{\"name\":\"alice\",\"age\":30,\"active\":true}";
        JsonValue parsed = JsonParser.parse(original);
        String written = JsonWriter.write(parsed);
        JsonValue reparsed = JsonParser.parse(written);
        assertThat(reparsed).isEqualTo(parsed);
    }

    @Test
    void writePretty_emptyObject() {
        assertThat(JsonWriter.writePretty(new JsonValue.JsonObject(Map.of()))).isEqualTo("{}");
    }

    @Test
    void writePretty_emptyArray() {
        assertThat(JsonWriter.writePretty(new JsonValue.JsonArray(List.of()))).isEqualTo("[]");
    }

    @Test
    void writePretty_containsNewlines() {
        JsonValue.JsonObject obj = new JsonValue.JsonObject(Map.of("a", new JsonValue.JsonNumber(1)));
        String result = JsonWriter.writePretty(obj);
        assertThat(result).contains("\n");
    }

    @Test
    void writePretty_containsIndentation() {
        JsonValue.JsonObject obj = new JsonValue.JsonObject(Map.of("a", new JsonValue.JsonNumber(1)));
        String result = JsonWriter.writePretty(obj);
        assertThat(result).contains("  ");
    }

    @Test
    void writePretty_roundTrip() {
        String json = "{\"x\":1}";
        JsonValue parsed = JsonParser.parse(json);
        String pretty = JsonWriter.writePretty(parsed);
        JsonValue reparsed = JsonParser.parse(pretty);
        assertThat(reparsed).isEqualTo(parsed);
    }
}
