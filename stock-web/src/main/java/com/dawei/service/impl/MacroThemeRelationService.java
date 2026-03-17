package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MacroNewsRaw;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.entity.ThemeWatchlist;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.ThemeWatchlistMapper;
import com.dawei.service.ThemeAutoPoolService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 宏观主题与标的映射关系构建器：
 * 1. 主题观察池映射（WATCHLIST）
 * 2. 文本显式提股映射（EXPLICIT）
 */
@Component
public class MacroThemeRelationService {

    private static final int EXPLICIT_STOCK_LOOKBACK_DAYS = 365;
    private static final Set<String> AMBIGUOUS_STOCK_NAMES = Set.of("机器人");
    private static final List<String> MARKET_DIGEST_KEYWORDS = List.of(
            "etf", "港股", "美股", "指数", "午评", "收评", "午后", "盘中", "早盘", "尾盘"
    );

    private final AStockRssMapper aStockRssMapper;
    private final ThemeWatchlistMapper themeWatchlistMapper;
    private final ThemeAutoPoolService themeAutoPoolService;

    public MacroThemeRelationService(AStockRssMapper aStockRssMapper,
                                     ThemeWatchlistMapper themeWatchlistMapper,
                                     ThemeAutoPoolService themeAutoPoolService) {
        this.aStockRssMapper = aStockRssMapper;
        this.themeWatchlistMapper = themeWatchlistMapper;
        this.themeAutoPoolService = themeAutoPoolService;
    }

    public Map<String, List<ThemeWatchlist>> loadEnabledWatchlist() {
        Map<String, ThemeWatchlist> merged = new LinkedHashMap<>();
        for (ThemeWatchlist mapping : themeWatchlistMapper.selectList(new QueryWrapper<ThemeWatchlist>()
                .eq("enabled", 1)
                .orderByDesc("priority")
                .orderByAsc("stock_code"))) {
            if (mapping == null || StringUtils.isBlank(mapping.getThemeName()) || StringUtils.isBlank(mapping.getStockCode())) {
                continue;
            }
            merged.put(normalizeThemeStockKey(mapping.getThemeName(), mapping.getStockCode()), mapping);
        }
        List<ThemeAutoPoolCandidate> autoCandidates = themeAutoPoolService.listEnabled();
        for (ThemeAutoPoolCandidate candidate : autoCandidates == null ? List.<ThemeAutoPoolCandidate>of() : autoCandidates) {
            if (candidate == null || StringUtils.isBlank(candidate.getThemeName()) || StringUtils.isBlank(candidate.getStockCode())) {
                continue;
            }
            String key = normalizeThemeStockKey(candidate.getThemeName(), candidate.getStockCode());
            merged.putIfAbsent(key, toThemeWatchlist(candidate));
        }
        return merged.values().stream()
                .collect(Collectors.groupingBy(ThemeWatchlist::getThemeName, LinkedHashMap::new, Collectors.toList()));
    }

    public List<MacroThemeStockRel> buildRelations(MacroThemeEvent event,
                                                   MacroNewsRaw raw,
                                                   Map<String, List<ThemeWatchlist>> watchlistByTheme) {
        return buildRelations(event, raw, watchlistByTheme, loadExplicitReferencePool(watchlistByTheme));
    }

    public List<MacroThemeStockRel> buildRelations(MacroThemeEvent event,
                                                   MacroNewsRaw raw,
                                                   Map<String, List<ThemeWatchlist>> watchlistByTheme,
                                                   List<StockReference> explicitReferences) {
        if (event == null || StringUtils.isBlank(event.getId())) {
            return List.of();
        }

        Map<String, MacroThemeStockRel> relations = new LinkedHashMap<>();
        for (ThemeWatchlist mapping : watchlistByTheme.getOrDefault(event.getThemeName(), List.of())) {
            addRelation(relations, buildWatchlistRelation(event, mapping));
        }

        String explicitText = buildExplicitText(event, raw);
        if (StringUtils.isBlank(explicitText)) {
            return new ArrayList<>(relations.values());
        }

        for (StockReference reference : defaultExplicitReferences(explicitReferences, watchlistByTheme)) {
            if (!explicitlyMentions(explicitText, event.getThemeName(), reference)) {
                continue;
            }
            addRelation(relations, buildExplicitRelation(event, reference, explicitText));
        }
        return new ArrayList<>(relations.values());
    }

