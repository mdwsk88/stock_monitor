package com.dawei.service.impl;

import com.dawei.entity.AReportFusionContext;
import com.dawei.entity.AReportOpportunityInsight;
import com.dawei.entity.AReportResonanceCard;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.StockAlertDTO;
import com.dawei.service.AReportFusionService;
import com.dawei.service.MacroNewsService;
import com.dawei.service.MarketStateService;
import com.dawei.service.StockRankService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A股报告融合服务实现
 */
@Service
public class AReportFusionServiceImpl implements AReportFusionService {

    private static final int SECTION_LIMIT = 3;
    private static final int MIN_MACRO_POOL_LIMIT = 12;
    private static final int MACRO_POOL_MULTIPLIER = 4;
    private static final List<String> M_AND_A_MACRO_KEYWORDS = List.of(
            "并购", "重组", "资产注入", "发行股份购买资产", "国企改革", "国资国企", "央企重组", "收购"
    );
    private static final List<String> M_AND_A_NOTICE_KEYWORDS = List.of(
            "并购重组", "重大资产重组", "发行股份购买资产", "收购出售资产", "资产注入", "购买资产", "收购"
    );
    private static final Map<String, List<String>> THEME_KEYWORDS = Map.ofEntries(
            Map.entry("国企改革", List.of("国企改革", "国资国企", "央企重组", "资产注入", "国企并购")),
            Map.entry("并购重组", List.of("并购", "重组", "资产注入", "发行股份购买资产", "收购")),
            Map.entry("金融", List.of("金融", "券商", "保险", "银行", "资本市场")),
            Map.entry("稳增长", List.of("稳增长", "基建", "专项债", "重大项目", "设备更新", "工程机械")),
            Map.entry("算力", List.of("算力", "智算", "数据中心", "服务器", "光模块")),
            Map.entry("人工智能", List.of("人工智能", "大模型", "aigc", "智能体")),
            Map.entry("低空经济", List.of("低空经济", "飞行汽车", "无人机", "通航", "evtol")),
            Map.entry("机器人", List.of("机器人", "人形机器人", "工业机器人", "机器狗")),
            Map.entry("半导体", List.of("半导体", "芯片", "晶圆", "封装", "存储")),
            Map.entry("创新药", List.of("创新药", "药物", "双抗", "adc", "car-t")),
            Map.entry("军工", List.of("军工", "国防", "导弹", "卫星")),
            Map.entry("稀土", List.of("稀土", "镨钕", "氧化镨钕")),
            Map.entry("光伏", List.of("光伏", "硅料", "硅片", "逆变器")),
            Map.entry("锂电", List.of("锂电", "电池", "锂矿", "固态电池")),
            Map.entry("黄金", List.of("黄金", "金价")),
            Map.entry("原油", List.of("原油", "油价", "布伦特", "wti")),
            Map.entry("天然气", List.of("天然气", "lng"))
    );

    private final StockRankService stockRankService;
    private final MacroNewsService macroNewsService;
    private final MarketStateService marketStateService;
    private final AStockReportClassifier aStockReportClassifier;
    private final AReportOpportunityInsightService opportunityInsightService;

    public AReportFusionServiceImpl(StockRankService stockRankService,
                                    MacroNewsService macroNewsService,
                                    MarketStateService marketStateService,
                                    AStockReportClassifier aStockReportClassifier,
                                    AReportOpportunityInsightService opportunityInsightService) {
        this.stockRankService = stockRankService;
        this.macroNewsService = macroNewsService;
        this.marketStateService = marketStateService;
        this.aStockReportClassifier = aStockReportClassifier;
        this.opportunityInsightService = opportunityInsightService;
    }

