package com.dawei;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsDashboardStaticResourceTest {

    @Test
    void opsDashboardHtmlShouldExist() throws Exception {
        ClassPathResource resource = new ClassPathResource("static/ops-dashboard.html");

        assertTrue(resource.exists());
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("/api/ops-dashboard/summary"));
        assertTrue(content.contains("实时链路运营看板"));
        assertTrue(content.contains("快照链路"));
    }
}
