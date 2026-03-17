package com.dawei;

import com.dawei.mcp.tool.AStockTool;
import com.dawei.mcp.tool.MacroThemeTool;
import com.dawei.mcp.tool.WebFetchTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationToolRegistrationTest {

    @Test
    void registMcpToolsShouldUseMacroThemeToolInsteadOfWebFetchTool() throws Exception {
        assertNotNull(Application.class.getDeclaredMethod(
                "registMCPTools",
                AStockTool.class,
                MacroThemeTool.class
        ));

        assertThrows(NoSuchMethodException.class, () ->
                Application.class.getDeclaredMethod(
                        "registMCPTools",
                        AStockTool.class,
                        WebFetchTool.class
                ));
    }
}
