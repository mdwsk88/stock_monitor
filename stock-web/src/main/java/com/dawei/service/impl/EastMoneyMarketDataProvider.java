package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.config.StockFilterConfig;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.entity.ThemeWatchlist;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.mapper.ThemeAutoPoolCandidateMapper;
import com.dawei.mapper.ThemeWatchlistMapper;
import com.dawei.service.MarketDataProvider;
import com.dawei.utils.AStockMarketClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 东方财富市场快照抓取。
 */
@Slf4j
@Service
public class EastMoneyMarketDataProvider implements MarketDataProvider {

    private static final String INDEX_QUOTE_URL =
            "https://push2.eastmoney.com/api/qt/ulist.np/get"
                    + "?secids=1.000001,0.399001,0.399006"
                    + "&fields=f12,f14,f2,f3"
                    + "&fltt=2&invt=2&ut=fa5fd1943c7b386f172d6893dbfba10b";
    private static final String BREADTH_URL_TEMPLATE =
            "https://push2.eastmoney.com/api/qt/clist/get"
                    + "?pn=1&pz=%d&po=1&np=1&fltt=2&invt=2&fid=f3"
                    + "&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23"
                    + "&fields=f3&ut=fa5fd1943c7b386f172d6893dbfba10b";
    private static final String TENCENT_INDEX_QUOTE_URL =
            "https://qt.gtimg.cn/q=s_sh000001,s_sz399001,s_sz399006";
    private static final String TENCENT_SHORT_QUOTE_URL_TEMPLATE =
            "https://qt.gtimg.cn/q=%s";

    private final RestTemplate restTemplate;
    private final StockFilterConfig filterConfig;
    private final AStockRssMapper aStockRssMapper;
    private final ThemeWatchlistMapper themeWatchlistMapper;
    private final ThemeAutoPoolCandidateMapper themeAutoPoolCandidateMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile LocalDate breadthSampleCacheDate;
    private volatile List<String> breadthSampleSymbols = List.of();

    @Autowired
    public EastMoneyMarketDataProvider(RestTemplate restTemplate,
                                       StockFilterConfig filterConfig,
                                       AStockRssMapper aStockRssMapper,
                                       ThemeWatchlistMapper themeWatchlistMapper,
                                       ThemeAutoPoolCandidateMapper themeAutoPoolCandidateMapper) {
        this.restTemplate = restTemplate;
        this.filterConfig = filterConfig;
        this.aStockRssMapper = aStockRssMapper;
        this.themeWatchlistMapper = themeWatchlistMapper;
        this.themeAutoPoolCandidateMapper = themeAutoPoolCandidateMapper;
    }

    EastMoneyMarketDataProvider(RestTemplate restTemplate,
                                StockFilterConfig filterConfig) {
        this(restTemplate, filterConfig, null, null, null);
    }

    @Override
    public MarketSnapshot fetchSnapshot() {
        MarketSnapshot snapshot = MarketSnapshot.neutral(LocalDateTime.now(), "EASTMONEY");

        boolean indexLoaded = loadIndexFromEastMoney(snapshot);
        if (!indexLoaded) {
            indexLoaded = loadIndexFromTencent(snapshot);
        }

        boolean breadthLoaded = loadBreadthFromEastMoney(snapshot);
        if (!breadthLoaded) {
            breadthLoaded = loadBreadthFromTencentSample(snapshot);
        }

        if (!indexLoaded) {
            throw new IllegalStateException("抓取市场快照失败：核心指数数据不可用");
        }
        if (!breadthLoaded) {
            appendSourceSuffix(snapshot, "NO_BREADTH");
            log.info("市场宽度抓取失败，当前退化为指数快照。source={}", snapshot.getSource());
        }
        return snapshot;
    }

    private boolean loadIndexFromEastMoney(MarketSnapshot snapshot) {
        try {
            JsonNode indexRoot = fetchJson(INDEX_QUOTE_URL);
            parseIndexDiff(snapshot, indexRoot.path("data").path("diff"));
            if (hasAnyIndexData(snapshot)) {
                snapshot.setSource("EASTMONEY");
                return true;
            }
        } catch (Exception ex) {
            logPrimaryFailure("东方财富指数快照不可用，准备切换兜底源", ex);
        }
        return false;
    }

