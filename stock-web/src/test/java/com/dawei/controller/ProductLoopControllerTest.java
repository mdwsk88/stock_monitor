package com.dawei.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.WeComFeedback;
import com.dawei.entity.WeComSubscription;
import com.dawei.mapper.AStockPushLogMapper;
import com.dawei.mapper.WeComFeedbackMapper;
import com.dawei.mapper.WeComSubscriptionMapper;
import com.dawei.utils.WeComApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLoopControllerTest {

    @Mock
    private WeComFeedbackMapper weComFeedbackMapper;
    @Mock
    private WeComSubscriptionMapper weComSubscriptionMapper;
    @Mock
    private AStockPushLogMapper aStockPushLogMapper;
    @Mock
    private WeComApi weComApi;

    private ProductLoopController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductLoopController(
                weComFeedbackMapper,
                weComSubscriptionMapper,
                aStockPushLogMapper,
                weComApi
        );
    }

    @Test
    @DisplayName("summary should expose feedback stats, subscriptions, and recent pushes")
    void summaryShouldExposeProductLoopData() {
        WeComFeedback feedback = new WeComFeedback();
        feedback.setFeedbackType("USEFUL");
        WeComSubscription subscription = new WeComSubscription();
        subscription.setTargetName("低空经济");
        AStockPushLog pushLog = new AStockPushLog();
        pushLog.setTitle("盘中风口瞬时共振");

        when(weComFeedbackMapper.selectList(any(Wrapper.class))).thenReturn(List.of(feedback));
        when(weComSubscriptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(subscription));
        when(aStockPushLogMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pushLog));

        Map<String, Object> result = controller.summary();

        assertEquals(true, result.get("success"));
        assertNotNull(result.get("feedbackStats"));
        assertEquals(List.of(subscription), result.get("subscriptions"));
        assertEquals(List.of(pushLog), result.get("recentPushes"));
    }

    @Test
    @DisplayName("recordFeedback should normalize Chinese feedback and persist it")
    void recordFeedbackShouldNormalizeAndPersist() {
        ProductLoopController.FeedbackRequest request = new ProductLoopController.FeedbackRequest();
        request.setFeedbackType("太吵");
        request.setTargetType("推送");
        request.setTargetName("盘中风口");
        request.setComment("频率可以低一点");

        Map<String, Object> result = controller.recordFeedback(request);

        assertEquals(true, result.get("success"));
        ArgumentCaptor<WeComFeedback> captor = ArgumentCaptor.forClass(WeComFeedback.class);
        verify(weComFeedbackMapper).insert(captor.capture());
        assertEquals("NOISY", captor.getValue().getFeedbackType());
        assertEquals("PUSH", captor.getValue().getTargetType());
        assertEquals("product-prototype", captor.getValue().getSource());
    }

    @Test
    @DisplayName("upsertSubscription should create a stock subscription")
    void upsertSubscriptionShouldCreateStockSubscription() {
        when(weComSubscriptionMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        ProductLoopController.SubscriptionRequest request = new ProductLoopController.SubscriptionRequest();
        request.setTargetName("宗申动力");
        request.setStockCode("001696");
        request.setReason("盘中共振命中");

        Map<String, Object> result = controller.upsertSubscription(request);

        assertEquals(true, result.get("success"));
        ArgumentCaptor<WeComSubscription> captor = ArgumentCaptor.forClass(WeComSubscription.class);
        verify(weComSubscriptionMapper).insert(captor.capture());
        assertEquals("STOCK", captor.getValue().getSubscriptionType());
        assertEquals("宗申动力", captor.getValue().getTargetName());
        assertEquals(1, captor.getValue().getEnabled());
    }

    @Test
    @DisplayName("demo message should include engagement follow-up prompts")
    void demoMessageShouldIncludeEngagementTail() {
        Map<String, Object> result = controller.demoMessage("intraday");

        assertEquals(true, result.get("success"));
        String markdown = (String) result.get("markdown");
        assertTrue(markdown.contains("盘中风口瞬时共振"));
        assertTrue(markdown.contains("继续追问"));
        assertTrue(markdown.contains("@A股分析专家"));
    }

    @Test
    @DisplayName("demo push should send A-share markdown to WeCom")
    void demoPushShouldSendWeComMarkdown() {
        Map<String, Object> result = controller.demoPush("risk");

        assertEquals(true, result.get("success"));
        verify(weComApi).sendMarkdownMessage(any(String.class), org.mockito.Mockito.eq(WeComApi.MarketType.A));
    }
}
