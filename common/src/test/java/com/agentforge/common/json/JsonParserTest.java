package com.agentforge.common.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonParserTest {

    @Test
    void parseString_basic() {
        JsonValue result = JsonParser.parse("\"hello\"");
        assertThat(result).isInstanceOf(JsonValue.JsonString.class);
        assertThat(((JsonValue.JsonString) result).value()).isEqualTo("hello");
    }

    @Test
    void parseString_empty() {
        JsonValue result = JsonParser.parse("\"\"");
        assertThat(((JsonValue.JsonString) result).value()).isEmpty();
    }

    @Test
    void parseString_escapeNewline() {
        JsonValue result = JsonParser.parse("\"line1\\nline2\"");
        assertThat(((JsonValue.JsonString) result).value()).isEqualTo("line1\nline2");
    }

    @Test
    void parseString_escapeTab() {
        JsonValue result = JsonParser.parse("\"col1\\tcol2\"");
        assertThat(((JsonValue.JsonString) result).value()).isEqualTo("col1\tcol2");
    }

    @Test
    void parseString_escapeQuote() {
        JsonValue result = JsonParser.parse("\"say \\\"hi\\\"\"");
        assertThat(((JsonValue.JsonString) result).value()).isEqualTo("say \"hi\"");
    }

    @Test
    void parseString_escapeBackslash() {
        JsonValue result = JsonParser.parse("\"a\\\\b\"");
        assertThat(((JsonValue.JsonString) result).value()).isEqualTo("a\\b");
    }

    @Test
    void parseString_unicodeEscape() {
        JsonValue result = JsonParser.parse("\"\\u0041\"");
        assertThat(((JsonValue.JsonString) result).value()).isEqualTo("A");
    }

    @Test
    void parseNumber_integer() {
        JsonValue result = JsonParser.parse("42");
        assertThat(result).isInstanceOf(JsonValue.JsonNumber.class);
        assertThat(((JsonValue.JsonNumber) result).value()).isEqualTo(42.0);
    }

    @Test
    void parseNumber_negative() {
        JsonValue result = JsonParser.parse("-7");
        assertThat(((JsonValue.JsonNumber) result).value()).isEqualTo(-7.0);
    }

    @Test
    void parseNumber_float() {
        JsonValue result = JsonParser.parse("3.14");
        assertThat(((JsonValue.JsonNumber) result).value()).isCloseTo(3.14, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void parseNumber_scientific() {
        JsonValue result = JsonParser.parse("1e3");
        assertThat(((JsonValue.JsonNumber) result).value()).isEqualTo(1000.0);
    }

    @Test
    void parseTrue() {
        JsonValue result = JsonParser.parse("true");
        assertThat(result).isInstanceOf(JsonValue.JsonBool.class);
        assertThat(((JsonValue.JsonBool) result).value()).isTrue();
    }

    @Test
    void parseFalse() {
        JsonValue result = JsonParser.parse("false");
        assertThat(((JsonValue.JsonBool) result).value()).isFalse();
    }

    @Test
    void parseNull() {
        JsonValue result = JsonParser.parse("null");
        assertThat(result).isInstanceOf(JsonValue.JsonNull.class);
    }

    @Test
    void parseArray_empty() {
        JsonValue result = JsonParser.parse("[]");
        assertThat(result).isInstanceOf(JsonValue.JsonArray.class);
        assertThat(((JsonValue.JsonArray) result).elements()).isEmpty();
    }

    @Test
    void parseArray_withElements() {
        JsonValue result = JsonParser.parse("[1,2,3]");
        JsonValue.JsonArray arr = (JsonValue.JsonArray) result;
        assertThat(arr.elements()).hasSize(3);
        assertThat(((JsonValue.JsonNumber) arr.elements().get(0)).value()).isEqualTo(1.0);
    }

    @Test
    void parseArray_mixed() {
        JsonValue result = JsonParser.parse("[\"a\",1,true,null]");
        JsonValue.JsonArray arr = (JsonValue.JsonArray) result;
        assertThat(arr.elements()).hasSize(4);
        assertThat(arr.elements().get(0)).isInstanceOf(JsonValue.JsonString.class);
        assertThat(arr.elements().get(2)).isInstanceOf(JsonValue.JsonBool.class);
        assertThat(arr.elements().get(3)).isInstanceOf(JsonValue.JsonNull.class);
    }

    @Test
    void parseObject_empty() {
        JsonValue result = JsonParser.parse("{}");
        assertThat(result).isInstanceOf(JsonValue.JsonObject.class);
        assertThat(((JsonValue.JsonObject) result).fields()).isEmpty();
    }

    @Test
    void parseObject_singleField() {
        JsonValue result = JsonParser.parse("{\"name\":\"alice\"}");
        JsonValue.JsonObject obj = (JsonValue.JsonObject) result;
        assertThat(obj.getString("name")).contains("alice");
    }

    @Test
    void parseObject_multipleFields() {
        JsonValue result = JsonParser.parse("{\"a\":1,\"b\":true}");
        JsonValue.JsonObject obj = (JsonValue.JsonObject) result;
        assertThat(obj.getNumber("a")).contains(1.0);
        assertThat(obj.getBool("b")).contains(true);
    }

    @Test
    void parseNested_objectInArray() {
        JsonValue result = JsonParser.parse("[{\"x\":1},{\"x\":2}]");
        JsonValue.JsonArray arr = (JsonValue.JsonArray) result;
        assertThat(arr.elements()).hasSize(2);
        assertThat(((JsonValue.JsonObject) arr.elements().get(0)).getNumber("x")).contains(1.0);
    }

    @Test
    void parseNested_objectInObject() {
        JsonValue result = JsonParser.parse("{\"inner\":{\"val\":42}}");
        JsonValue.JsonObject obj = (JsonValue.JsonObject) result;
        assertThat(obj.getObject("inner")).isPresent();
        assertThat(obj.getObject("inner").get().getNumber("val")).contains(42.0);
    }

    @Test
    void parseWithWhitespace() {
        JsonValue result = JsonParser.parse("  {  \"a\"  :  1  }  ");
        assertThat(result).isInstanceOf(JsonValue.JsonObject.class);
    }

    @Test
    void parseError_unterminatedString() {
        assertThatThrownBy(() -> JsonParser.parse("\"unclosed"))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    void parseError_invalidEscape() {
        assertThatThrownBy(() -> JsonParser.parse("\"\\q\""))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    void parseError_trailingChars() {
        assertThatThrownBy(() -> JsonParser.parse("{}extra"))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    void parseError_nullInput() {
        assertThatThrownBy(() -> JsonParser.parse(null))
                .isInstanceOf(JsonParseException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"a\":}", "{ unclosed", "[1,2,", "[1 2]"})
    void parseError_malformed(String input) {
        assertThatThrownBy(() -> JsonParser.parse(input))
                .isInstanceOf(JsonParseException.class);
    }
}
