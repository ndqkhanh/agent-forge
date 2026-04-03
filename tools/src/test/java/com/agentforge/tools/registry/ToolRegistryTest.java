package com.agentforge.tools.registry;

import com.agentforge.common.model.ToolSchema;
import com.agentforge.tools.Tool;
import com.agentforge.tools.ToolExecutor.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToolRegistry")
class ToolRegistryTest {

    // --- Minimal stub tool ---

    static Tool stubTool(String name, String result) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "stub " + name; }
            @Override public String inputSchema() { return "{\"type\":\"object\",\"properties\":{}}"; }
            @Override public ToolResult execute(String inputJson) { return ToolResult.success(result); }
        };
    }

    static Tool throwingTool(String name) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "throws"; }
            @Override public String inputSchema() { return "{\"type\":\"object\",\"properties\":{}}"; }
            @Override public ToolResult execute(String inputJson) {
                throw new RuntimeException("boom");
            }
        };
    }

    // --- Builder / registration ---

    @Test
    @DisplayName("register single tool and retrieve via supports()")
    void register_singleTool_supports() {
        ToolRegistry registry = ToolRegistry.builder()
                .register(stubTool("echo", "hello"))
                .build();

        assertThat(registry.supports("echo")).isTrue();
    }

    @Test
    @DisplayName("supports() returns false for unknown tool")
    void supports_unknownTool_returnsFalse() {
        ToolRegistry registry = ToolRegistry.builder().build();

        assertThat(registry.supports("unknown")).isFalse();
    }

    @Test
    @DisplayName("register multiple tools via registerAll()")
    void registerAll_multipleTools() {
        ToolRegistry registry = ToolRegistry.builder()
                .registerAll(List.of(stubTool("a", "A"), stubTool("b", "B"), stubTool("c", "C")))
                .build();

        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.supports("a")).isTrue();
        assertThat(registry.supports("b")).isTrue();
        assertThat(registry.supports("c")).isTrue();
    }

    @Test
    @DisplayName("size() returns number of registered tools")
    void size_returnsCorrectCount() {
        ToolRegistry registry = ToolRegistry.builder()
                .register(stubTool("x", "X"))
                .register(stubTool("y", "Y"))
                .build();

        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("toolNames() returns all registered names")
    void toolNames_returnsAllNames() {
        ToolRegistry registry = ToolRegistry.builder()
                .register(stubTool("alpha", "a"))
                .register(stubTool("beta", "b"))
                .build();

        Set<String> names = registry.toolNames();
        assertThat(names).containsExactlyInAnyOrder("alpha", "beta");
    }

    // --- Execute ---

    @Test
    @DisplayName("execute() delegates to registered tool and returns success")
    void execute_registeredTool_returnsSuccess() {
        ToolRegistry registry = ToolRegistry.builder()
                .register(stubTool("greet", "Hello, World!"))
                .build();

        ToolResult result = registry.execute("greet", "{}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).isEqualTo("Hello, World!");
    }

    @Test
    @DisplayName("execute() returns error for unknown tool")
    void execute_unknownTool_returnsError() {
        ToolRegistry registry = ToolRegistry.builder().build();

        ToolResult result = registry.execute("no_such_tool", "{}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("Unknown tool", "no_such_tool");
    }

    @Test
    @DisplayName("execute() catches exceptions from tool and returns error")
    void execute_toolThrows_returnsError() {
        ToolRegistry registry = ToolRegistry.builder()
                .register(throwingTool("bomb"))
                .build();

        ToolResult result = registry.execute("bomb", "{}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("Tool execution failed");
    }

    // --- allSchemas ---

    @Test
    @DisplayName("allSchemas() returns one schema per registered tool")
    void allSchemas_returnsAllSchemas() {
        ToolRegistry registry = ToolRegistry.builder()
                .register(stubTool("t1", "r1"))
                .register(stubTool("t2", "r2"))
                .build();

        List<ToolSchema> schemas = registry.allSchemas();

        assertThat(schemas).hasSize(2);
        assertThat(schemas).extracting(ToolSchema::name)
                .containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    @DisplayName("allSchemas() is empty when no tools registered")
    void allSchemas_empty_whenNoTools() {
        ToolRegistry registry = ToolRegistry.builder().build();

        assertThat(registry.allSchemas()).isEmpty();
    }

    // --- Merge ---

    @Test
    @DisplayName("merge() combines tools from two registries")
    void merge_combinesTwoRegistries() {
        ToolRegistry r1 = ToolRegistry.builder().register(stubTool("a", "A")).build();
        ToolRegistry r2 = ToolRegistry.builder().register(stubTool("b", "B")).build();

        ToolRegistry merged = ToolRegistry.builder().merge(r1).merge(r2).build();

        assertThat(merged.size()).isEqualTo(2);
        assertThat(merged.supports("a")).isTrue();
        assertThat(merged.supports("b")).isTrue();
    }

    @Test
    @DisplayName("later registration overwrites same-name tool")
    void register_sameName_overwritesPrevious() {
        ToolRegistry registry = ToolRegistry.builder()
                .register(stubTool("tool", "first"))
                .register(stubTool("tool", "second"))
                .build();

        assertThat(registry.size()).isEqualTo(1);
        ToolResult result = registry.execute("tool", "{}");
        assertThat(result.output()).isEqualTo("second");
    }

    // --- Immutability ---

    @Test
    @DisplayName("registry is immutable — builder changes do not affect built registry")
    void immutability_builderChangesDoNotAffectRegistry() {
        ToolRegistry.Builder builder = ToolRegistry.builder().register(stubTool("orig", "original"));
        ToolRegistry registry = builder.build();

        // mutate builder after build
        builder.register(stubTool("added_later", "late"));

        assertThat(registry.supports("added_later")).isFalse();
        assertThat(registry.size()).isEqualTo(1);
    }

    // --- ToolResult factory methods ---

    @Test
    @DisplayName("ToolResult.success() sets isError false")
    void toolResult_success_isNotError() {
        ToolResult r = ToolResult.success("ok");
        assertThat(r.isError()).isFalse();
        assertThat(r.output()).isEqualTo("ok");
    }

    @Test
    @DisplayName("ToolResult.error() sets isError true")
    void toolResult_error_isError() {
        ToolResult r = ToolResult.error("something went wrong");
        assertThat(r.isError()).isTrue();
        assertThat(r.output()).isEqualTo("something went wrong");
    }
}
