package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockRealtimeContext;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.mapper.ThemeAutoPoolCandidateMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A股实时预警上下文服务
 */
@Service
public class AStockRealtimeContextService {

    private static final int MAX_RELATION_SCAN = 20;
    private static final int MAX_THEME_SCAN = 12;

    private final MacroThemeStockRelMapper macroThemeStockRelMapper;
    private final MacroThemeEventMapper macroThemeEventMapper;
    private final ThemeAutoPoolCandidateMapper themeAutoPoolCandidateMapper;
    private final StockFilterConfig filterConfig;

    public AStockRealtimeContextService(MacroThemeStockRelMapper macroThemeStockRelMapper,
                                        MacroThemeEventMapper macroThemeEventMapper,
                                        ThemeAutoPoolCandidateMapper themeAutoPoolCandidateMapper,
                                        StockFilterConfig filterConfig) {
        this.macroThemeStockRelMapper = macroThemeStockRelMapper;
        this.macroThemeEventMapper = macroThemeEventMapper;
        this.themeAutoPoolCandidateMapper = themeAutoPoolCandidateMapper;
        this.filterConfig = filterConfig;
    }

    public AStockRealtimeContext buildContext(AStockRss notice, LocalDateTime now) {
        if (notice == null || StringUtils.isBlank(notice.getStockCode())) {
            return AStockRealtimeContext.empty();
        }
        LocalDateTime referenceTime = now == null ? LocalDateTime.now() : now;
        AStockRealtimeContext relationContext = findMappedRelationContext(notice, referenceTime);
        AStockRealtimeContext poolContext = findThemePoolContext(notice, referenceTime);
        return pickBetterContext(relationContext, poolContext);
    }

    private AStockRealtimeContext findMappedRelationContext(AStockRss notice, LocalDateTime referenceTime) {
        LocalDateTime startTime = referenceTime.minusHours(filterConfig.getARealtimeContextHours());
        List<MacroThemeStockRel> relations = macroThemeStockRelMapper.selectList(new QueryWrapper<MacroThemeStockRel>()
                .eq("stock_code", notice.getStockCode())
                .ge("create_time", startTime)
                .orderByDesc("confidence")
                .orderByDesc("create_time")
                .last("LIMIT " + MAX_RELATION_SCAN));
        if (relations == null || relations.isEmpty()) {
            return AStockRealtimeContext.empty();
        }

        Map<String, MacroThemeStockRel> bestRelationByEventId = new LinkedHashMap<>();
        for (MacroThemeStockRel relation : relations) {
            if (relation == null || StringUtils.isBlank(relation.getThemeEventId())) {
                continue;
            }
            bestRelationByEventId.merge(relation.getThemeEventId(), relation,
                    (left, right) -> safeInt(left.getConfidence()) >= safeInt(right.getConfidence()) ? left : right);
        }
        List<String> themeEventIds = bestRelationByEventId.keySet().stream().toList();
        if (themeEventIds.isEmpty()) {
            return AStockRealtimeContext.empty();
        }

        Map<String, MacroThemeEvent> eventById = macroThemeEventMapper.selectBatchIds(themeEventIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(MacroThemeEvent::getId, event -> event, (left, right) -> left));

        return bestRelationByEventId.values().stream()
                .map(relation -> buildContextFromRelation(notice, relation, eventById.get(relation.getThemeEventId()), referenceTime))
                .filter(AStockRealtimeContext::hasResonance)
                .max(Comparator.comparingInt(context -> safeInt(context.getResonanceScore())))
                .orElseGet(AStockRealtimeContext::empty);
    }

