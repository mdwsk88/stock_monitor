package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.MarketAlertFamily;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketState;
import com.dawei.service.AStockPushLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 市场情绪/宏观级告警协调器。
 */
@Service
public class MarketAlertCoordinatorService {

    private final AStockPushLogService aStockPushLogService;
    private final StockFilterConfig filterConfig;

    public MarketAlertCoordinatorService(AStockPushLogService aStockPushLogService,
                                         StockFilterConfig filterConfig) {
        this.aStockPushLogService = aStockPushLogService;
        this.filterConfig = filterConfig;
    }

    public AlertDispatchDecision evaluate(MarketAlertFamily family,
                                          AStockPushType pushType,
                                          String pushKey,
                                          MarketSnapshot snapshot,
                                          int signalScore,
                                          LocalDateTime now) {
        if (family == null || family == MarketAlertFamily.NEUTRAL || pushType == null || now == null) {
            return AlertDispatchDecision.suppress("无效家族告警");
        }

        if (isCounterTrendSuppressed(family, snapshot)) {
            return AlertDispatchDecision.suppress("命中多空互斥锁");
        }

        LocalDateTime cooldownStart = now.minusMinutes(filterConfig.getMarketAlertFamilyCooldownMinutes());
        AStockPushLog latestFamilyPush = aStockPushLogService.findLatestPush(family.pushTypes(), cooldownStart);
        if (latestFamilyPush == null) {
            return AlertDispatchDecision.allow("家族冷却窗口内无同向告警");
        }

        if (isUpgrade(latestFamilyPush, pushType, pushKey, signalScore, snapshot)) {
            return AlertDispatchDecision.allow("同家族级别升级，允许突破静默期");
        }

        return AlertDispatchDecision.suppress("命中家族级冷却期");
    }

    private boolean isCounterTrendSuppressed(MarketAlertFamily family, MarketSnapshot snapshot) {
        MarketAlertFamily marketFamily = MarketAlertFamily.fromMarketState(snapshot != null ? snapshot.getMarketState() : null);
        return marketFamily.conflictsWith(family) && !isEmergencyRiskOverride(family, snapshot);
    }

    private boolean isEmergencyRiskOverride(MarketAlertFamily family, MarketSnapshot snapshot) {
        if (family != MarketAlertFamily.BEARISH || snapshot == null) {
            return false;
        }
        if (snapshot.getWeakestIndexChangePct() <= filterConfig.getMarketPanicIndexDropThreshold()) {
            return true;
        }
        if (snapshot.getLimitDownCount() >= filterConfig.getMarketPanicLimitDownThreshold()) {
            return true;
        }
        return snapshot.hasBreadthData()
                && snapshot.getAdvanceDeclineRatio() <= filterConfig.getMarketPanicBreadthThreshold();
    }

    private boolean isUpgrade(AStockPushLog latestFamilyPush,
                              AStockPushType currentPushType,
                              String currentPushKey,
                              int signalScore,
                              MarketSnapshot snapshot) {
        if (currentPushType != AStockPushType.MARKET_PULSE_OPPORTUNITY) {
            return false;
        }
        if (currentPushKey == null || !currentPushKey.endsWith(MarketState.OVERHEAT.name())) {
            return false;
        }
        return rank(currentPushType, currentPushKey, signalScore, snapshot)
                > rank(latestFamilyPush);
    }

    private int rank(AStockPushLog pushLog) {
        if (pushLog == null || pushLog.getPushType() == null) {
            return 0;
        }
        AStockPushType pushType;
        try {
            pushType = AStockPushType.valueOf(pushLog.getPushType());
        } catch (IllegalArgumentException ex) {
            return 0;
        }
        return rank(pushType, pushLog.getPushKey(), safeInt(pushLog.getSignalScore()), null);
    }

    private int rank(AStockPushType pushType,
                     String pushKey,
                     int signalScore,
                     MarketSnapshot snapshot) {
        if (pushType == null) {
            return 0;
        }
        return switch (pushType) {
            case MARKET_PULSE_RISK -> 40;
            case MARKET_PULSE_OPPORTUNITY -> {
                MarketState state = snapshot != null ? snapshot.getMarketState() : null;
                boolean overheat = state == MarketState.OVERHEAT
                        || (pushKey != null && pushKey.endsWith(MarketState.OVERHEAT.name()));
                yield overheat ? 45 : 35;
            }
            case MACRO_REALTIME_RISK -> 20 + Math.min(15, Math.max(0, signalScore - 80) / 2);
            case MACRO_REALTIME_OPPORTUNITY -> 20 + Math.min(10, Math.max(0, signalScore - 85) / 2);
            default -> 0;
        };
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    public record AlertDispatchDecision(boolean allowed, String reason) {

        private static AlertDispatchDecision allow(String reason) {
            return new AlertDispatchDecision(true, reason);
        }

        private static AlertDispatchDecision suppress(String reason) {
            return new AlertDispatchDecision(false, reason);
        }
    }
}