    public List<StockReference> loadExplicitReferencePool(Map<String, List<ThemeWatchlist>> watchlistByTheme) {
        Map<String, StockReference> references = new LinkedHashMap<>();
        for (ThemeWatchlist mapping : flattenDistinctWatchlist(watchlistByTheme)) {
            mergeReference(references, new StockReference(
                    StringUtils.trimToNull(mapping.getStockCode()),
                    StringUtils.trimToNull(mapping.getStockName()),
                    safeInt(mapping.getPriority()),
                    true
            ));
        }

        LocalDateTime startTime = LocalDateTime.now().minusDays(EXPLICIT_STOCK_LOOKBACK_DAYS);
        List<Map<String, Object>> dbRows = aStockRssMapper.selectMaps(new QueryWrapper<AStockRss>()
                .select("stock_code", "stock_name")
                .ge("pub_date", startTime)
                .isNotNull("stock_code")
                .isNotNull("stock_name")
                .groupBy("stock_code", "stock_name"));
        for (Map<String, Object> row : dbRows) {
            mergeReference(references, new StockReference(
                    trimObject(row.get("stock_code")),
                    trimObject(row.get("stock_name")),
                    0,
                    false
            ));
        }
        return new ArrayList<>(references.values());
    }

    private List<ThemeWatchlist> flattenDistinctWatchlist(Map<String, List<ThemeWatchlist>> watchlistByTheme) {
        Set<String> seen = new LinkedHashSet<>();
        List<ThemeWatchlist> result = new ArrayList<>();
        for (List<ThemeWatchlist> mappings : watchlistByTheme.values()) {
            for (ThemeWatchlist mapping : mappings) {
                if (mapping == null || StringUtils.isBlank(mapping.getStockCode())) {
                    continue;
                }
                if (seen.add(mapping.getStockCode())) {
                    result.add(mapping);
                }
            }
        }
        return result;
    }

    private MacroThemeStockRel buildWatchlistRelation(MacroThemeEvent event, ThemeWatchlist mapping) {
        MacroThemeStockRel rel = baseRelation(event, mapping);
        rel.setConfidence(Math.max(50, 60 + safeInt(mapping.getPriority()) * 10));
        rel.setMatchType("WATCHLIST");
        rel.setReason(StringUtils.defaultIfBlank(mapping.getReason(), "主题观察池命中"));
        return rel;
    }

    private MacroThemeStockRel buildExplicitRelation(MacroThemeEvent event,
                                                     StockReference reference,
                                                     String explicitText) {
        MacroThemeStockRel rel = baseRelation(event, event.getThemeName(), reference.stockCode(), reference.stockName());
        rel.setConfidence(Math.max(85, 75 + safeInt(reference.priority()) * 5));
        rel.setMatchType("EXPLICIT");
        rel.setReason(buildExplicitReason(reference, explicitText));
        return rel;
    }

    private MacroThemeStockRel baseRelation(MacroThemeEvent event, ThemeWatchlist mapping) {
        return baseRelation(event, mapping.getThemeName(), mapping.getStockCode(), mapping.getStockName());
    }

    private MacroThemeStockRel baseRelation(MacroThemeEvent event,
                                            String themeName,
                                            String stockCode,
                                            String stockName) {
        MacroThemeStockRel rel = new MacroThemeStockRel();
        rel.setId(UUID.randomUUID().toString().replace("-", ""));
        rel.setThemeEventId(event.getId());
        rel.setThemeName(themeName);
        rel.setStockCode(stockCode);
        rel.setStockName(stockName);
        rel.setCreateTime(LocalDateTime.now());
        return rel;
    }

    private void addRelation(Map<String, MacroThemeStockRel> relations, MacroThemeStockRel relation) {
        if (relation == null || StringUtils.isBlank(relation.getStockCode()) || StringUtils.isBlank(relation.getMatchType())) {
            return;
        }
        relations.putIfAbsent(relation.getStockCode() + "|" + relation.getMatchType(), relation);
    }

    private boolean explicitlyMentions(String explicitText, String eventThemeName, StockReference reference) {
        String stockCode = StringUtils.trimToEmpty(reference.stockCode());
        String stockName = normalizeText(reference.stockName());
        if (StringUtils.isNotBlank(stockCode) && containsStockCode(explicitText, stockCode)) {
            return true;
        }
        if (isAmbiguousStockName(stockName, normalizeText(eventThemeName))) {
            return false;
        }
        if (looksLikeMarketDigest(explicitText)) {
            return false;
        }
        return StringUtils.length(stockName) >= 4 && explicitText.contains(stockName);
    }