    private AStockRealtimeContext findThemePoolContext(AStockRss notice, LocalDateTime referenceTime) {
        LocalDateTime startTime = referenceTime.minusHours(filterConfig.getARealtimeContextHours());
        List<ThemeAutoPoolCandidate> candidates = themeAutoPoolCandidateMapper.selectList(new QueryWrapper<ThemeAutoPoolCandidate>()
                .eq("stock_code", notice.getStockCode())
                .eq("enabled", 1)
                .orderByDesc("candidate_score")
                .orderByDesc("hit_count")
                .orderByDesc("latest_pub_date")
                .last("LIMIT 4"));
        if (candidates == null || candidates.isEmpty()) {
            return AStockRealtimeContext.empty();
        }

        List<String> themeNames = candidates.stream()
                .map(ThemeAutoPoolCandidate::getThemeName)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (themeNames.isEmpty()) {
            return AStockRealtimeContext.empty();
        }

        List<MacroThemeEvent> events = macroThemeEventMapper.selectList(new QueryWrapper<MacroThemeEvent>()
                .in("theme_name", themeNames)
                .ge("pub_date", startTime)
                .ge("signal_score", filterConfig.getMacroShadowSignalThreshold())
                .orderByDesc("signal_score")
                .orderByDesc("importance_level")
                .orderByDesc("pub_date")
                .last("LIMIT " + MAX_THEME_SCAN));
        if (events == null || events.isEmpty()) {
            return AStockRealtimeContext.empty();
        }

        Map<String, ThemeAutoPoolCandidate> candidateByTheme = candidates.stream()
                .filter(candidate -> StringUtils.isNotBlank(candidate.getThemeName()))
                .collect(Collectors.toMap(ThemeAutoPoolCandidate::getThemeName, candidate -> candidate,
                        (left, right) -> safeInt(left.getCandidateScore()) >= safeInt(right.getCandidateScore()) ? left : right));

        return events.stream()
                .map(event -> buildContextFromPool(notice, candidateByTheme.get(event.getThemeName()), event, referenceTime))
                .filter(AStockRealtimeContext::hasResonance)
                .max(Comparator.comparingInt(context -> safeInt(context.getResonanceScore())))
                .orElseGet(AStockRealtimeContext::empty);
    }

    private AStockRealtimeContext buildContextFromRelation(AStockRss notice,
                                                           MacroThemeStockRel relation,
                                                           MacroThemeEvent event,
                                                           LocalDateTime referenceTime) {
        if (relation == null
                || event == null
                || safeInt(event.getSignalScore()) < filterConfig.getMacroShadowSignalThreshold()
                || hasConflictingSide(notice, event)) {
            return AStockRealtimeContext.empty();
        }
        int resonanceScore = computeResonanceScore(
                safeInt(notice.getSignalScore()),
                safeInt(event.getSignalScore()),
                safeInt(relation.getConfidence()),
                event.getPubDate(),
                referenceTime,
                true
        );
        return new AStockRealtimeContext(
                event.getThemeName(),
                event.getTitle(),
                event.getSummary(),
                event.getSignalScore(),
                resonanceScore,
                StringUtils.defaultIfBlank(relation.getReason(), "宏观主题映射命中"),
                StringUtils.defaultIfBlank(relation.getMatchType(), "RELATION")
        );
    }

    private AStockRealtimeContext buildContextFromPool(AStockRss notice,
                                                       ThemeAutoPoolCandidate candidate,
                                                       MacroThemeEvent event,
                                                       LocalDateTime referenceTime) {
        if (candidate == null || event == null || hasConflictingSide(notice, event)) {
            return AStockRealtimeContext.empty();
        }
        int resonanceScore = computeResonanceScore(
                safeInt(notice.getSignalScore()),
                safeInt(event.getSignalScore()),
                safeInt(candidate.getCandidateScore()),
                event.getPubDate(),
                referenceTime,
                false
        );
        return new AStockRealtimeContext(
                event.getThemeName(),
                event.getTitle(),
                event.getSummary(),
                event.getSignalScore(),
                resonanceScore,
                StringUtils.defaultIfBlank(candidate.getReason(), "主题自动候选池命中"),
                "AUTO_POOL"
        );
    }

    private AStockRealtimeContext pickBetterContext(AStockRealtimeContext left, AStockRealtimeContext right) {
        if (left == null || !left.hasResonance()) {
            return right == null ? AStockRealtimeContext.empty() : right;
        }
        if (right == null || !right.hasResonance()) {
            return left;
        }
        return safeInt(left.getResonanceScore()) >= safeInt(right.getResonanceScore()) ? left : right;
    }

    private int computeResonanceScore(int noticeScore,
                                      int macroScore,
                                      int relationStrength,
                                      LocalDateTime eventTime,
                                      LocalDateTime referenceTime,
                                      boolean explicitMapping) {
        int total = Math.max(noticeScore, macroScore);
        total += (int) Math.round(Math.min(noticeScore, macroScore) * 0.35);
        total += Math.min(12, Math.max(0, relationStrength) / 8);
        total += explicitMapping ? 10 : 6;
        if (eventTime != null && !eventTime.isBefore(referenceTime.minusHours(6))) {
            total += 6;
        }
        return Math.min(180, total);
    }

    private boolean hasConflictingSide(AStockRss notice, MacroThemeEvent event) {
        String noticeSide = StringUtils.defaultIfBlank(notice.getSignalSide(), "中性");
        String macroSide = StringUtils.defaultIfBlank(event.getSignalSide(), "中性");
        return ("利多".equals(noticeSide) && "利空".equals(macroSide))
                || ("利空".equals(noticeSide) && "利多".equals(macroSide));
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