    @Override
    public AReportFusionContext buildContext(LocalDateTime startTime,
                                             LocalDateTime endTime,
                                             int stockLimit,
                                             int macroThemeLimit,
                                             int resonanceLimit) {
        int effectiveMacroLimit = Math.max(1, macroThemeLimit);
        int effectiveResonanceLimit = Math.max(1, resonanceLimit);
        List<StockAlertDTO<AStockRss>> rankedAlerts = stockRankService.getATopNStocksWithFrequency(
                Math.max(stockLimit, SECTION_LIMIT * 2),
                startTime,
                endTime
        );
        AStockReportClassifier.Sections sections = aStockReportClassifier.split(rankedAlerts, SECTION_LIMIT);
        List<MacroThemeEvent> macroThemePool = macroNewsService.getShadowThemeEvents(
                startTime,
                endTime,
                Math.max(MIN_MACRO_POOL_LIMIT, Math.max(effectiveMacroLimit, effectiveResonanceLimit) * MACRO_POOL_MULTIPLIER)
        );
        MarketSnapshot marketSnapshot = marketStateService.getLatestSnapshot();
        List<AReportResonanceCard> resonanceCards = buildResonanceCards(rankedAlerts, macroThemePool, endTime, effectiveResonanceLimit);
        List<MacroThemeEvent> macroThemes = selectMacroThemesForDisplay(macroThemePool, resonanceCards, effectiveMacroLimit);
        List<AReportOpportunityInsight> opportunityInsights = opportunityInsightService.buildInsights(
                sections.opportunities(),
                resonanceCards,
                marketSnapshot
        );

        AReportFusionContext context = new AReportFusionContext();
        context.setWindowStart(startTime);
        context.setWindowEnd(endTime);
        context.setMarketSnapshot(marketSnapshot);
        context.setMacroThemes(macroThemes);
        context.setResonanceCandidates(resonanceCards);
        context.setOpportunityInsights(opportunityInsights);
        context.setOpportunityAlerts(sections.opportunities());
        context.setRiskAlerts(sections.risks());
        return context;
    }

    private List<MacroThemeEvent> selectMacroThemesForDisplay(List<MacroThemeEvent> macroThemePool,
                                                              List<AReportResonanceCard> resonanceCards,
                                                              int macroThemeLimit) {
        if (macroThemePool == null || macroThemePool.isEmpty()) {
            return List.of();
        }

        Comparator<MacroThemeEvent> byPriority = Comparator
                .comparing((MacroThemeEvent event) -> safeInt(event.getSignalScore())).reversed()
                .thenComparing(event -> safeInt(event.getMappedStockCount()), Comparator.reverseOrder())
                .thenComparing(MacroThemeEvent::getPubDate, Comparator.nullsLast(Comparator.reverseOrder()));

        List<MacroThemeEvent> ordered = new ArrayList<>();
        if (resonanceCards != null) {
            for (AReportResonanceCard card : resonanceCards) {
                if (card == null || StringUtils.isBlank(card.getMacroThemeName())) {
                    continue;
                }
                for (MacroThemeEvent event : macroThemePool) {
                    if (event == null || !matchesThemeName(card.getMacroThemeName(), event.getThemeName())) {
                        continue;
                    }
                    addIfAbsent(ordered, event);
                }
            }
        }

        macroThemePool.stream()
                .filter(Objects::nonNull)
                .filter(event -> safeInt(event.getMappedStockCount()) > 0)
                .sorted(byPriority)
                .forEach(event -> addIfAbsent(ordered, event));

        macroThemePool.stream()
                .filter(Objects::nonNull)
                .sorted(byPriority)
                .forEach(event -> addIfAbsent(ordered, event));

        return ordered.stream()
                .collect(Collectors.collectingAndThen(Collectors.toList(), this::mergeDisplayThemesByThemeName))
                .stream()
                .limit(Math.max(1, macroThemeLimit))
                .collect(Collectors.toList());
    }

