package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.MacroNewsRaw;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.entity.ThemeWatchlist;
import com.dawei.mapper.MacroNewsRawMapper;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.service.MacroNewsService;
import com.dawei.service.ThemeAutoPoolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 宏观新闻抓取与主题事件服务
 */
@Slf4j
@Service
public class MacroNewsServiceImpl implements MacroNewsService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String GOV_POLICY_URL = "https://www.gov.cn/zhengce/";
    private static final String PBC_OMO_URL = "https://www.pbc.gov.cn/zhengcehuobisi/125207/125213/125431/125475/index.html";
    private static final String STATS_RSS_URL = "https://www.stats.gov.cn/sj/zxfb/rss.xml";
    private static final String CSRC_HIGHLIGHT_URL =
            "https://www.csrc.gov.cn/searchList/a1a078ee0bc54721ab6b148884c784a8?_isAgg=true&_isJson=true&_template=index&_rangeTimeGte=&_channelName=&page=1&_pageSize=%d";
    private static final String EASTMONEY_FAST_NEWS_API =
            "https://np-weblist.eastmoney.com/comm/web/getFastNewsList?client=web&biz=web_724&fastColumn=102&sortEnd=&pageSize=%d&req_trace=macro_shadow&callback=callback";
    private static final String EASTMONEY_FAST_NEWS_PAGE_URL = "https://kuaixun.eastmoney.com/";

    private final RestTemplate restTemplate;
    private final MacroNewsRawMapper macroNewsRawMapper;
    private final MacroThemeEventMapper macroThemeEventMapper;
    private final MacroThemeStockRelMapper macroThemeStockRelMapper;
    private final MacroNewsSignalService macroNewsSignalService;
    private final MacroThemeRelationService macroThemeRelationService;
    private final ThemeAutoPoolService themeAutoPoolService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MacroNewsServiceImpl(RestTemplate restTemplate,
                                MacroNewsRawMapper macroNewsRawMapper,
                                MacroThemeEventMapper macroThemeEventMapper,
                                MacroThemeStockRelMapper macroThemeStockRelMapper,
                                MacroThemeRelationService macroThemeRelationService,
                                MacroNewsSignalService macroNewsSignalService,
                                ThemeAutoPoolService themeAutoPoolService) {
        this.restTemplate = restTemplate;
        this.macroNewsRawMapper = macroNewsRawMapper;
        this.macroThemeEventMapper = macroThemeEventMapper;
        this.macroThemeStockRelMapper = macroThemeStockRelMapper;
        this.macroThemeRelationService = macroThemeRelationService;
        this.macroNewsSignalService = macroNewsSignalService;
        this.themeAutoPoolService = themeAutoPoolService;
    }

    @Override
    public FetchSummary fetchAndSaveMacroNews() throws Exception {
        FetchSummary summary = new FetchSummary();
        Map<String, List<ThemeWatchlist>> watchlistByTheme = macroThemeRelationService.loadEnabledWatchlist();
        List<MacroThemeRelationService.StockReference> explicitReferences =
                macroThemeRelationService.loadExplicitReferencePool(watchlistByTheme);

        List<MacroSourceItem> items = new ArrayList<>();
        items.addAll(safeFetch("中国政府网", this::fetchGovPolicyItems));
        items.addAll(safeFetch("中国人民银行", this::fetchPbcOpenMarketItems));
        items.addAll(safeFetch("国家统计局", this::fetchStatsRssItems));
        items.addAll(safeFetch("中国证监会", this::fetchCsrcHighlightItems));
        items.addAll(safeFetch("东方财富快讯", this::fetchEastmoneyFastNewsItems));

        for (MacroSourceItem item : items) {
            summary.incrementScanned();

            MacroNewsRaw raw = buildRaw(item);
            if (macroNewsRawMapper.insertIgnore(raw) > 0) {
                summary.incrementSavedRaw();
            }

            MacroThemeEvent event = buildEvent(item);
            if (!macroNewsSignalService.enrichEvent(event, raw)) {
                summary.incrementFiltered();
                continue;
            }

            int inserted = macroThemeEventMapper.insertIgnore(event);
            if (inserted > 0) {
                summary.incrementSavedEvents();
            }

            MacroThemeEvent persistedEvent = inserted > 0 ? event : resolvePersistedEvent(event);
            if (persistedEvent == null || StringUtils.isBlank(persistedEvent.getId())) {
                continue;
            }

            for (MacroThemeStockRel rel : macroThemeRelationService.buildRelations(
                    persistedEvent,
                    raw,
                    watchlistByTheme,
                explicitReferences
            )) {
                if (macroThemeStockRelMapper.insertIgnore(rel) > 0) {
                    summary.incrementSavedRelations();
                    if (recordAutoCandidateIfNeeded(rel, persistedEvent, watchlistByTheme)) {
                        explicitReferences = macroThemeRelationService.loadExplicitReferencePool(watchlistByTheme);
                    }
                }
            }
        }

        log.info("宏观新闻抓取完成: {}", summary);
        return summary;
    }

    private boolean recordAutoCandidateIfNeeded(MacroThemeStockRel relation,
                                                MacroThemeEvent event,
                                                Map<String, List<ThemeWatchlist>> watchlistByTheme) {
        if (relation == null || !"EXPLICIT".equals(relation.getMatchType())) {
            return false;
        }
        ThemeAutoPoolCandidate candidate = themeAutoPoolService.recordExplicitHit(
                relation.getThemeName(),
                relation.getStockCode(),
                relation.getStockName(),
                relation.getConfidence(),
                relation.getReason(),
                event != null ? event.getPubDate() : null
        );
        return mergeAutoCandidateIntoWatchlistMap(watchlistByTheme, candidate);
    }

    private boolean mergeAutoCandidateIntoWatchlistMap(Map<String, List<ThemeWatchlist>> watchlistByTheme,
                                                       ThemeAutoPoolCandidate candidate) {
        if (candidate == null || safeInt(candidate.getEnabled()) <= 0) {
            return false;
        }
        String themeName = StringUtils.trimToNull(candidate.getThemeName());
        String stockCode = StringUtils.trimToNull(candidate.getStockCode());
        if (themeName == null || stockCode == null) {
            return false;
        }
        List<ThemeWatchlist> mappings = watchlistByTheme.computeIfAbsent(themeName, unused -> new ArrayList<>());
        boolean exists = mappings.stream()
                .anyMatch(mapping -> StringUtils.equals(stockCode, StringUtils.trimToNull(mapping.getStockCode())));
        if (exists) {
            return false;
        }

        ThemeWatchlist mapping = new ThemeWatchlist();
        mapping.setId(candidate.getId());
        mapping.setThemeName(themeName);
        mapping.setStockCode(stockCode);
        mapping.setStockName(StringUtils.trimToNull(candidate.getStockName()));
        mapping.setPriority(toPriority(candidate.getCandidateScore()));
        mapping.setEnabled(1);
        mapping.setReason(StringUtils.defaultIfBlank(candidate.getReason(), "自动候选池命中"));
        mapping.setCreateTime(candidate.getCreateTime());
        mappings.add(mapping);
        mappings.sort(Comparator
                .comparing((ThemeWatchlist item) -> safeInt(item.getPriority())).reversed()
                .thenComparing(ThemeWatchlist::getStockCode, Comparator.nullsLast(String::compareTo)));
        return true;
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

    private List<MacroSourceItem> safeFetch(String sourceName,
                                            ThrowingSupplier<List<MacroSourceItem>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("宏观源抓取失败，已跳过。source={}, reason={}", sourceName, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<MacroThemeEvent> getShadowThemeEvents(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        int effectiveLimit = Math.max(1, limit);
        QueryWrapper<MacroThemeEvent> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("pub_date", format(startTime));
        queryWrapper.le("pub_date", format(endTime));
        queryWrapper.orderByDesc("pub_date");

        List<MacroThemeEvent> events = macroThemeEventMapper.selectList(queryWrapper)
                .stream()
                .filter(Objects::nonNull)
                .filter(event -> event.getSignalScore() != null
                        && macroNewsSignalService.meetsShadowThreshold(event.getSignalScore()))
                .toList();
        if (events.isEmpty()) {
            return List.of();
        }

        Map<String, List<MacroThemeEvent>> grouped = events.stream()
                .collect(Collectors.groupingBy(event -> StringUtils.defaultIfBlank(event.getClusterKey(), event.getId()),
                        LinkedHashMap::new, Collectors.toList()));

        Set<String> eventIds = events.stream()
                .map(MacroThemeEvent::getId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, List<MacroThemeStockRel>> relByEventId = loadRelationsByEventId(eventIds);

        return grouped.values().stream()
                .map(group -> mergeCluster(group, relByEventId))
                .sorted(Comparator
                        .comparing((MacroThemeEvent event) -> safeInt(event.getSignalScore())).reversed()
                        .thenComparing(event -> safeInt(event.getClusterEventCount()), Comparator.reverseOrder())
                        .thenComparing(MacroThemeEvent::getPubDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(effectiveLimit)
                .toList();
    }

    private MacroThemeEvent mergeCluster(List<MacroThemeEvent> group,
                                         Map<String, List<MacroThemeStockRel>> relByEventId) {
        MacroThemeEvent representative = group.stream()
                .max(Comparator
                        .comparing((MacroThemeEvent event) -> safeInt(event.getSignalScore()))
                        .thenComparing(MacroThemeEvent::getPubDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::copyEvent)
                .orElse(null);
        if (representative == null) {
            return null;
        }

        representative.setClusterEventCount(group.size());

        List<MacroThemeStockRel> relations = group.stream()
                .map(MacroThemeEvent::getId)
                .filter(StringUtils::isNotBlank)
                .map(relByEventId::get)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .toList();

        Set<String> stocks = relations.stream()
                .map(rel -> rel.getStockName() + "(" + rel.getStockCode() + ")")
                .collect(Collectors.toCollection(LinkedHashSet::new));
        representative.setMappedStockCount(stocks.size());
        representative.setMappedStocks(String.join("、", stocks));
        return representative;
    }

    private MacroThemeEvent copyEvent(MacroThemeEvent source) {
        MacroThemeEvent target = new MacroThemeEvent();
        target.setId(source.getId());
        target.setSourceName(source.getSourceName());
        target.setSourceType(source.getSourceType());
        target.setNewsKey(source.getNewsKey());
        target.setTitle(source.getTitle());
        target.setSummary(source.getSummary());
        target.setLink(source.getLink());
        target.setSourceTags(source.getSourceTags());
        target.setPubDate(source.getPubDate());
        target.setCreateTime(source.getCreateTime());
        target.setThemeName(source.getThemeName());
        target.setEventType(source.getEventType());
        target.setSignalSide(source.getSignalSide());
        target.setSignalScore(source.getSignalScore());
        target.setImportanceLevel(source.getImportanceLevel());
        target.setClusterKey(source.getClusterKey());
        return target;
    }

    private Map<String, List<MacroThemeStockRel>> loadRelationsByEventId(Set<String> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        List<MacroThemeStockRel> relations = macroThemeStockRelMapper.selectList(new QueryWrapper<MacroThemeStockRel>()
                .in("theme_event_id", eventIds));
        return relations.stream().collect(Collectors.groupingBy(MacroThemeStockRel::getThemeEventId));
    }

    private MacroThemeEvent resolvePersistedEvent(MacroThemeEvent event) {
        return macroThemeEventMapper.selectOne(new QueryWrapper<MacroThemeEvent>()
                .eq("source_name", event.getSourceName())
                .eq("news_key", event.getNewsKey())
                .last("LIMIT 1"));
    }

    private MacroNewsRaw buildRaw(MacroSourceItem item) {
        MacroNewsRaw raw = new MacroNewsRaw();
        raw.setId(UUID.randomUUID().toString().replace("-", ""));
        raw.setSourceName(item.sourceName());
        raw.setSourceType(item.sourceType());
        raw.setNewsKey(item.newsKey());
        raw.setTitle(item.title());
        raw.setContent(item.content());
        raw.setLink(item.link());
        raw.setSourceTags(item.tags());
        raw.setPubDate(item.pubDate());
        raw.setCreateTime(LocalDateTime.now());
        return raw;
    }

    private MacroThemeEvent buildEvent(MacroSourceItem item) {
        MacroThemeEvent event = new MacroThemeEvent();
        event.setId(UUID.randomUUID().toString().replace("-", ""));
        event.setSourceName(item.sourceName());
        event.setSourceType(item.sourceType());
        event.setNewsKey(item.newsKey());
        event.setTitle(item.title());
        event.setSummary(summarize(item));
        event.setLink(item.link());
        event.setSourceTags(item.tags());
        event.setPubDate(item.pubDate());
        event.setCreateTime(LocalDateTime.now());
        return event;
    }

    private String summarize(MacroSourceItem item) {
        String content = cleanText(item.content());
        if (StringUtils.isBlank(content)) {
            return item.title();
        }
        if (content.length() <= 180) {
            return content;
        }
        return content.substring(0, 180);
    }

    private List<MacroSourceItem> fetchGovPolicyItems() {
        Document document = Jsoup.parse(fetchBody(GOV_POLICY_URL), GOV_POLICY_URL);
        Elements items = document.select(".item03 .list li");
        List<MacroSourceItem> result = new ArrayList<>();
        int limit = macroNewsSignalService.getFetchLimitPerSource();
        for (Element item : items) {
            if (result.size() >= limit) {
                break;
            }
            Element anchor = item.selectFirst("a[href]");
            Element date = item.selectFirst("span");
            if (anchor == null || date == null) {
                continue;
            }
            result.add(new MacroSourceItem(
                    "中国政府网",
                    "OFFICIAL",
                    normalizeNewsKey(anchor.attr("href")),
                    cleanText(anchor.text()),
                    cleanText(anchor.text()),
                    resolveUrl(GOV_POLICY_URL, anchor.attr("href")),
                    parseDateStart(date.text()),
                    "最新政策"
            ));
        }
        return result;
    }

    private List<MacroSourceItem> fetchPbcOpenMarketItems() {
        Document document = Jsoup.parse(fetchBody(PBC_OMO_URL), PBC_OMO_URL);
        Elements anchors = document.select("a[istitle=true]");
        List<MacroSourceItem> result = new ArrayList<>();
        int limit = macroNewsSignalService.getFetchLimitPerSource();
        for (Element anchor : anchors) {
            if (result.size() >= limit) {
                break;
            }
            Element container = anchor.closest("td");
            Element date = container != null ? container.selectFirst("span.hui12") : null;
            if (date == null) {
                continue;
            }
            result.add(new MacroSourceItem(
                    "中国人民银行",
                    "OFFICIAL",
                    normalizeNewsKey(anchor.attr("href")),
                    cleanText(anchor.text()),
                    cleanText(anchor.text()),
                    resolveUrl(PBC_OMO_URL, anchor.attr("href")),
                    parseDateStart(date.text()),
                    "公开市场业务"
            ));
        }
        return result;
    }

    private List<MacroSourceItem> fetchStatsRssItems() {
        Document document = Jsoup.parse(fetchBody(STATS_RSS_URL), "", Parser.xmlParser());
        Elements items = document.select("channel > item");
        List<MacroSourceItem> result = new ArrayList<>();
        int limit = macroNewsSignalService.getFetchLimitPerSource();
        for (Element item : items) {
            if (result.size() >= limit) {
                break;
            }
            result.add(new MacroSourceItem(
                    "国家统计局",
                    "OFFICIAL",
                    normalizeNewsKey(textOf(item, "docId", textOf(item, "link", ""))),
                    cleanText(textOf(item, "title", "")),
                    cleanText(textOf(item, "description", "")),
                    cleanText(textOf(item, "link", "")),
                    parseDateTime(textOf(item, "pubDate", "1970-01-01 00:00:00")),
                    cleanText(textOf(item, "channel", "数据发布"))
            ));
        }
        return result;
    }

    private List<MacroSourceItem> fetchCsrcHighlightItems() throws Exception {
        String url = CSRC_HIGHLIGHT_URL.formatted(macroNewsSignalService.getFetchLimitPerSource());
        JsonNode root = objectMapper.readTree(fetchBody(url));
        JsonNode results = root.path("data").path("results");
        List<MacroSourceItem> items = new ArrayList<>();
        if (!results.isArray()) {
            return items;
        }
        for (JsonNode node : results) {
            String key = text(node, "manuscriptId");
            String title = text(node, "title");
            if (StringUtils.isBlank(key) || StringUtils.isBlank(title)) {
                continue;
            }
            items.add(new MacroSourceItem(
                    "中国证监会",
                    "OFFICIAL",
                    normalizeNewsKey(key),
                    cleanText(title),
                    cleanText(text(node, "content")),
                    resolveUrl("https://www.csrc.gov.cn/", text(node, "url")),
                    parsePublishedMillis(node.path("publishedTime").asLong()),
                    cleanText(text(node, "channelName"))
            ));
        }
        return items;
    }

    private List<MacroSourceItem> fetchEastmoneyFastNewsItems() throws Exception {
        String url = EASTMONEY_FAST_NEWS_API.formatted(macroNewsSignalService.getFetchLimitPerSource());
        JsonNode root = objectMapper.readTree(unwrapJsonp(fetchBody(url), "callback"));
        JsonNode fastNewsList = root.path("data").path("fastNewsList");
        if (!fastNewsList.isArray()) {
            return List.of();
        }
        List<MacroSourceItem> items = new ArrayList<>();
        int limit = macroNewsSignalService.getFetchLimitPerSource();
        for (JsonNode node : fastNewsList) {
            if (items.size() >= limit) {
                break;
            }
            String code = text(node, "code");
            String title = cleanText(text(node, "title"));
            String summary = cleanText(text(node, "summary"));
            if (StringUtils.isBlank(code) || StringUtils.isBlank(title)) {
                continue;
            }
            items.add(new MacroSourceItem(
                    "东方财富快讯",
                    "QUICK",
                    normalizeNewsKey(code),
                    title,
                    StringUtils.defaultIfBlank(summary, title),
                    EASTMONEY_FAST_NEWS_PAGE_URL,
                    parseDateTime(text(node, "showTime")),
                    ""
            ));
        }
        return items;
    }

    private String unwrapJsonp(String body, String callbackName) {
        String prefix = callbackName + "(";
        if (StringUtils.startsWith(body, prefix) && StringUtils.endsWith(body.trim(), ")")) {
            return StringUtils.removeEnd(StringUtils.removeStart(body.trim(), prefix), ")");
        }
        return body;
    }

    private String fetchBody(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0");
        headers.set("Accept", "*/*");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return StringUtils.defaultString(response.getBody());
        } catch (ResourceAccessException e) {
            if (!shouldRetryWithoutTlsValidation(url, e)) {
                throw e;
            }
            log.warn("宏观源 TLS 校验失败，使用兼容模式重试。url={}", url);
            return fetchBodyWithoutTlsValidation(url);
        }
    }

    private boolean shouldRetryWithoutTlsValidation(String url, ResourceAccessException e) {
        return StringUtils.startsWithIgnoreCase(url, "https://")
                && StringUtils.containsIgnoreCase(e.getMessage(), "PKIX path building failed");
    }

    private String fetchBodyWithoutTlsValidation(String url) {
        try {
            URLConnection connection = new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Encoding", "gzip");

            if (connection instanceof HttpsURLConnection httpsURLConnection) {
                httpsURLConnection.setSSLSocketFactory(buildUnsafeSslSocketFactory());
                httpsURLConnection.setHostnameVerifier(new TrustAllHostnameVerifier());
            }

            try (InputStream rawStream = connection.getInputStream();
                 InputStream bodyStream = isGzip(connection) ? new GZIPInputStream(rawStream) : rawStream) {
                return new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            throw new RuntimeException("兼容模式抓取失败: " + ex.getMessage(), ex);
        }
    }

    private boolean isGzip(URLConnection connection) {
        return StringUtils.containsIgnoreCase(connection.getHeaderField("Content-Encoding"), "gzip");
    }

    private SSLSocketFactory buildUnsafeSslSocketFactory() {
        try {
            TrustManager[] trustManagers = new TrustManager[]{new TrustAllX509TrustManager()};
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("初始化兼容 TLS 失败: " + e.getMessage(), e);
        }
    }

    private String normalizeNewsKey(String value) {
        return StringUtils.defaultString(value).trim();
    }

    private String resolveUrl(String baseUrl, String rawUrl) {
        if (StringUtils.isBlank(rawUrl)) {
            return baseUrl;
        }
        if (rawUrl.startsWith("//")) {
            return "https:" + rawUrl;
        }
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl;
        }
        return URI.create(baseUrl).resolve(rawUrl).toString();
    }

    private LocalDateTime parseDateStart(String dateText) {
        return LocalDate.parse(dateText.trim()).atTime(LocalTime.of(9, 0));
    }

    private LocalDateTime parseDateTime(String dateTimeText) {
        return LocalDateTime.parse(dateTimeText.trim(), DATE_TIME_FORMATTER);
    }

    private LocalDateTime parsePublishedMillis(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(SHANGHAI).toLocalDateTime();
    }

    private String cleanText(String value) {
        return StringUtils.defaultString(value)
                .replace('\u00A0', ' ')
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private String textOf(Element root, String cssQuery, String defaultValue) {
        Element element = root.selectFirst(cssQuery);
        return element == null ? defaultValue : element.text();
    }

    private String format(LocalDateTime value) {
        return value.format(DATE_TIME_FORMATTER);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record MacroSourceItem(String sourceName,
                                   String sourceType,
                                   String newsKey,
                                   String title,
                                   String content,
                                   String link,
                                   LocalDateTime pubDate,
                                   String tags) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class TrustAllX509TrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private static final class TrustAllHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}
