package com.dawei;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @ClassName RestTemplateConfig
 * @Author dawei
 * @Version 1.0
 * @Description RestTemplateConfig
 **/
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