    private boolean loadIndexFromTencent(MarketSnapshot snapshot) {
        try {
            parseTencentShortQuote(snapshot, fetchText(TENCENT_INDEX_QUOTE_URL), true);
            if (hasAnyIndexData(snapshot)) {
                snapshot.setSource("TENCENT_QUOTE");
                log.info("市场指数快照已切换至腾讯行情兜底源");
                return true;
            }
        } catch (Exception ex) {
            log.warn("腾讯指数快照兜底失败，reason={}", ex.getMessage());
        }
        return false;
    }

    private boolean loadBreadthFromEastMoney(MarketSnapshot snapshot) {
        try {
            JsonNode breadthRoot = fetchJson(BREADTH_URL_TEMPLATE.formatted(filterConfig.getMarketBreadthFetchLimit()));
            parseBreadth(snapshot, breadthRoot.path("data").path("diff"));
            return snapshot.hasBreadthData();
        } catch (Exception ex) {
            logPrimaryFailure("东方财富市场宽度不可用，准备切换样本宽度代理", ex);
            return false;
        }
    }

    private boolean loadBreadthFromTencentSample(MarketSnapshot snapshot) {
        List<String> symbols = loadBreadthSampleSymbols();
        if (symbols.isEmpty()) {
            return false;
        }
        int batchSize = Math.max(10, filterConfig.getMarketQuoteBatchSize());
        int upCount = 0;
        int downCount = 0;
        int flatCount = 0;
        int limitUpCount = 0;
        int limitDownCount = 0;
        int processed = 0;

        for (int start = 0; start < symbols.size(); start += batchSize) {
            int end = Math.min(symbols.size(), start + batchSize);
            String url = TENCENT_SHORT_QUOTE_URL_TEMPLATE.formatted(String.join(",", symbols.subList(start, end)));
            try {
                String content = fetchText(url);
                for (TencentQuote quote : parseTencentQuotes(content, false)) {
                    processed++;
                    if (quote.changePct() > 0.0d) {
                        upCount++;
                    } else if (quote.changePct() < 0.0d) {
                        downCount++;
                    } else {
                        flatCount++;
                    }
                    if (quote.changePct() >= 9.5d) {
                        limitUpCount++;
                    }
                    if (quote.changePct() <= -9.5d) {
                        limitDownCount++;
                    }
                }
            } catch (Exception ex) {
                log.warn("腾讯样本宽度抓取失败，batchStart={}, batchEnd={}, reason={}",
                        start, end, ex.getMessage());
            }
        }

        if (processed <= 0) {
            return false;
        }
        snapshot.setUpCount(upCount);
        snapshot.setDownCount(downCount);
        snapshot.setFlatCount(flatCount);
        snapshot.setLimitUpCount(limitUpCount);
        snapshot.setLimitDownCount(limitDownCount);
        snapshot.setBreadthSampleSize(processed);
        appendSourceSuffix(snapshot, "SAMPLE_BREADTH");
        log.info("市场宽度已切换到腾讯样本代理，sampleSize={}, up={}, down={}, flat={}",
                processed, upCount, downCount, flatCount);
        return true;
    }

    private void parseIndexDiff(MarketSnapshot snapshot, JsonNode diffNode) {
        if (snapshot == null || diffNode == null || !diffNode.isArray()) {
            return;
        }
        for (JsonNode node : diffNode) {
            String code = node.path("f12").asText("");
            double changePct = node.path("f3").asDouble(0.0d);
            switch (code) {
                case "000001" -> snapshot.setShChangePct(changePct);
                case "399001" -> snapshot.setSzChangePct(changePct);
                case "399006" -> snapshot.setCybChangePct(changePct);
                default -> {
                }
            }
        }
    }

