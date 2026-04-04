package com.dawei;

import com.dawei.mcp.tool.AStockTool;
import com.dawei.mcp.tool.AStockToolEnglish;
import com.dawei.mcp.tool.MacroThemeTool;
import com.dawei.mcp.tool.MacroThemeToolEnglish;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationToolRegistrationTest {

    @Test
    @DisplayName("注册方法应支持中英文工具对象与语言开关")
    void registMcpToolsShouldSupportLanguageSwitch() throws Exception {
        assertNotNull(Application.class.getDeclaredMethod(
                "registMCPTools",
                AStockTool.class,
                MacroThemeTool.class,
                AStockToolEnglish.class,
                MacroThemeToolEnglish.class,
                String.class
        ));
    }

    @Test
    @DisplayName("英文模式应注册英文工具描述")
    void registMcpToolsShouldExposeEnglishDescriptionsWhenLanguageIsEn() {
        Application application = new Application();

        ToolCallbackProvider provider = application.registMCPTools(
                new AStockTool(),
                new MacroThemeTool(),
                new AStockToolEnglish(),
                new MacroThemeToolEnglish(),
                "en"
        );

        Map<String, ToolDefinition> definitions = Arrays.stream(provider.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .collect(Collectors.toMap(ToolDefinition::name, definition -> definition));

        assertEquals(8, definitions.size());
        assertTrue(definitions.get("resolveAStock").description().contains("Use this tool first"));
        assertTrue(definitions.get("getMacroThemeBoard").description().contains("market direction"));
    }

    @Test
    @DisplayName("中文模式应保留中文工具描述")
    void registMcpToolsShouldKeepChineseDescriptionsWhenLanguageIsZh() {
        Application application = new Application();

        ToolCallbackProvider provider = application.registMCPTools(
                new AStockTool(),
                new MacroThemeTool(),
                new AStockToolEnglish(),
                new MacroThemeToolEnglish(),
                "zh"
        );

        Map<String, ToolDefinition> definitions = Arrays.stream(provider.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .collect(Collectors.toMap(ToolDefinition::name, definition -> definition));

        assertEquals(8, definitions.size());
        assertTrue(definitions.get("resolveAStock").description().contains("先用这个工具解析用户口中的A股标的"));
        assertTrue(definitions.get("getMacroThemeBoard").description().contains("当用户询问今天什么方向强"));
    }
}
