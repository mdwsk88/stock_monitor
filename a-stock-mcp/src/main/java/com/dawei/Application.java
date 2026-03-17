package com.dawei;

import com.dawei.common.utils.DotenvLoader;
import com.dawei.mcp.tool.AStockTool;
import com.dawei.mcp.tool.MacroThemeTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        DotenvLoader.loadToSystemProperties();
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public ToolCallbackProvider registMCPTools(AStockTool aStockTool, MacroThemeTool macroThemeTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(aStockTool, macroThemeTool)
                .build();
    }
}