    private List<MacroThemeEvent> mergeDisplayThemesByThemeName(List<MacroThemeEvent> ordered) {
        if (ordered == null || ordered.isEmpty()) {
            return List.of();
        }
        Map<String, MacroThemeEvent> mergedByTheme = new LinkedHashMap<>();
        for (MacroThemeEvent candidate : ordered) {
            if (candidate == null) {
                continue;
            }
            String key = StringUtils.defaultIfBlank(candidate.getThemeName(), candidate.getId());
            MacroThemeEvent existing = mergedByTheme.get(key);
            if (existing == null) {
                mergedByTheme.put(key, copyMacroTheme(candidate));
                continue;
            }
            mergeMacroThemeForDisplay(existing, candidate);
        }
        return new ArrayList<>(mergedByTheme.values());
    }

    private MacroThemeEvent copyMacroTheme(MacroThemeEvent source) {
        MacroThemeEvent copy = new MacroThemeEvent();
        copy.setId(source.getId());
        copy.setSourceName(source.getSourceName());
        copy.setSourceType(source.getSourceType());
        copy.setNewsKey(source.getNewsKey());
        copy.setTitle(source.getTitle());
        copy.setSummary(source.getSummary());
        copy.setLink(source.getLink());
        copy.setSourceTags(source.getSourceTags());
        copy.setPubDate(source.getPubDate());
        copy.setCreateTime(source.getCreateTime());
        copy.setThemeName(source.getThemeName());
        copy.setEventType(source.getEventType());
        copy.setSignalSide(source.getSignalSide());
        copy.setSignalScore(source.getSignalScore());
        copy.setImportanceLevel(source.getImportanceLevel());
        copy.setClusterKey(source.getClusterKey());
        copy.setClusterEventCount(source.getClusterEventCount());
        copy.setMappedStockCount(source.getMappedStockCount());
        copy.setMappedStocks(source.getMappedStocks());
        return copy;
    }

    private void mergeMacroThemeForDisplay(MacroThemeEvent base, MacroThemeEvent candidate) {
        base.setSourceName(joinDistinct(base.getSourceName(), candidate.getSourceName()));
        base.setEventType(joinDistinct(base.getEventType(), candidate.getEventType()));
        base.setTitle(joinDistinct(base.getTitle(), candidate.getTitle()));
        base.setSummary(joinDistinct(base.getSummary(), candidate.getSummary()));
        base.setMappedStocks(joinDistinctWithDelimiter(base.getMappedStocks(), candidate.getMappedStocks(), "、"));
        base.setMappedStockCount(countDelimitedItems(base.getMappedStocks(), "、"));
        base.setSignalScore(Math.max(safeInt(base.getSignalScore()), safeInt(candidate.getSignalScore())));
        base.setImportanceLevel(Math.max(safeInt(base.getImportanceLevel()), safeInt(candidate.getImportanceLevel())));
        base.setClusterEventCount(safeInt(base.getClusterEventCount()) + Math.max(1, safeInt(candidate.getClusterEventCount())));
        if (candidate.getPubDate() != null && (base.getPubDate() == null || candidate.getPubDate().isAfter(base.getPubDate()))) {
            base.setPubDate(candidate.getPubDate());
        }
    }

