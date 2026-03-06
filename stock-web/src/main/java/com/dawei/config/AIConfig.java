package com.dawei.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName AIConfig
 * @Author dawei
 * @Version 1.0
 * @Description Spring AI 配置类
 **/
@Configuration
public class AIConfig {

    /**
     * 配置 ChatClient
     * 注意：Spring AI 1.0.0-M6+ 版本使用 OpenAiChatModel 创建 ChatClient
     */
    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}