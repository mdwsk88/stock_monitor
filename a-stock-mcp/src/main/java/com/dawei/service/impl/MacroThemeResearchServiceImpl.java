package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.dto.MacroThemeCard;
import com.dawei.dto.ResonanceStockCard;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.mapper.ThemeAutoPoolCandidateMapper;
import com.dawei.service.MacroThemeResearchService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MacroThemeResearchServiceImpl implements MacroThemeResearchService {

    private static final int DEFAULT_LOOKBACK_HOURS = 24;
    private static final int DEFAULT_LIMIT = 6;
    private static final int DEFAULT_FETCH_LIMIT = 120;
    private static final int DEFAULT_MIN_SIGNAL_SCORE = 80;

    @Resource
    private MacroThemeEventMapper macroThemeEventMapper;

    @Resource
    private MacroThemeStockRelMapper macroThemeStockRelMapper;

    @Resource
    private ThemeAutoPoolCandidateMapper themeAutoPoolCandidateMapper;

    @Override
    public List<MacroThemeCard> getMacroThemeBoard(Integer hours, Integer minSignalScore, Integer limit) {
        int normalizedHours = normalizedHours(hours);
        int normalizedLimit = normalizedLimit(limit);

        List<MacroThemeEvent> events = fetchMacroThemeEvents(normalizedHours, normalizedSignalScore(minSignalScore));
        if (events.isEmpty()) {
            return List.of();
        }

        Map<String, List<MacroThemeStockRel>> relsByEventId = fetchRelationsByEventId(events);
        List<MacroThemeCard> cards = events.stream()
                .collect(Collectors.groupingBy(this::themeGroupKey, LinkedHashMap::new, Collectors.toList()))
                .values()
                .stream()
                .map(group -> toMacroThemeCard(group, relsByEventId))
                .sorted(Comparator
                        .comparing(MacroThemeCard::getSignalScore, Comparator.reverseOrder())
                        .thenComparing(MacroThemeCard::getImportanceLevel, Comparator.reverseOrder())
                        .thenComparing(MacroThemeCard::getMappedStockCount, Comparator.reverseOrder())
                        .thenComparing(MacroThemeCard::getLatestPubDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        return cards.subList(0, Math.min(normalizedLimit, cards.size()));
    }

    @Override
    public List<ResonanceStockCard> getThemeResonanceBoard(String themeName, Integer hours, Integer limit) {
        int normalizedHours = normalizedHours(hours);
        int normalizedLimit = normalizedLimit(limit);

        QueryWrapper<ThemeAutoPoolCandidate> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("enabled", 1);
        queryWrapper.ge("latest_pub_date", LocalDateTime.now().minusHours(normalizedHours));
        if (StringUtils.hasText(themeName)) {
            queryWrapper.eq("theme_name", themeName.trim());
        }
        queryWrapper.orderByDesc("candidate_score")
                .orderByDesc("hit_count")
                .orderByDesc("latest_pub_date")
                .last(limitClause(DEFAULT_FETCH_LIMIT));

        List<ThemeAutoPoolCandidate> candidates = themeAutoPoolCandidateMapper.selectList(queryWrapper);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, MacroThemeEvent> bestThemeEventByThemeStock = fetchBestThemeEventByThemeStock(candidates, normalizedHours);
        List<ResonanceStockCard> cards = candidates.stream()
                .map(candidate -> toResonanceCard(candidate, bestThemeEventByThemeStock))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(ResonanceStockCard::getCandidateScore, Comparator.reverseOrder())
                        .thenComparing(ResonanceStockCard::getThemeSignalScore, Comparator.reverseOrder())
                        .thenComparing(ResonanceStockCard::getLatestPubDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        return cards.subList(0, Math.min(normalizedLimit, cards.size()));
    }

    private List<MacroThemeEvent> fetchMacroThemeEvents(int hours, int minSignalScore) {
        QueryWrapper<MacroThemeEvent> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("signal_score", minSignalScore);
        queryWrapper.ge("pub_date", LocalDateTime.now().minusHours(hours));
        queryWrapper.orderByDesc("signal_score")
                .orderByDesc("importance_level")
                .orderByDesc("pub_date")
                .last(limitClause(DEFAULT_FETCH_LIMIT));
        return macroThemeEventMapper.selectList(queryWrapper);
    }

    private Map<String, List<MacroThemeStockRel>> fetchRelationsByEventId(List<MacroThemeEvent> events) {
        List<String> eventIds = events.stream().map(MacroThemeEvent::getId).toList();
        QueryWrapper<MacroThemeStockRel> relWrapper = new QueryWrapper<>();
        relWrapper.in("theme_event_id", eventIds);
        List<MacroThemeStockRel> relations = macroThemeStockRelMapper.selectList(relWrapper);
        return relations.stream().collect(Collectors.groupingBy(MacroThemeStockRel::getThemeEventId));
    }

    private MacroThemeCard toMacroThemeCard(List<MacroThemeEvent> groupedEvents,
                                            Map<String, List<MacroThemeStockRel>> relsByEventId) {
        List<MacroThemeEvent> sortedEvents = groupedEvents.stream()
                .sorted(Comparator
                        .comparing(MacroThemeEvent::getSignalScore, Comparator.reverseOrder())
                        .thenComparing(MacroThemeEvent::getImportanceLevel, Comparator.reverseOrder())
                        .thenComparing(MacroThemeEvent::getPubDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        MacroThemeEvent representative = sortedEvents.get(0);
        LocalDateTime latestPubDate = groupedEvents.stream()
                .map(MacroThemeEvent::getPubDate)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(representative.getPubDate());

        List<MacroThemeStockRel> mappedRelations = groupedEvents.stream()
                .map(MacroThemeEvent::getId)
                .map(relsByEventId::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();

        Set<String> mappedStockCodes = mappedRelations.stream()
                .map(MacroThemeStockRel::getStockCode)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        String mappedStocks = mappedRelations.stream()
                .map(MacroThemeStockRel::getStockName)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .collect(Collectors.joining("、"));
        String relatedTitles = groupedEvents.stream()
                .map(MacroThemeEvent::getTitle)
                .filter(title -> !Objects.equals(title, representative.getTitle()))
                .distinct()
                .limit(3)
                .collect(Collectors.joining("；"));
        String analysisHint = representative.getThemeName() + "主题最近 "
                + groupedEvents.size() + " 条核心事件，方向为" + sideLabel(representative.getSignalSide())
                + "，映射 " + mappedStockCodes.size() + " 只股票。";

        return new MacroThemeCard(
                representative.getThemeName(),
                representative.getTitle(),
                representative.getSummary(),
                representative.getSignalSide(),
                representative.getSignalScore(),
                representative.getImportanceLevel(),
                latestPubDate,
                groupedEvents.size(),
                mappedStockCodes.size(),
                mappedStocks,
                relatedTitles,
                analysisHint
        );
    }

    private Map<String, MacroThemeEvent> fetchBestThemeEventByThemeStock(List<ThemeAutoPoolCandidate> candidates, int hours) {
        List<String> themeNames = candidates.stream().map(ThemeAutoPoolCandidate::getThemeName).distinct().toList();
        QueryWrapper<MacroThemeEvent> eventWrapper = new QueryWrapper<>();
        eventWrapper.in("theme_name", themeNames);
        eventWrapper.ge("pub_date", LocalDateTime.now().minusHours(hours));
        eventWrapper.orderByDesc("signal_score")
                .orderByDesc("pub_date")
                .last(limitClause(DEFAULT_FETCH_LIMIT));
        List<MacroThemeEvent> events = macroThemeEventMapper.selectList(eventWrapper);
        if (events.isEmpty()) {
            return Map.of();
        }

        List<String> eventIds = events.stream().map(MacroThemeEvent::getId).toList();
        QueryWrapper<MacroThemeStockRel> relWrapper = new QueryWrapper<>();
        relWrapper.in("theme_event_id", eventIds);
        List<MacroThemeStockRel> relations = macroThemeStockRelMapper.selectList(relWrapper);

        Map<String, MacroThemeEvent> eventById = events.stream()
                .collect(Collectors.toMap(MacroThemeEvent::getId, event -> event));
        Map<String, MacroThemeEvent> bestEventByThemeStock = new LinkedHashMap<>();
        for (MacroThemeStockRel relation : relations) {
            MacroThemeEvent event = eventById.get(relation.getThemeEventId());
            if (event == null) {
                continue;
            }
            String key = relation.getThemeName() + ":" + relation.getStockCode();
            MacroThemeEvent current = bestEventByThemeStock.get(key);
            if (current == null || compareThemeEvent(event, current) < 0) {
                bestEventByThemeStock.put(key, event);
            }
        }
        return bestEventByThemeStock;
    }

    private int compareThemeEvent(MacroThemeEvent left, MacroThemeEvent right) {
        Comparator<MacroThemeEvent> comparator = Comparator
                .comparing(MacroThemeEvent::getSignalScore, Comparator.reverseOrder())
                .thenComparing(MacroThemeEvent::getImportanceLevel, Comparator.reverseOrder())
                .thenComparing(MacroThemeEvent::getPubDate, Comparator.nullsLast(Comparator.reverseOrder()));
        return comparator.compare(left, right);
    }

    private ResonanceStockCard toResonanceCard(ThemeAutoPoolCandidate candidate,
                                               Map<String, MacroThemeEvent> bestThemeEventByThemeStock) {
        MacroThemeEvent themeEvent = bestThemeEventByThemeStock.get(candidate.getThemeName() + ":" + candidate.getStockCode());
        if (themeEvent == null || !"BUY".equals(themeEvent.getSignalSide())) {
            return null;
        }
        Integer themeSignalScore = themeEvent.getSignalScore();
        String themeSignalSide = themeEvent.getSignalSide();
        String relatedEventTitle = themeEvent.getTitle();
        String analysisHint = candidate.getThemeName() + "共振，候选分 "
                + candidate.getCandidateScore() + "，命中 " + candidate.getHitCount() + " 次。";
        return new ResonanceStockCard(
                candidate.getThemeName(),
                candidate.getStockCode(),
                candidate.getStockName(),
                candidate.getCandidateScore(),
                candidate.getHitCount(),
                candidate.getLatestPubDate(),
                themeSignalScore,
                themeSignalSide,
                relatedEventTitle,
                candidate.getReason(),
                analysisHint
        );
    }

    private String themeGroupKey(MacroThemeEvent event) {
        if (StringUtils.hasText(event.getClusterKey())) {
            return event.getClusterKey();
        }
        return event.getThemeName() + ":" + event.getEventType() + ":" + event.getSignalSide() + ":" + event.getTitle();
    }

    private int normalizedHours(Integer hours) {
        if (hours == null || hours <= 0) {
            return DEFAULT_LOOKBACK_HOURS;
        }
        return Math.min(hours, 72);
    }

    private int normalizedLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 10);
    }

    private int normalizedSignalScore(Integer score) {
        if (score == null) {
            return DEFAULT_MIN_SIGNAL_SCORE;
        }
        return Math.max(0, Math.min(100, score));
    }

    private String sideLabel(String signalSide) {
        return switch (signalSide) {
            case "BUY" -> "利多";
            case "SELL" -> "利空";
            default -> "中性";
        };
    }

    private String limitClause(int limit) {
        return "LIMIT " + limit;
    }
}
