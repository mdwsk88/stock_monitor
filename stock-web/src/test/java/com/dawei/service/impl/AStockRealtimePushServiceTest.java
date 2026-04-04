package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushDecision;
import com.dawei.entity.AStockPushDecisionLog;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRealtimeAlertCard;
import com.dawei.entity.AStockRealtimeContext;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketState;
import com.dawei.service.AStockPushDecisionLogService;
import com.dawei.service.MarketStateService;
import com.dawei.service.AStockPushLogService;
import com.dawei.utils.PushLanguageService;
import com.dawei.utils.WeComApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AStockRealtimePushServiceTest {

    private static final Pattern HASH_PUSH_KEY_PATTERN = Pattern.compile("300121\\|重大合同\\|[a-f0-9]{32}");

    @Mock
    private AStockPushPolicyService aStockPushPolicyService;

    @Mock
    private AStockRealtimeContextService aStockRealtimeContextService;

    @Mock
    private AStockPushLogService aStockPushLogService;

    @Mock
    private AStockPushDecisionLogService aStockPushDecisionLogService;

    @Mock
    private MarketStateService marketStateService;

    @Mock
    private WeComApi weComApi;

    private AStockRealtimePushService pushService;

    @BeforeEach
    void setUp() {
        StockFilterConfig filterConfig = new StockFilterConfig();
        filterConfig.setARealtimePushCooldownMinutes(120);
        filterConfig.setARealtimeCriticalThreshold(92);
        pushService = new AStockRealtimePushService(
                aStockPushPolicyService,
                aStockRealtimeContextService,
                new AReportOpportunityInsightService(),
                aStockPushDecisionLogService,
                aStockPushLogService,
                marketStateService,
                weComApi,
                filterConfig
        );
    }

    @Test
    void handleSavedNotice_ShouldSkipWhenDecisionIsReportOnly() {
        AStockRss notice = buildNotice();
        when(marketStateService.getLatestSnapshot()).thenReturn(MarketSnapshot.neutral(null, "TEST"));
        when(aStockPushPolicyService.classify(any(AStockRss.class), any(), any(MarketSnapshot.class)))
                .thenReturn(new AStockPushDecision(AStockPushType.REPORT_ONLY, "仅进报告", false, false));

        boolean pushed = pushService.handleSavedNotice(notice);

        assertFalse(pushed);
        verify(weComApi, never()).sendMarkdownMessage(any(), any());
        verify(aStockPushLogService, never()).recordPush(any());
        verify(aStockPushDecisionLogService).recordDecision(any(AStockPushDecisionLog.class));
    }

    @Test
    void handleSavedNotice_ShouldSkipWhenCooldownExists() {
        AStockRss notice = buildNotice();
        when(marketStateService.getLatestSnapshot()).thenReturn(MarketSnapshot.neutral(null, "TEST"));
        when(aStockPushPolicyService.classify(any(AStockRss.class), any(), any(MarketSnapshot.class)))
                .thenReturn(new AStockPushDecision(AStockPushType.REALTIME_OPPORTUNITY, "盘中强催化", false, true));
        when(aStockPushPolicyService.refineRealtimeDecision(any(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.REALTIME_OPPORTUNITY), any()))
                .thenReturn(true);

        boolean pushed = pushService.handleSavedNotice(notice);

        assertFalse(pushed);
        verify(weComApi, never()).sendMarkdownMessage(any(), any());
        verify(aStockPushLogService, never()).recordPush(any());
        ArgumentCaptor<AStockPushDecisionLog> captor = ArgumentCaptor.forClass(AStockPushDecisionLog.class);
        verify(aStockPushDecisionLogService).recordDecision(captor.capture());
        assertTrue("SKIPPED".equals(captor.getValue().getSendStatus()));
        assertTrue(captor.getValue().getCooldownHit() == 1);
    }

    @Test
    void handleSavedNotice_ShouldSendStructuredAlertAndRecordPushOnSuccess() {
        AStockRss notice = buildNotice();
        AStockRealtimeContext context = new AStockRealtimeContext(
                "低空经济",
                "低空飞行基础设施建设提速",
                "政策扶持继续加码",
                90,
                146,
                "公告直接命中主线",
                "EXPLICIT"
        );
        MarketSnapshot snapshot = MarketSnapshot.neutral(null, "TEST");
        snapshot.setMarketState(MarketState.RISK_ON);
        when(marketStateService.getLatestSnapshot()).thenReturn(snapshot);
        when(aStockPushPolicyService.classify(any(AStockRss.class), any(), any(MarketSnapshot.class)))
                .thenReturn(new AStockPushDecision(AStockPushType.REALTIME_OPPORTUNITY, "高分利多白名单事件", false, true));
        when(aStockPushPolicyService.refineRealtimeDecision(any(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.REALTIME_OPPORTUNITY), any()))
                .thenReturn(false);
        when(aStockRealtimeContextService.buildContext(any(AStockRss.class), any())).thenReturn(context);
        when(weComApi.formatAStockRealtimeAlert(any(AStockRealtimeAlertCard.class))).thenReturn("formatted-alert");

        boolean pushed = pushService.handleSavedNotice(notice);

        assertTrue(pushed);
        ArgumentCaptor<AStockRealtimeAlertCard> cardCaptor = ArgumentCaptor.forClass(AStockRealtimeAlertCard.class);
        verify(weComApi).formatAStockRealtimeAlert(cardCaptor.capture());
        AStockRealtimeAlertCard card = cardCaptor.getValue();
        assertTrue("领军核心".equals(card.getPositionLabel()));
        assertTrue(card.getTradeHint().contains("主线锚点"));
        assertTrue("进攻态".equals(card.getMarketStateLabel()));
        verify(weComApi).sendMarkdownMessage("formatted-alert", WeComApi.MarketType.A);
        ArgumentCaptor<AStockPushLog> pushLogCaptor = ArgumentCaptor.forClass(AStockPushLog.class);
        verify(aStockPushLogService).recordPush(pushLogCaptor.capture());
        AStockPushLog pushLog = pushLogCaptor.getValue();
        assertTrue(pushLog.getPushKey().contains("cluster-contract"));
        assertTrue("低空经济".equals(pushLog.getMacroThemeName()));
        assertTrue(pushLog.getResonanceScore() >= 146);
        ArgumentCaptor<AStockPushDecisionLog> decisionCaptor = ArgumentCaptor.forClass(AStockPushDecisionLog.class);
        verify(aStockPushDecisionLogService).recordDecision(decisionCaptor.capture());
        assertTrue("SENT".equals(decisionCaptor.getValue().getSendStatus()));
        assertTrue("领军核心".equals(decisionCaptor.getValue().getPositionLabel()));
    }

    @Test
    void handleSavedNotice_ShouldNotRecordPushWhenSendFails() {
        AStockRss notice = buildNotice();
        when(marketStateService.getLatestSnapshot()).thenReturn(MarketSnapshot.neutral(null, "TEST"));
        when(aStockPushPolicyService.classify(any(AStockRss.class), any(), any(MarketSnapshot.class)))
                .thenReturn(new AStockPushDecision(AStockPushType.REALTIME_RISK, "高危利空", true, true));
        when(aStockPushPolicyService.refineRealtimeDecision(any(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.REALTIME_RISK), any()))
                .thenReturn(false);
        when(aStockRealtimeContextService.buildContext(any(AStockRss.class), any()))
                .thenReturn(AStockRealtimeContext.empty());
        when(weComApi.formatAStockRealtimeAlert(any(AStockRealtimeAlertCard.class))).thenReturn("risk-alert");
        doThrow(new RuntimeException("wecom down")).when(weComApi)
                .sendMarkdownMessage("risk-alert", WeComApi.MarketType.A);

        boolean pushed = pushService.handleSavedNotice(notice);

        assertFalse(pushed);
        verify(aStockPushLogService, never()).recordPush(any());
        ArgumentCaptor<AStockPushDecisionLog> captor = ArgumentCaptor.forClass(AStockPushDecisionLog.class);
        verify(aStockPushDecisionLogService).recordDecision(captor.capture());
        assertTrue("FAILED".equals(captor.getValue().getSendStatus()));
        assertTrue(captor.getValue().getFailureReason().contains("wecom down"));
    }

    @Test
    void handleSavedNotice_ShouldHashFallbackPushKeyWhenClusterKeyMissing() {
        AStockRss notice = buildNotice();
        notice.setClusterKey(null);
        when(marketStateService.getLatestSnapshot()).thenReturn(MarketSnapshot.neutral(null, "TEST"));
        when(aStockPushPolicyService.classify(any(AStockRss.class), any(), any(MarketSnapshot.class)))
                .thenReturn(new AStockPushDecision(AStockPushType.REALTIME_OPPORTUNITY, "盘中强催化", false, true));
        when(aStockPushPolicyService.refineRealtimeDecision(any(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.REALTIME_OPPORTUNITY), any()))
                .thenReturn(false);
        when(aStockRealtimeContextService.buildContext(any(AStockRss.class), any()))
                .thenReturn(AStockRealtimeContext.empty());
        when(weComApi.formatAStockRealtimeAlert(any(AStockRealtimeAlertCard.class))).thenReturn("formatted-alert");

        boolean pushed = pushService.handleSavedNotice(notice);

        assertTrue(pushed);
        ArgumentCaptor<AStockPushLog> pushLogCaptor = ArgumentCaptor.forClass(AStockPushLog.class);
        verify(aStockPushLogService).recordPush(pushLogCaptor.capture());
        assertTrue(HASH_PUSH_KEY_PATTERN.matcher(pushLogCaptor.getValue().getPushKey()).matches());
        verify(aStockPushDecisionLogService).recordDecision(any(AStockPushDecisionLog.class));
    }

    @Test
    void handleSavedNotice_ShouldSkipFollowerOpportunityAfterPolicyRefinement() {
        AStockRss notice = buildNotice();
        AStockRealtimeContext context = new AStockRealtimeContext(
                null,
                null,
                null,
                0,
                0,
                null,
                null
        );
        MarketSnapshot snapshot = MarketSnapshot.neutral(null, "TEST");
        snapshot.setMarketState(MarketState.OVERHEAT);
        when(marketStateService.getLatestSnapshot()).thenReturn(snapshot);
        when(aStockPushPolicyService.classify(any(AStockRss.class), any(), any(MarketSnapshot.class)))
                .thenReturn(new AStockPushDecision(AStockPushType.REALTIME_OPPORTUNITY, "高分利多白名单事件", false, true));
        when(aStockPushPolicyService.refineRealtimeDecision(any(), any(), any(), any(), any()))
                .thenReturn(new AStockPushDecision(AStockPushType.REPORT_ONLY, "高潮态仅放行领军核心", false, true));
        when(aStockRealtimeContextService.buildContext(any(AStockRss.class), any())).thenReturn(context);

        boolean pushed = pushService.handleSavedNotice(notice);

        assertFalse(pushed);
        verify(weComApi, never()).sendMarkdownMessage(any(), any());
        verify(aStockPushLogService, never()).recordPush(any());
        ArgumentCaptor<AStockPushDecisionLog> captor = ArgumentCaptor.forClass(AStockPushDecisionLog.class);
        verify(aStockPushDecisionLogService).recordDecision(captor.capture());
        assertTrue("SKIPPED".equals(captor.getValue().getSendStatus()));
        assertTrue(captor.getValue().getDecisionReason().contains("高潮态仅放行领军核心"));
    }

    @Test
    void handleSavedNotice_ShouldBuildEnglishRealtimeCardWhenLanguageSwitchOn() {
        StockFilterConfig filterConfig = new StockFilterConfig();
        filterConfig.setARealtimePushCooldownMinutes(120);
        filterConfig.setARealtimeCriticalThreshold(92);
        AStockRealtimePushService englishService = new AStockRealtimePushService(
                aStockPushPolicyService,
                aStockRealtimeContextService,
                new AReportOpportunityInsightService(),
                aStockPushDecisionLogService,
                aStockPushLogService,
                marketStateService,
                weComApi,
                filterConfig,
                new PushLanguageService("en")
        );
        AStockRss notice = buildNotice();
        AStockRealtimeContext context = new AStockRealtimeContext(
                "低空经济",
                "低空飞行基础设施建设提速",
                "政策扶持继续加码",
                90,
                146,
                "公告直接命中主线",
                "EXPLICIT"
        );
        MarketSnapshot snapshot = MarketSnapshot.neutral(null, "TEST");
        snapshot.setMarketState(MarketState.RISK_ON);
        when(marketStateService.getLatestSnapshot()).thenReturn(snapshot);
        when(aStockPushPolicyService.classify(any(AStockRss.class), any(), any(MarketSnapshot.class)))
                .thenReturn(new AStockPushDecision(AStockPushType.REALTIME_OPPORTUNITY, "高分利多白名单事件", false, true));
        when(aStockPushPolicyService.refineRealtimeDecision(any(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(aStockPushLogService.hasRecentPush(any(), eq(AStockPushType.REALTIME_OPPORTUNITY), any())).thenReturn(false);
        when(aStockRealtimeContextService.buildContext(any(AStockRss.class), any())).thenReturn(context);
        when(weComApi.formatAStockRealtimeAlert(any(AStockRealtimeAlertCard.class))).thenReturn("formatted-alert");

        boolean pushed = englishService.handleSavedNotice(notice);

        assertTrue(pushed);
        ArgumentCaptor<AStockRealtimeAlertCard> cardCaptor = ArgumentCaptor.forClass(AStockRealtimeAlertCard.class);
        verify(weComApi).formatAStockRealtimeAlert(cardCaptor.capture());
        AStockRealtimeAlertCard card = cardCaptor.getValue();
        assertTrue("Leading Core".equals(card.getPositionLabel()));
        assertTrue(card.getTradeHint().contains("main-theme anchor"));
        assertTrue("Risk-On".equals(card.getMarketStateLabel()));
        assertTrue("Major Contract".equals(card.getEventType()));
    }

    private AStockRss buildNotice() {
        AStockRss notice = new AStockRss();
        notice.setStockCode("300121");
        notice.setStockName("阳谷华泰");
        notice.setTitle("阳谷华泰:关于中标8亿元无人机复材订单的公告");
        notice.setEventType("重大合同");
        notice.setSignalSide("利多");
        notice.setSignalScore(91);
        notice.setClusterKey("cluster-contract");
        return notice;
    }
}
