package com.dawei.staticpage;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticConsolePageTest {

    @Test
    void manualPushConsoleContainsCoreActions() throws Exception {
        String html = new String(new ClassPathResource("static/manual-push-console.html")
                .getInputStream()
                .readAllBytes(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(html.contains("手动推送测试台")),
                () -> assertTrue(html.contains("Manual Push Console")),
                () -> assertTrue(html.contains("stock-manual-push-console-language")),
                () -> assertTrue(html.contains("Back to Ops Dashboard")),
                () -> assertTrue(html.contains("盘中演示快讯")),
                () -> assertTrue(html.contains("Demo Intraday Opportunity Flash")),
                () -> assertTrue(html.contains("Demo Intraday Risk Flash")),
                () -> assertTrue(html.contains("A-Stock Post-Close Risk Digest")),
                () -> assertTrue(html.contains("url.searchParams.set('lang', state.language)")),
                () -> assertTrue(html.contains("/api/report-push/push/a/morning")),
                () -> assertTrue(html.contains("/api/report-push/push/a/evening")),
                () -> assertTrue(html.contains("/api/report-push/push/a/post-close-risk")),
                () -> assertTrue(html.contains("/api/report-push/push/a/demo/intraday-opportunity")),
                () -> assertTrue(html.contains("/api/report-push/push/a/demo/intraday-risk")),
                () -> assertTrue(html.contains("/api/report-push/push/all")),
                () -> assertTrue(html.contains("日报 / 晚报触发")),
                () -> assertTrue(html.contains("响应详情"))
        );
    }

    @Test
    void opsDashboardContainsConsoleEntryLink() throws Exception {
        String html = new String(new ClassPathResource("static/ops-dashboard.html")
                .getInputStream()
                .readAllBytes(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(html.contains("/manual-push-console.html")),
                () -> assertTrue(html.contains("打开手动测试台"))
        );
    }
}
