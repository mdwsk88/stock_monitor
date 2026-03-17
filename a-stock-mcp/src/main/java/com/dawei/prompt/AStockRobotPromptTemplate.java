package com.dawei.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class AStockRobotPromptTemplate {

    private static final String TEMPLATE_PATH = "prompts/wecom-a-stock-robot-system-prompt.md";

    private final String systemPrompt;

    public AStockRobotPromptTemplate() {
        this.systemPrompt = loadTemplate();
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    private String loadTemplate() {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载 A 股机器人系统提示词失败: " + TEMPLATE_PATH, e);
        }
    }
}