    private void parseBreadth(MarketSnapshot snapshot, JsonNode diffNode) {
        if (snapshot == null || diffNode == null || !diffNode.isArray()) {
            return;
        }
        int upCount = 0;
        int downCount = 0;
        int flatCount = 0;
        int limitUpCount = 0;
        int limitDownCount = 0;
        for (JsonNode node : diffNode) {
            if (!node.has("f3") || node.path("f3").isNull()) {
                continue;
            }
            double changePct = node.path("f3").asDouble(0.0d);
            if (changePct > 0.0d) {
                upCount++;
            } else if (changePct < 0.0d) {
                downCount++;
            } else {
                flatCount++;
            }
            if (changePct >= 9.5d) {
                limitUpCount++;
            }
            if (changePct <= -9.5d) {
                limitDownCount++;
            }
        }
        snapshot.setUpCount(upCount);
        snapshot.setDownCount(downCount);
        snapshot.setFlatCount(flatCount);
        snapshot.setLimitUpCount(limitUpCount);
        snapshot.setLimitDownCount(limitDownCount);
        snapshot.setBreadthSampleSize(0);
    }

    private void parseTencentShortQuote(MarketSnapshot snapshot, String content, boolean indexQuote) {
        for (TencentQuote quote : parseTencentQuotes(content, indexQuote)) {
            if (indexQuote) {
                switch (quote.code()) {
                    case "000001" -> snapshot.setShChangePct(quote.changePct());
                    case "399001" -> snapshot.setSzChangePct(quote.changePct());
                    case "399006" -> snapshot.setCybChangePct(quote.changePct());
                    default -> {
                    }
                }
            }
        }
    }

