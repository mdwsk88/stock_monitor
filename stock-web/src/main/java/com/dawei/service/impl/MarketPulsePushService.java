package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.service.AStockPushLogService;
import com.dawei.service.MarketStateService;
import com.dawei.utils.AStockEngagementMarkdown;
import com.dawei.utils.WeComApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 市场状态脉冲推送。
 */
@Slf4j
@Service
public class MarketPulsePushService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MarketStateService marketStateService;
    private final AStockPushLogService aStockPushLogService;
    private final WeComApi weComApi;
    private final StockFilterConfig filterConfig;

    public MarketPulsePushService(MarketStateService marketStateService,
                                  AStockPushLogService aStockPushLogService,
                                  WeComApi weComApi,
                                  StockFilterConfig filterConfig) {
        this.marketStateService = marketStateService;
        this.aStockPushLogService = aStockPushLogService;
        this.weComApi = weComApi;
        this.filterConfig = filterConfig;
    }

    public boolean refreshAndPushIfNeeded() {
        return pushIfNeeded(marketStateService.refreshSnapshot());
    }

    boolean pushIfNeeded(MarketSnapshot snapshot) {
        if (!shouldPush(snapshot)) {
            return false;
        }
        MarketState state = snapshot.getMarketState();
        AStockPushType pushType = state == MarketState.DEFENSIVE
                ? AStockPushType.MARKET_PULSE_RISK
                : AStockPushType.MARKET_PULSE_OPPORTUNITY;
        String pushKey = "market-pulse|" + state.name();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cooldownStart = now.minusMinutes(filterConfig.getMarketPulseCooldownMinutes());
        if (aStockPushLogService.hasRecentPush(pushKey, pushType, cooldownStart)) {
            log.info("市场状态脉冲命中冷却期，state={}, pushKey={}", state, pushKey);
            return false;
        }

        String markdown = buildMarkdown(snapshot, now);
        try {
            weComApi.sendMarkdownMessage(markdown, WeComApi.MarketType.A);
            aStockPushLogService.recordPush(buildPushLog(pushKey, pushType, snapshot, now));
            return true;
        } catch (Exception ex) {
            log.error("市场状态脉冲推送失败，state={}, reason={}", state, ex.getMessage(), ex);
            return false;
        }
    }

    private boolean shouldPush(MarketSnapshot snapshot) {
        if (snapshot == null || snapshot.getMarketState() == null) {
            return false;
        }
        if (snapshot.getSnapshotHealth() != null && snapshot.getSnapshotHealth() != MarketSnapshotHealth.LIVE) {
            log.info("市场状态脉冲跳过，原因=快照非实时态，health={}, failureCount={}",
                    snapshot.getSnapshotHealth(), snapshot.getConsecutiveFailureCount());
            return false;
        }
        return snapshot.getMarketState() == MarketState.DEFENSIVE
                || snapshot.getMarketState() == MarketState.RISK_ON
                || snapshot.getMarketState() == MarketState.OVERHEAT;
    }

    private String buildMarkdown(MarketSnapshot snapshot, LocalDateTime now) {
        boolean defensive = snapshot.getMarketState() == MarketState.DEFENSIVE;
        String title = defensive
                ? "# 🚨 A股市场防守雷达"
                : "# 🔥 A股市场进攻雷达";
        String conclusion = switch (snapshot.getMarketState()) {
            case DEFENSIVE -> "系统检测到市场进入【防守态】，优先控制回撤，谨慎接飞刀。";
            case OVERHEAT -> "系统检测到市场进入【高潮态】，龙头仍强，但后排追高风险显著上升。";
            case RISK_ON -> "系统检测到市场进入【进攻态】，风险偏好抬升，可重点盯主线强催化。";
            default -> "市场暂处中性。";
        };
        String action = switch (snapshot.getMarketState()) {
            case DEFENSIVE -> "优先关注硬风险公告、跌停扩散和弱势股补跌。";
            case OVERHEAT -> "只跟踪最强主线和高辨识度龙头，谨防情绪见顶后的兑现。";
            case RISK_ON -> "利好公告阈值已动态下调，但仍需盯共振与板块扩散。";
            default -> "维持中性观察。";
        };

        String markdown = title + "\n\n"
                + "> **状态**：<font color=\"" + (defensive ? "warning" : "info") + "\">"
                + snapshot.getMarketState().getLabel() + "</font>\n"
                + "> **指数**：上证 " + formatPct(snapshot.getShChangePct())
                + " | 深成 " + formatPct(snapshot.getSzChangePct())
                + " | 创业板 " + formatPct(snapshot.getCybChangePct()) + "\n"
                + "> **市场宽度**：上涨 " + snapshot.getUpCount()
                + " | 下跌 " + snapshot.getDownCount()
                + " | 近似涨停 " + snapshot.getLimitUpCount()
                + " | 近似跌停 " + snapshot.getLimitDownCount() + "\n"
                + "> **结论**：" + conclusion + "\n"
                + "> **执行提示**：" + action + "\n"
                + "> **触发时间**：" + now.format(TIME_FORMATTER) + "\n\n"
                + "<font color=\"comment\">市场状态脉冲由指数与市场宽度联合判定，仅供盘中研究使用。</font>";
        return AStockEngagementMarkdown.appendRealtimeTail(markdown, snapshot.getMarketState().getLabel());
    }

    private AStockPushLog buildPushLog(String pushKey,
                                       AStockPushType pushType,
                                       MarketSnapshot snapshot,
                                       LocalDateTime now) {
        AStockPushLog pushLog = new AStockPushLog();
        pushLog.setId(UUID.randomUUID().toString().replace("-", ""));
        pushLog.setPushKey(pushKey);
        pushLog.setPushType(pushType.name());
        pushLog.setSignalSide(snapshot.getMarketState() == MarketState.DEFENSIVE ? "利空" : "利多");
        double intensity = snapshot.getMarketState() == MarketState.DEFENSIVE
                ? Math.abs(snapshot.getWeakestIndexChangePct())
                : Math.abs(snapshot.getStrongestIndexChangePct());
        pushLog.setSignalScore((int) Math.round(intensity * 10));
        pushLog.setEventType(snapshot.getMarketState().getLabel());
        pushLog.setTitle("市场状态切换至" + snapshot.getMarketState().getLabel());
        pushLog.setDecisionReason("市场状态脉冲");
        pushLog.setPushedAt(now);
        pushLog.setCreateTime(now);
        return pushLog;
    }

    private String formatPct(double value) {
        return String.format("%+.2f%%", value);
    }
}