    private List<AReportResonanceCard> buildResonanceCards(List<StockAlertDTO<AStockRss>> rankedAlerts,
                                                           List<MacroThemeEvent> macroThemes,
                                                           LocalDateTime endTime,
                                                           int resonanceLimit) {
        if (rankedAlerts == null || rankedAlerts.isEmpty() || macroThemes == null || macroThemes.isEmpty()) {
            return List.of();
        }

        Map<String, StockAlertDTO<AStockRss>> alertByCode = rankedAlerts.stream()
                .filter(Objects::nonNull)
                .filter(dto -> dto.getStock() != null)
                .collect(Collectors.toMap(
                        dto -> dto.getStock().getStockCode(),
                        dto -> dto,
                        (left, right) -> left.getSignalScore() >= right.getSignalScore() ? left : right,
                        LinkedHashMap::new
                ));

        Map<String, AReportResonanceCard> resonanceByStock = new LinkedHashMap<>();
        for (MacroThemeEvent macroTheme : macroThemes) {
            if (macroTheme == null) {
                continue;
            }
            for (StockAlertDTO<AStockRss> dto : alertByCode.values()) {
                AStockRss stock = dto.getStock();
                if (stock == null || StringUtils.isBlank(stock.getStockCode())) {
                    continue;
                }
                if (!isResonanceMatch(dto, macroTheme)) {
                    continue;
                }
                if (!isResonanceCompatible(dto, macroTheme)) {
                    continue;
                }

                AReportResonanceCard candidate = buildResonanceCard(dto, macroTheme, endTime);
                mergeResonanceCard(resonanceByStock, candidate);
            }
        }

        return resonanceByStock.values().stream()
                .sorted(Comparator
                        .comparingInt(AReportResonanceCard::getFusionScore).reversed()
                        .thenComparing(AReportResonanceCard::getMacroSignalScore, Comparator.reverseOrder())
                        .thenComparing(AReportResonanceCard::getNoticeSignalScore, Comparator.reverseOrder()))
                .limit(resonanceLimit)
                .collect(Collectors.toList());
    }

    private boolean isResonanceMatch(StockAlertDTO<AStockRss> dto, MacroThemeEvent macroTheme) {
        return containsMappedStock(macroTheme.getMappedStocks(), dto.getStock())
                || matchesThemeKeywordResonance(dto, macroTheme)
                || matchesMacroEventResonance(dto, macroTheme);
    }

    private boolean containsMappedStock(String mappedStocks, AStockRss stock) {
        if (StringUtils.isBlank(mappedStocks) || stock == null) {
            return false;
        }
        String stockCode = StringUtils.trimToEmpty(stock.getStockCode());
        String stockName = StringUtils.defaultString(stock.getStockName());
        return mappedStocks.contains("(" + stockCode + ")")
                || (!stockName.isBlank() && mappedStocks.contains(stockName + "(" + stockCode + ")"));
    }

    private boolean matchesThemeKeywordResonance(StockAlertDTO<AStockRss> dto, MacroThemeEvent macroTheme) {
        List<String> keywords = THEME_KEYWORDS.get(macroTheme.getThemeName());
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        String macroText = normalizeText(joinText(
                macroTheme.getThemeName(),
                macroTheme.getEventType(),
                macroTheme.getTitle(),
                macroTheme.getSummary()
        ));
        String noticeText = normalizeText(joinText(
                dto.getStock().getEventType(),
                dto.getStock().getTitle(),
                dto.getStock().getAnalysisHint(),
                dto.getStock().getClusterHighlights()
        ));
        return containsAnyKeyword(macroText, keywords) && containsAnyKeyword(noticeText, keywords);
    }

    private boolean matchesMacroEventResonance(StockAlertDTO<AStockRss> dto, MacroThemeEvent macroTheme) {
        String macroText = normalizeText(joinText(
                macroTheme.getThemeName(),
                macroTheme.getEventType(),
                macroTheme.getTitle(),
                macroTheme.getSummary()
        ));
        if (!containsAnyKeyword(macroText, M_AND_A_MACRO_KEYWORDS)) {
            return false;
        }
        String noticeText = normalizeText(joinText(
                dto.getStock().getEventType(),
                dto.getStock().getTitle(),
                dto.getStock().getAnalysisHint(),
                dto.getStock().getClusterHighlights()
        ));
        return containsAnyKeyword(noticeText, M_AND_A_NOTICE_KEYWORDS);
    }

    private boolean isResonanceCompatible(StockAlertDTO<AStockRss> dto, MacroThemeEvent macroTheme) {
        String noticeSide = StringUtils.defaultIfBlank(dto.getSignalSide(), "中性");
        String macroSide = StringUtils.defaultIfBlank(macroTheme.getSignalSide(), "中性");
        if ("利多".equals(noticeSide) && "利空".equals(macroSide)) {
            return false;
        }
        return !"利空".equals(noticeSide) || !"利多".equals(macroSide);
    }

