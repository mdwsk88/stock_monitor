package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockPushDecision;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRealtimeAlertCard;
import com.dawei.entity.AStockRealtimeContext;
import com.dawei.entity.AStockRss;
import com.dawei.service.AStockPushLogService;
import com.dawei.utils.WeComApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * A股盘中实时预警服务
 */
@Service
@Slf4j
public class AStockRealtimePushService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AStockPushPolicyService aStockPushPolicyService;
    private final AStockRealtimeContextService aStockRealtimeContextService;
    private final AStockPushLogService aStockPushLogService;
    private final WeComApi weComApi;
    private final StockFilterConfig filterConfig;

    public AStockRealtimePushService(AStockPushPolicyService aStockPushPolicyService,
                                     AStockRealtimeContextService aStockRealtimeContextService,
                                     AStockPushLogService aStockPushLogService,
                                     WeComApi weComApi,
                                     StockFilterConfig filterConfig) {
        this.aStockPushPolicyService = aStockPushPolicyService;
        this.aStockRealtimeContextService = aStockRealtimeContextService;
        this.aStockPushLogService = aStockPushLogService;
        this.weComApi = weComApi;
        this.filterConfig = filterConfig;
    }

    public boolean handleSavedNotice(AStockRss notice) {
        LocalDateTime now = LocalDateTime.now();
        AStockPushDecision decision = aStockPushPolicyService.classify(notice, now);
        if (!decision.shouldSendRealtime()) {
            log.debug("A股公告不进入实时推送：{} {}，原因={}",
                    notice != null ? notice.getStockCode() : "unknown",
                    notice != null ? notice.getTitle() : "",
                    decision.getReason());
            return false;
        }

        String pushKey = buildPushKey(notice);
        LocalDateTime cooldownStart = now.minusMinutes(filterConfig.getARealtimePushCooldownMinutes());
        if (aStockPushLogService.hasRecentPush(pushKey, decision.getPushType(), cooldownStart)) {
            log.info("A股实时预警命中冷却期，跳过推送：stock={}, pushKey={}, type={}",
                    notice.getStockCode(), pushKey, decision.getPushType());
            return false;
        }

        AStockRealtimeContext context = aStockRealtimeContextService.buildContext(notice, now);
        AStockRealtimeAlertCard card = buildAlertCard(notice, decision, context, now);
        try {
            weComApi.sendMarkdownMessage(weComApi.formatAStockRealtimeAlert(card), WeComApi.MarketType.A);
            aStockPushLogService.recordPush(buildPushLog(pushKey, notice, decision, context, now));
            log.info("A股实时预警推送成功：stock={}, type={}, score={}, theme={}",
                    notice.getStockCode(), decision.getPushType(), notice.getSignalScore(), context.getThemeName());
            return true;
        } catch (Exception ex) {
            log.error("A股实时预警推送失败：stock={}, title={}, reason={}",
                    notice.getStockCode(), notice.getTitle(), ex.getMessage(), ex);
            return false;
        }
    }

    private AStockRealtimeAlertCard buildAlertCard(AStockRss notice,
                                                   AStockPushDecision decision,
                                                   AStockRealtimeContext context,
                                                   LocalDateTime now) {
        AStockRealtimeAlertCard card = new AStockRealtimeAlertCard();
        card.setStockCode(notice.getStockCode());
        card.setStockName(notice.getStockName());
        card.setPushType(decision.getPushType());
        card.setSeverityLabel(resolveSeverityLabel(safeInt(notice.getSignalScore()), decision.getPushType()));
        card.setSignalSide(notice.getSignalSide());
        card.setSignalScore(notice.getSignalScore());
        card.setEventType(notice.getEventType());
        card.setTitle(notice.getTitle());
        card.setConclusion(buildConclusion(notice, decision, context));
        card.setReasoning(buildReasoning(notice, context));
        card.setRiskHint(buildRiskHint(notice, decision, now));
        card.setMacroThemeName(context.getThemeName());
        card.setMacroTitle(context.getMacroTitle());
        card.setMacroSignalScore(context.getMacroSignalScore());
        card.setResonanceScore(context.getResonanceScore());
        card.setRelationReason(context.getRelationReason());
        return card;
    }

    private String buildConclusion(AStockRss notice, AStockPushDecision decision, AStockRealtimeContext context) {
        String stock = notice.getStockName() + "(" + notice.getStockCode() + ")";
        String base = switch (decision.getPushType()) {
            case REALTIME_OPPORTUNITY -> stock + " 刚披露高价值利多公告，属于盘中可跟踪的强催化。";
            case REALTIME_RISK -> stock + " 刚触发高等级风险公告，属于盘中需要优先规避的负面催化。";
            default -> stock + " 当前无实时推送结论。";
        };
        if (context.hasResonance()) {
            return base + " 且与【" + context.getThemeName() + "】主线形成共振。";
        }
        return base + " 暂未检测到明确主线共振。";
    }

    private String buildReasoning(AStockRss notice, AStockRealtimeContext context) {
        String eventHint = switch (StringUtils.defaultString(notice.getEventType())) {
            case "重大合同" -> "重大合同/中标通常直接抬升订单兑现预期，盘中更容易触发资金抢筹。";
            case "并购重组" -> "并购重组会重估资产与控制权预期，若进度实质推进，短线弹性通常较强。";
            case "业绩兑现" -> "业绩兑现会快速修正市场预期差，是资金最容易理解的基本面催化。";
            case "产品获批" -> "获批/认证意味着商业化预期改善，若叠加主线，容易强化情绪。";
            case "回购增持" -> "回购或增持传递管理层态度改善，但强度一般弱于订单和并购。";
            case "退市风险" -> "退市类公告会迅速压制风险偏好，短线资金通常优先撤退。";
            case "监管处罚" -> "立案/处罚显著提升监管不确定性，容易形成快速杀估值。";
            case "重整风险" -> "重整/破产会影响资产与交易连续性，属于高等级风险事件。";
            case "司法处置" -> "司法拍卖或强制处置通常伴随控制权与流动性风险。";
            case "诉讼仲裁" -> "重大诉讼仲裁会抬升赔偿和经营不确定性，短线偏负面。";
            case "业绩承压" -> "预亏和减值会压制盈利预期与估值锚。";
            default -> "该公告被规则识别为高分事件，具备盘中跟踪价值。";
        };
        if (!context.hasResonance()) {
            return eventHint;
        }
        return eventHint + " 同时主题上下文显示【" + context.getThemeName() + "】正在活跃，强化了资金关注度。";
    }

    private String buildRiskHint(AStockRss notice, AStockPushDecision decision, LocalDateTime now) {
        String sideHint = decision.getPushType() == AStockPushType.REALTIME_RISK
                ? "风险预警不等于立即跌停，但说明负面信息正在加速释放。"
                : "利好预警不等于直接涨停，仍需看开盘后的资金承接和板块联动。";
        return sideHint + " 触发时间：" + now.format(TIME_FORMATTER);
    }

    private String resolveSeverityLabel(int signalScore, AStockPushType pushType) {
        if (signalScore >= filterConfig.getARealtimeCriticalThreshold()) {
            return pushType == AStockPushType.REALTIME_RISK ? "核弹级风险" : "核弹级催化";
        }
        if (signalScore >= 90) {
            return pushType == AStockPushType.REALTIME_RISK ? "强风险预警" : "强催化预警";
        }
        return pushType == AStockPushType.REALTIME_RISK ? "高优先级风险" : "高优先级机会";
    }

    private AStockPushLog buildPushLog(String pushKey,
                                       AStockRss notice,
                                       AStockPushDecision decision,
                                       AStockRealtimeContext context,
                                       LocalDateTime now) {
        AStockPushLog pushLog = new AStockPushLog();
        pushLog.setId(UUID.randomUUID().toString().replace("-", ""));
        pushLog.setPushKey(pushKey);
        pushLog.setStockCode(notice.getStockCode());
        pushLog.setStockName(notice.getStockName());
        pushLog.setPushType(decision.getPushType().name());
        pushLog.setSignalSide(notice.getSignalSide());
        pushLog.setSignalScore(notice.getSignalScore());
        pushLog.setEventType(notice.getEventType());
        pushLog.setTitle(notice.getTitle());
        pushLog.setMacroThemeName(context.getThemeName());
        pushLog.setResonanceScore(context.getResonanceScore());
        pushLog.setDecisionReason(decision.getReason());
        pushLog.setPushedAt(now);
        pushLog.setCreateTime(now);
        return pushLog;
    }

    private String buildPushKey(AStockRss notice) {
        if (notice == null) {
            return "unknown";
        }
        if (StringUtils.isNotBlank(notice.getClusterKey())) {
            return notice.getClusterKey();
        }
        String titleHash = DigestUtils.md5DigestAsHex(
                StringUtils.defaultString(notice.getTitle()).replaceAll("\\s+", "")
                        .getBytes(StandardCharsets.UTF_8));
        return String.join("|",
                StringUtils.defaultString(notice.getStockCode()),
                StringUtils.defaultString(notice.getEventType()),
                titleHash);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