    private String buildExplicitReason(StockReference reference, String explicitText) {
        String stockCode = StringUtils.trimToEmpty(reference.stockCode());
        String stockName = normalizeText(reference.stockName());
        if (StringUtils.isNotBlank(stockCode) && containsStockCode(explicitText, stockCode)) {
            return "标题/摘要显式提及股票代码";
        }
        if (StringUtils.length(stockName) >= 3 && explicitText.contains(stockName)) {
            return "标题/摘要显式提及股票名称";
        }
        return "标题/摘要显式提及标的";
    }

    private String buildExplicitText(MacroThemeEvent event, MacroNewsRaw raw) {
        return normalizeText(String.join(" ",
                StringUtils.defaultString(raw != null ? raw.getTitle() : null),
                StringUtils.defaultString(event != null ? event.getTitle() : null),
                StringUtils.defaultString(event != null ? event.getSummary() : null)));
    }

    private boolean isAmbiguousStockName(String stockName, String themeName) {
        if (StringUtils.isBlank(stockName)) {
            return true;
        }
        return AMBIGUOUS_STOCK_NAMES.contains(stockName)
                || (StringUtils.length(stockName) <= 3 && StringUtils.equals(stockName, themeName));
    }

    private String normalizeText(String value) {
        return StringUtils.defaultString(value)
                .replace('\u00A0', ' ')
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", "")
                .toLowerCase();
    }

    private boolean containsStockCode(String text, String stockCode) {
        Pattern pattern = Pattern.compile("(^|[^0-9])" + Pattern.quote(stockCode) + "([^0-9]|$)");
        return pattern.matcher(text).find();
    }

    private boolean looksLikeMarketDigest(String explicitText) {
        for (String keyword : MARKET_DIGEST_KEYWORDS) {
            if (explicitText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private ThemeWatchlist toThemeWatchlist(ThemeAutoPoolCandidate candidate) {
        ThemeWatchlist mapping = new ThemeWatchlist();
        mapping.setId(candidate.getId());
        mapping.setThemeName(candidate.getThemeName());
        mapping.setStockCode(candidate.getStockCode());
        mapping.setStockName(candidate.getStockName());
        mapping.setPriority(toPriority(candidate.getCandidateScore()));
        mapping.setEnabled(1);
        mapping.setReason(StringUtils.defaultIfBlank(candidate.getReason(), "自动候选池命中"));
        mapping.setCreateTime(candidate.getCreateTime());
        return mapping;
    }

    private int toPriority(Integer candidateScore) {
        int score = safeInt(candidateScore);
        if (score >= 95) {
            return 3;
        }
        if (score >= 90) {
            return 2;
        }
        return 1;
    }

    private String normalizeThemeStockKey(String themeName, String stockCode) {
        return StringUtils.trimToEmpty(themeName) + "|" + StringUtils.trimToEmpty(stockCode);
    }

    private List<StockReference> defaultExplicitReferences(List<StockReference> explicitReferences,
                                                           Map<String, List<ThemeWatchlist>> watchlistByTheme) {
        if (explicitReferences != null && !explicitReferences.isEmpty()) {
            return explicitReferences;
        }
        return loadExplicitReferencePool(watchlistByTheme);
    }

    private void mergeReference(Map<String, StockReference> references, StockReference candidate) {
        if (candidate == null
                || StringUtils.isBlank(candidate.stockCode())
                || StringUtils.isBlank(candidate.stockName())) {
            return;
        }
        references.merge(candidate.stockCode(), candidate, (left, right) -> preferReference(left, right) ? left : right);
    }

    private boolean preferReference(StockReference left, StockReference right) {
        if (left.fromWatchlist() != right.fromWatchlist()) {
            return left.fromWatchlist();
        }
        if (safeInt(left.priority()) != safeInt(right.priority())) {
            return safeInt(left.priority()) > safeInt(right.priority());
        }
        return StringUtils.length(StringUtils.defaultString(left.stockName()))
                >= StringUtils.length(StringUtils.defaultString(right.stockName()));
    }

    private String trimObject(Object value) {
        return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
    }

    public record StockReference(String stockCode,
                                 String stockName,
                                 Integer priority,
                                 boolean fromWatchlist) {
    }
}