    private AReportResonanceCard buildResonanceCard(StockAlertDTO<AStockRss> dto,
                                                    MacroThemeEvent macroTheme,
                                                    LocalDateTime endTime) {
        AStockRss stock = dto.getStock();
        String resonanceSide = resolveResonanceSide(dto.getSignalSide(), macroTheme.getSignalSide());
        int fusionScore = computeFusionScore(dto, macroTheme, endTime);

        AReportResonanceCard card = new AReportResonanceCard();
        card.setStockCode(stock.getStockCode());
        card.setStockName(stock.getStockName());
        card.setSignalSide(resonanceSide);
        card.setFusionScore(fusionScore);
        card.setNoticeSignalScore(dto.getSignalScore());
        card.setMacroSignalScore(safeInt(macroTheme.getSignalScore()));
        card.setEventClusterCount(dto.getEventCount());
        card.setSupportNoticeCount(dto.getFrequency());
        card.setNoticeEventType(stock.getEventType());
        card.setNoticeTitle(stock.getTitle());
        card.setNoticeAnalysisHint(stock.getAnalysisHint());
        card.setMacroThemeName(macroTheme.getThemeName());
        card.setMacroEventType(macroTheme.getEventType());
        card.setMacroTitle(macroTheme.getTitle());
        card.setMacroSummary(macroTheme.getSummary());
        card.setRelationReason(buildRelationReason(macroTheme, stock));
        return card;
    }

    private void mergeResonanceCard(Map<String, AReportResonanceCard> resonanceByStock,
                                    AReportResonanceCard candidate) {
        if (candidate == null || StringUtils.isBlank(candidate.getStockCode())) {
            return;
        }
        AReportResonanceCard existing = resonanceByStock.get(candidate.getStockCode());
        if (existing == null) {
            resonanceByStock.put(candidate.getStockCode(), candidate);
            return;
        }
        if (candidate.getFusionScore() > existing.getFusionScore()) {
            candidate.setMacroThemeName(joinDistinct(candidate.getMacroThemeName(), existing.getMacroThemeName()));
            candidate.setMacroTitle(joinDistinct(candidate.getMacroTitle(), existing.getMacroTitle()));
            resonanceByStock.put(candidate.getStockCode(), candidate);
            return;
        }
        existing.setMacroThemeName(joinDistinct(existing.getMacroThemeName(), candidate.getMacroThemeName()));
        existing.setMacroTitle(joinDistinct(existing.getMacroTitle(), candidate.getMacroTitle()));
        existing.setMacroSignalScore(Math.max(existing.getMacroSignalScore(), candidate.getMacroSignalScore()));
        existing.setFusionScore(Math.max(existing.getFusionScore(), candidate.getFusionScore()));
    }

