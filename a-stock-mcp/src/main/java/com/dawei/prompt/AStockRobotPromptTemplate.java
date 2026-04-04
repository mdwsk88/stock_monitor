package com.dawei.prompt;

import com.dawei.support.McpLanguageSupport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class AStockRobotPromptTemplate {

    private static final String ZH_TEMPLATE_PATH = "prompts/wecom-a-stock-robot-system-prompt.md";
    private static final String EN_TEMPLATE_PATH = "prompts/wecom-a-stock-robot-system-prompt-en.md";

    private final String systemPrompt;

    public AStockRobotPromptTemplate() {
        this(McpLanguageSupport.ZH);
    }

    @Autowired
    public AStockRobotPromptTemplate(@Value("${a.stock.mcp.language:zh}") String language) {
        this.systemPrompt = loadTemplate(resolveTemplatePath(language));
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    private static String resolveTemplatePath(String language) {
        return McpLanguageSupport.isEnglish(language) ? EN_TEMPLATE_PATH : ZH_TEMPLATE_PATH;
    }

    private String loadTemplate(String templatePath) {
        ClassPathResource resource = new ClassPathResource(templatePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载 A 股机器人系统提示词失败: " + templatePath, e);
        }
    }
}