    private List<TencentQuote> parseTencentQuotes(String content, boolean indexQuote) {
        List<TencentQuote> quotes = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return quotes;
        }
        for (String line : content.split(";")) {
            if (line == null || line.isBlank() || !line.contains("\"")) {
                continue;
            }
            int firstQuote = line.indexOf('"');
            int lastQuote = line.lastIndexOf('"');
            if (firstQuote < 0 || lastQuote <= firstQuote) {
                continue;
            }
            String payload = line.substring(firstQuote + 1, lastQuote);
            String[] fields = payload.split("~");
            if (fields.length < 6) {
                continue;
            }
            String code = fields[2];
            double changePct = parseDouble(fields[5]);
            if (!indexQuote && (code == null || code.isBlank())) {
                continue;
            }
            quotes.add(new TencentQuote(code, changePct));
        }
        return quotes;
    }

    private boolean hasAnyIndexData(MarketSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return snapshot.getShChangePct() != 0.0d
                || snapshot.getSzChangePct() != 0.0d
                || snapshot.getCybChangePct() != 0.0d;
    }

    private JsonNode fetchJson(String url) {
        try {
            return objectMapper.readTree(fetchText(url));
        } catch (Exception ex) {
            throw new IllegalStateException("解析市场快照失败", ex);
        }
    }

    private String fetchText(String url) {
        int attempts = Math.max(1, filterConfig.getMarketSnapshotRetryAttempts());
        Exception lastException = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Mozilla/5.0");
                headers.set("Accept", "application/json,text/plain,*/*");
                headers.set("Connection", "close");
                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );
                return response.getBody();
            } catch (Exception ex) {
                lastException = ex;
                if (attempt < attempts) {
                    log.debug("抓取市场快照失败，准备重试，第{}/{}次，url={}, reason={}",
                            attempt, attempts, url, ex.getMessage());
                    backoff();
                    continue;
                }
                LocalDateTime now = LocalDateTime.now();
                if (AStockMarketClock.isMarketAttentionWindow(now)) {
                    log.warn("抓取市场快照失败，已重试{}次，url={}, reason={}", attempts, url, ex.getMessage());
                } else {
                    log.debug("抓取市场快照失败，已重试{}次，当前非交易关注时段，url={}, reason={}",
                            attempts, url, ex.getMessage());
                }
            }
        }
        throw new IllegalStateException("抓取市场快照失败", lastException);
    }

    private void backoff() {
        long backoffMillis = Math.max(0L, filterConfig.getMarketSnapshotRetryBackoffMillis());
        if (backoffMillis <= 0L) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void logPrimaryFailure(String message, Exception ex) {
        if (AStockMarketClock.isMarketAttentionWindow(LocalDateTime.now())) {
            log.warn("{}，reason={}", message, ex.getMessage());
        } else {
            log.info("{}，reason={}", message, ex.getMessage());
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ex) {
            return 0.0d;
        }
    }

    private void appendSourceSuffix(MarketSnapshot snapshot, String suffix) {
        if (snapshot == null || suffix == null || suffix.isBlank()) {
            return;
        }
        String source = snapshot.getSource();
        if (source == null || source.isBlank()) {
            snapshot.setSource(suffix);
            return;
        }
        if (!source.contains(suffix)) {
            snapshot.setSource(source + "+" + suffix);
        }
    }

    private List<String> loadBreadthSampleSymbols() {
        if (aStockRssMapper == null || themeWatchlistMapper == null || themeAutoPoolCandidateMapper == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        List<String> cached = breadthSampleSymbols;
        if (breadthSampleCacheDate != null && breadthSampleCacheDate.equals(today) && !cached.isEmpty()) {
            return cached;
        }
        synchronized (this) {
            if (breadthSampleCacheDate != null && breadthSampleCacheDate.equals(today) && !breadthSampleSymbols.isEmpty()) {
                return breadthSampleSymbols;
            }
            List<String> rebuilt = buildBreadthSampleSymbols();
            breadthSampleSymbols = List.copyOf(rebuilt);
            breadthSampleCacheDate = today;
            return breadthSampleSymbols;
        }
    }

    private List<String> buildBreadthSampleSymbols() {
        int sampleSize = Math.max(100, filterConfig.getMarketBreadthSampleSize());
        LinkedHashSet<String> symbols = new LinkedHashSet<>(sampleSize * 2);

        for (ThemeWatchlist item : themeWatchlistMapper.selectList(new QueryWrapper<ThemeWatchlist>()
                .select("stock_code")
                .eq("enabled", 1)
                .isNotNull("stock_code")
                .orderByDesc("priority")
                .last("LIMIT " + sampleSize))) {
            addTencentSymbol(symbols, item.getStockCode());
        }
        for (ThemeAutoPoolCandidate item : themeAutoPoolCandidateMapper.selectList(new QueryWrapper<ThemeAutoPoolCandidate>()
                .select("stock_code")
                .eq("enabled", 1)
                .isNotNull("stock_code")
                .orderByDesc("candidate_score")
                .last("LIMIT " + sampleSize))) {
            addTencentSymbol(symbols, item.getStockCode());
        }

        LocalDateTime lookbackStart = LocalDateTime.now().minusDays(Math.max(30, filterConfig.getMarketBreadthSampleLookbackDays()));
        List<Map<String, Object>> recentRows = aStockRssMapper.selectMaps(new QueryWrapper<AStockRss>()
                .select("stock_code AS stockCode", "MAX(pub_date) AS lastPub")
                .ge("pub_date", lookbackStart)
                .isNotNull("stock_code")
                .ne("stock_code", "")
                .groupBy("stock_code")
                .orderByDesc("lastPub")
                .last("LIMIT " + Math.max(sampleSize * 3, sampleSize + 200)));
        for (Map<String, Object> row : recentRows) {
            addTencentSymbol(symbols, row.get("stockCode"));
            if (symbols.size() >= sampleSize) {
                break;
            }
        }
        return new ArrayList<>(symbols).subList(0, Math.min(sampleSize, symbols.size()));
    }

    private void addTencentSymbol(LinkedHashSet<String> symbols, Object stockCodeObject) {
        if (symbols == null || stockCodeObject == null) {
            return;
        }
        String stockCode = String.valueOf(stockCodeObject).trim();
        String symbol = toTencentShortSymbol(stockCode);
        if (symbol != null) {
            symbols.add(symbol);
        }
    }

    private String toTencentShortSymbol(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return null;
        }
        String code = stockCode.trim();
        if (code.length() != 6) {
            return null;
        }
        char first = code.charAt(0);
        if (first == '5' || first == '6' || first == '9') {
            return "s_sh" + code;
        }
        if (first == '0' || first == '1' || first == '2' || first == '3') {
            return "s_sz" + code;
        }
        if (first == '4' || first == '8') {
            return "s_bj" + code;
        }
        return null;
    }

    private record TencentQuote(String code, double changePct) {
    }
}
