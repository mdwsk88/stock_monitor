package com.dawei.service.impl;

import com.dawei.entity.AStockRealtimeAlertCard;
import com.dawei.utils.PushLanguageService;
import com.dawei.utils.WeComApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AStockIntradayDemoPushServiceTest {

    @Mock
    private WeComApi weComApi;

    @Test
    @DisplayName("盘中机会演示会发送中文 mock 卡片到 A 股企业微信")
    void pushOpportunityDemoShouldSendChineseCard() {
        PushLanguageService pushLanguageService = new PushLanguageService("zh");
        AStockIntradayDemoPushService service = new AStockIntradayDemoPushService(weComApi, pushLanguageService);

        when(weComApi.formatAStockRealtimeAlert(any(AStockRealtimeAlertCard.class)))
                .thenAnswer(invocation -> ((AStockRealtimeAlertCard) invocation.getArgument(0)).getTitle());

        AStockIntradayDemoPushService.DemoPushResult result = service.pushOpportunityDemo();

        ArgumentCaptor<String> markdownCaptor = ArgumentCaptor.forClass(String.class);
        verify(weComApi).sendMarkdownMessage(markdownCaptor.capture(), org.mockito.ArgumentMatchers.eq(WeComApi.MarketType.A));
        assertTrue(markdownCaptor.getValue().contains("【演示数据】"));
        assertTrue(result.demoData());
        assertEquals("盘中机会快讯（演示）", result.title());
        assertTrue(result.card().getConclusion().contains("展示样例"));
    }

    @Test
    @DisplayName("盘中风险演示在英文模式下会生成英文可见内容")
    void pushRiskDemoShouldRespectEnglishLanguage() {
        PushLanguageService pushLanguageService = new PushLanguageService("zh");
        AStockIntradayDemoPushService service = new AStockIntradayDemoPushService(weComApi, pushLanguageService);

        when(weComApi.formatAStockRealtimeAlert(any(AStockRealtimeAlertCard.class)))
                .thenAnswer(invocation -> ((AStockRealtimeAlertCard) invocation.getArgument(0)).getTitle());

        AStockIntradayDemoPushService.DemoPushResult result = pushLanguageService.withLanguage("en", service::pushRiskDemo);

        ArgumentCaptor<String> markdownCaptor = ArgumentCaptor.forClass(String.class);
        verify(weComApi).sendMarkdownMessage(markdownCaptor.capture(), org.mockito.ArgumentMatchers.eq(WeComApi.MarketType.A));
        assertTrue(markdownCaptor.getValue().contains("[Demo Data]"));
        assertEquals("Demo Intraday Risk Flash", result.title());
        assertTrue(result.card().getConclusion().contains("showcase intraday risk alert"));
        assertEquals("Defensive", result.card().getMarketStateLabel());
    }
}
