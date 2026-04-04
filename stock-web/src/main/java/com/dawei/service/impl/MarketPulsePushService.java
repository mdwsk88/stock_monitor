package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.MarketAlertFamily;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.service.AStockPushLogService;
import com.dawei.service.MarketStateService;
import com.dawei.utils.PushLanguageService;
import com.dawei.utils.WeComApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final MarketAlertCoordinatorService marketAlertCoordinatorService;
    private final WeComApi weComApi;
    private final StockFilterConfig filterConfig;
    private final PushLanguageService pushLanguageService;

    public MarketPulsePushService(MarketStateService marketStateService,
                                  AStockPushLogService aStockPushLogService,
                                  MarketAlertCoordinatorService marketAlertCoordinatorService,
                                  WeComApi weComApi,
                                  StockFilterConfig filterConfig) {
        this(marketStateService, aStockPushLogService, marketAlertCoordinatorService, weComApi, filterConfig, new PushLanguageService());
    }

    @Autowired
    public MarketPulsePushService(MarketStateService marketStateService,
                                  AStockPushLogService aStockPushLogService,
                                  MarketAlertCoordinatorService marketAlertCoordinatorService,
                                  WeComApi weComApi,
                                  StockFilterConfig filterConfig,
                                  PushLanguageService pushLanguageService) {
        this.marketStateService = marketStateService;
        this.aStockPushLogService = aStockPushLogService;
        this.marketAlertCoordinatorService = marketAlertCoordinatorService;
        this.weComApi = weComApi;
        this.filterConfig = filterConfig;
        this.pushLanguageService = pushLanguageService;
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
        LocalDateTime stateConfirmedAt = snapshot.getStateConfirmedAt() != null
                ? snapshot.getStateConfirmedAt()
                : now.minusMinutes(filterConfig.getMarketPulseCooldownMinutes());
        if (aStockPushLogService.hasRecentPush(pushKey, pushType, stateConfirmedAt.minusSeconds(1))) {
            log.info("市场状态脉冲已在当前状态窗内推送过，state={}, pushKey={}, confirmedAt={}",
                    state, pushKey, stateConfirmedAt);
            return false;
        }

        MarketAlertCoordinatorService.AlertDispatchDecision dispatchDecision =
                marketAlertCoordinatorService.evaluate(
                        MarketAlertFamily.fromPushType(pushType),
                        pushType,
                        pushKey,
                        snapshot,
                        scoreOf(snapshot),
                        now
                );
        if (!dispatchDecision.allowed()) {
            log.info("市场状态脉冲被协调器抑制，state={}, pushKey={}, reason={}",
                    state, pushKey, dispatchDecision.reason());
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
                ? pushLanguageService.text("# 🚨 A股市场防守雷达", "# 🚨 A-Share Defensive Radar")
                : pushLanguageService.text("# 🔥 A股市场进攻雷达", "# 🔥 A-Share Risk-On Radar");
        String conclusion = switch (snapshot.getMarketState()) {
            case DEFENSIVE -> pushLanguageService.text("系统检测到市场进入【防守态】，优先控制回撤，谨慎接飞刀。", "The market has shifted into a defensive regime. Focus on drawdown control and avoid impulsive bottom fishing.");
            case OVERHEAT -> pushLanguageService.text("系统检测到市场进入【高潮态】，龙头仍强，但后排追高风险显著上升。", "The market is in an overheated regime. Leaders may still hold up, but chasing weaker laggards is becoming materially riskier.");
            case RISK_ON -> pushLanguageService.text("系统检测到市场进入【进攻态】，风险偏好抬升，可重点盯主线强催化。", "The market has entered a risk-on regime. Risk appetite is improving, so focus on high-conviction catalysts inside active themes.");
            default -> pushLanguageService.text("市场暂处中性。", "The market is currently neutral.");
        };
        String action = switch (snapshot.getMarketState()) {
            case DEFENSIVE -> pushLanguageService.text("优先关注硬风险公告、跌停扩散和弱势股补跌。", "Prioritize hard-risk notices, limit-down expansion, and secondary weakness in laggards.");
            case OVERHEAT -> pushLanguageService.text("只跟踪最强主线和高辨识度龙头，谨防情绪见顶后的兑现。", "Track only the strongest themes and the clearest leaders, and stay alert for profit-taking after sentiment peaks.");
            case RISK_ON -> pushLanguageService.text("利好公告阈值已动态下调，但仍需盯共振与板块扩散。", "Bullish notice thresholds have been lowered dynamically, but you still need confirmation from resonance and sector breadth.");
            default -> pushLanguageService.text("维持中性观察。", "Stay neutral and keep observing.");
        };

        return title + "\n\n"
                + "> **" + pushLanguageService.text("状态", "Regime") + "**：<font color=\"" + (defensive ? "warning" : "info") + "\">"
                + pushLanguageService.marketStateLabel(snapshot.getMarketState()) + "</font>\n"
                + "> **" + pushLanguageService.text("指数", "Indexes") + "**："
                + pushLanguageService.text("上证", "SSE") + " " + formatPct(snapshot.getShChangePct())
                + " | " + pushLanguageService.text("深成", "SZSE") + " " + formatPct(snapshot.getSzChangePct())
                + " | " + pushLanguageService.text("创业板", "ChiNext") + " " + formatPct(snapshot.getCybChangePct()) + "\n"
                + "> **" + pushLanguageService.text("市场宽度", "Breadth") + "**："
                + pushLanguageService.text("上涨 ", "Advancers ") + snapshot.getUpCount()
                + " | " + pushLanguageService.text("下跌 ", "Decliners ") + snapshot.getDownCount()
                + " | " + pushLanguageService.text("近似涨停 ", "Near Limit-Up ") + snapshot.getLimitUpCount()
                + " | " + pushLanguageService.text("近似跌停 ", "Near Limit-Down ") + snapshot.getLimitDownCount() + "\n"
                + "> **" + pushLanguageService.text("结论", "Conclusion") + "**：" + conclusion + "\n"
                + "> **" + pushLanguageService.text("执行提示", "Action") + "**：" + action + "\n"
                + "> **" + pushLanguageService.text("触发时间", "Triggered At") + "**：" + now.format(TIME_FORMATTER) + "\n\n"
                + "<font color=\"comment\">"
                + pushLanguageService.text("市场状态脉冲由指数与市场宽度联合判定，仅供盘中研究使用。", "This market pulse is derived from index moves and market breadth, and is intended for intraday research only.")
                + "</font>";
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

    private int scoreOf(MarketSnapshot snapshot) {
        if (snapshot == null || snapshot.getMarketState() == null) {
            return 0;
        }
        return switch (snapshot.getMarketState()) {
            case DEFENSIVE -> (int) Math.round(Math.abs(snapshot.getWeakestIndexChangePct()) * 20);
            case OVERHEAT -> 110;
            case RISK_ON -> (int) Math.round(Math.abs(snapshot.getStrongestIndexChangePct()) * 15);
            default -> 0;
        };
    }
}