    private String joinDistinct(String left, String right) {
        List<String> merged = new ArrayList<>();
        for (String value : new String[]{left, right}) {
            if (StringUtils.isBlank(value)) {
                continue;
            }
            for (String part : value.split("\\s*\\|\\s*")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && !merged.contains(trimmed)) {
                    merged.add(trimmed);
                }
            }
        }
        return String.join(" | ", merged);
    }

    private String joinDistinctWithDelimiter(String left, String right, String delimiter) {
        List<String> merged = new ArrayList<>();
        for (String value : new String[]{left, right}) {
            if (StringUtils.isBlank(value)) {
                continue;
            }
            for (String part : value.split("\\s*" + java.util.regex.Pattern.quote(delimiter) + "\\s*")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && !merged.contains(trimmed)) {
                    merged.add(trimmed);
                }
            }
        }
        return String.join(delimiter, merged);
    }

    private int countDelimitedItems(String value, String delimiter) {
        if (StringUtils.isBlank(value)) {
            return 0;
        }
        return (int) java.util.Arrays.stream(value.split("\\s*" + java.util.regex.Pattern.quote(delimiter) + "\\s*"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .distinct()
                .count();
    }

    private int computeFusionScore(StockAlertDTO<AStockRss> dto, MacroThemeEvent macroTheme, LocalDateTime endTime) {
        int noticeScore = Math.max(0, dto.getSignalScore());
        int macroScore = safeInt(macroTheme.getSignalScore());
        int total = Math.max(noticeScore, macroScore);
        total += (int) Math.round(Math.min(noticeScore, macroScore) * 0.35);
        total += Math.min(12, dto.getEventCount() * 3);
        total += Math.min(8, Math.max(0, dto.getFrequency() - 1) * 2);

        String noticeSide = StringUtils.defaultIfBlank(dto.getSignalSide(), "中性");
        String macroSide = StringUtils.defaultIfBlank(macroTheme.getSignalSide(), "中性");
        if (noticeSide.equals(macroSide)) {
            total += 12;
        } else if ("中性".equals(noticeSide) || "中性".equals(macroSide)) {
            total += 6;
        }

        if (macroTheme.getPubDate() != null && macroTheme.getPubDate().isAfter(endTime.minusHours(6))) {
            total += 6;
        }
        return Math.min(180, total);
    }

    private String resolveResonanceSide(String noticeSide, String macroSide) {
        String normalizedNoticeSide = StringUtils.defaultIfBlank(noticeSide, "中性");
        String normalizedMacroSide = StringUtils.defaultIfBlank(macroSide, "中性");
        if (normalizedNoticeSide.equals(normalizedMacroSide)) {
            return normalizedNoticeSide;
        }
        if ("中性".equals(normalizedNoticeSide)) {
            return normalizedMacroSide;
        }
        if ("中性".equals(normalizedMacroSide)) {
            return normalizedNoticeSide;
        }
        return "中性";
    }

    private String buildRelationReason(MacroThemeEvent macroTheme, AStockRss stock) {
        String relation = StringUtils.defaultIfBlank(macroTheme.getMappedStocks(), "");
        String target = stock.getStockName() + "(" + stock.getStockCode() + ")";
        if (relation.contains(target)) {
            return "宏观主题映射命中：" + target;
        }
        if (matchesMacroEventResonance(new StockAlertDTO<>(stock, 0, safeInt(stock.getSignalScore()), safeInt(stock.getEventCount()), stock.getSignalSide()), macroTheme)) {
            return "宏观主题与公告事件类型共振";
        }
        if (matchesThemeKeywordResonance(new StockAlertDTO<>(stock, 0, safeInt(stock.getSignalScore()), safeInt(stock.getEventCount()), stock.getSignalSide()), macroTheme)) {
            return "宏观主题关键词与公告主题一致";
        }
        return "宏观主题与公告标的发生映射共振";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean matchesThemeName(String left, String right) {
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) {
            return false;
        }
        for (String part : left.split("\\s*\\|\\s*")) {
            if (right.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private void addIfAbsent(List<MacroThemeEvent> target, MacroThemeEvent candidate) {
        if (candidate == null || StringUtils.isBlank(candidate.getId())) {
            return;
        }
        boolean exists = target.stream().anyMatch(event -> candidate.getId().equals(event.getId()));
        if (!exists) {
            target.add(candidate);
        }
    }

    private boolean containsAnyKeyword(String text, List<String> keywords) {
        if (StringUtils.isBlank(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        return keywords.stream()
                .filter(StringUtils::isNotBlank)
                .map(this::normalizeText)
                .anyMatch(text::contains);
    }

    private String joinText(String... values) {
        return String.join(" ", values == null ? new String[0] : values);
    }

    private String normalizeText(String value) {
        return StringUtils.defaultString(value)
                .replace('\u00A0', ' ')
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }
}
