package com.dawei.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dawei.entity.WeComFeedback;
import com.dawei.entity.WeComSubscription;
import com.dawei.mapper.WeComFeedbackMapper;
import com.dawei.mapper.WeComSubscriptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AStockEngagementToolTest {

    @Mock
    private WeComSubscriptionMapper weComSubscriptionMapper;
    @Mock
    private WeComFeedbackMapper weComFeedbackMapper;

    private AStockEngagementTool tool;

    @BeforeEach
    void setUp() {
        tool = new AStockEngagementTool();
        ReflectionTestUtils.setField(tool, "weComSubscriptionMapper", weComSubscriptionMapper);
        ReflectionTestUtils.setField(tool, "weComFeedbackMapper", weComFeedbackMapper);
    }

    @Test
    @DisplayName("followAStockTarget should persist a group-level stock watch")
    void followAStockTargetShouldPersistStockWatch() {
        when(weComSubscriptionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        Map<String, Object> result = tool.followAStockTarget("STOCK", "宗申动力", "001696", "低空经济共振");

        assertEquals(true, result.get("success"));
        ArgumentCaptor<WeComSubscription> captor = ArgumentCaptor.forClass(WeComSubscription.class);
        verify(weComSubscriptionMapper).insert(captor.capture());
        assertEquals("STOCK", captor.getValue().getSubscriptionType());
        assertEquals("宗申动力", captor.getValue().getTargetName());
        assertEquals("001696", captor.getValue().getStockCode());
        assertEquals("wecom-mcp", captor.getValue().getSource());
    }

    @Test
    @DisplayName("recordAStockPushFeedback should persist lightweight feedback")
    void recordAStockPushFeedbackShouldPersistFeedback() {
        Map<String, Object> result = tool.recordAStockPushFeedback(
                "有用",
                "THEME",
                "低空经济",
                "",
                "低空经济",
                "这个方向继续跟"
        );

        assertEquals(true, result.get("success"));
        assertEquals("USEFUL", result.get("feedbackType"));
        ArgumentCaptor<WeComFeedback> captor = ArgumentCaptor.forClass(WeComFeedback.class);
        verify(weComFeedbackMapper).insert(captor.capture());
        assertEquals("USEFUL", captor.getValue().getFeedbackType());
        assertEquals("THEME", captor.getValue().getTargetType());
        assertEquals("wecom-mcp", captor.getValue().getSource());
    }

    @Test
    @DisplayName("listAStockSubscriptions should return current watches")
    void listAStockSubscriptionsShouldReturnCurrentWatches() {
        WeComSubscription subscription = new WeComSubscription();
        subscription.setTargetName("算力");
        when(weComSubscriptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(subscription));

        List<WeComSubscription> result = tool.listAStockSubscriptions(true, 10);

        assertEquals(1, result.size());
        assertEquals("算力", result.get(0).getTargetName());
        assertTrue(result.get(0).getTargetName().contains("算力"));
    }
}
