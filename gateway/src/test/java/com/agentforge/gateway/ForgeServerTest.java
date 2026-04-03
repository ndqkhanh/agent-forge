package com.agentforge.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ForgeServerTest {

    // -------------------------------------------------------------------------
    // extractMessageField — static utility
    // -------------------------------------------------------------------------

    @Test
    void extractMessageField_simpleJson_returnsMessage() {
        String json = "{\"message\":\"hello world\"}";
        assertThat(ForgeServer.extractMessageField(json)).isEqualTo("hello world");
    }

    @Test
    void extractMessageField_jsonWithOtherFields_returnsMessage() {
        String json = "{\"role\":\"user\",\"message\":\"tell me a joke\",\"ts\":123}";
        assertThat(ForgeServer.extractMessageField(json)).isEqualTo("tell me a joke");
    }

    @Test
    void extractMessageField_emptyMessage_returnsEmpty() {
        String json = "{\"message\":\"\"}";
        assertThat(ForgeServer.extractMessageField(json)).isEqualTo("");
    }

    @Test
    void extractMessageField_missingMessageKey_returnsNull() {
        String json = "{\"text\":\"hello\"}";
        assertThat(ForgeServer.extractMessageField(json)).isNull();
    }

    @Test
    void extractMessageField_nullInput_returnsNull() {
        assertThat(ForgeServer.extractMessageField(null)).isNull();
    }

    @Test
    void extractMessageField_blankInput_returnsNull() {
        assertThat(ForgeServer.extractMessageField("  ")).isNull();
    }

    @Test
    void extractMessageField_escapedQuotesInMessage_handledCorrectly() {
        String json = "{\"message\":\"say \\\"hello\\\"\"}";
        String result = ForgeServer.extractMessageField(json);
        assertThat(result).isEqualTo("say \"hello\"");
    }

    @Test
    void extractMessageField_newlineEscape_handledCorrectly() {
        String json = "{\"message\":\"line1\\nline2\"}";
        assertThat(ForgeServer.extractMessageField(json)).isEqualTo("line1\nline2");
    }

    @Test
    void extractMessageField_tabEscape_handledCorrectly() {
        String json = "{\"message\":\"col1\\tcol2\"}";
        assertThat(ForgeServer.extractMessageField(json)).isEqualTo("col1\tcol2");
    }

    // -------------------------------------------------------------------------
    // escapeJson — static utility
    // -------------------------------------------------------------------------

    @Test
    void escapeJson_plain_unchanged() {
        assertThat(ForgeServer.escapeJson("hello")).isEqualTo("hello");
    }

    @Test
    void escapeJson_doubleQuote_escaped() {
        assertThat(ForgeServer.escapeJson("say \"hi\"")).isEqualTo("say \\\"hi\\\"");
    }

    @Test
    void escapeJson_backslash_escaped() {
        assertThat(ForgeServer.escapeJson("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void escapeJson_newline_escaped() {
        assertThat(ForgeServer.escapeJson("a\nb")).isEqualTo("a\\nb");
    }

    @Test
    void escapeJson_null_returnsEmpty() {
        assertThat(ForgeServer.escapeJson(null)).isEmpty();
    }

    @Test
    void escapeJson_carriageReturn_escaped() {
        assertThat(ForgeServer.escapeJson("a\rb")).isEqualTo("a\\rb");
    }

    @Test
    void escapeJson_tab_escaped() {
        assertThat(ForgeServer.escapeJson("a\tb")).isEqualTo("a\\tb");
    }

    // -------------------------------------------------------------------------
    // Route path parsing logic (tested via extractMessageField edge cases)
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"message\":\"hello\"}",
        "{ \"message\" : \"world\" }",
        "{\"x\":1,\"message\":\"test\"}"
    })
    void extractMessageField_variousFormats_alwaysExtracts(String json) {
        assertThat(ForgeServer.extractMessageField(json)).isNotNull();
    }
}
