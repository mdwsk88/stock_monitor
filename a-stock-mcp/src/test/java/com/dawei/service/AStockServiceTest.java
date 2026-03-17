package com.dawei.service;

import com.dawei.entity.AStockRss;
import com.dawei.entity.StockCounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AStockServiceTest {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private AStockService aStockService;

    @Test
    void queryStockShouldFilterNoiseAndApplyLimit() {
        List<AStockRss> result = aStockService.queryStock("600519");

        assertNotNull(result);
        assertEquals(8, result.size(), "默认只应返回 8 条高价值公告");
        assertTrue(result.stream().allMatch(stock -> stock.getSignalScore() >= 60), "应过滤低分噪音公告");
        assertTrue(result.stream().allMatch(stock -> "600519".equals(stock.getStockCode())));
        assertTrue(result.stream().noneMatch(stock -> stock.getTitle().contains("营业执照")));
        assertTrue(result.stream().noneMatch(stock -> stock.getTitle().contains("董事会会议通知")));
        assertEquals(95, result.get(0).getSignalScore());
        assertSortedByScoreThenDate(result);
    }

    @Test
    void queryStockByNameShouldSupportFuzzyMatchWithSameGuards() {
        List<AStockRss> result = aStockService.queryStockByName("茅台");

        assertNotNull(result);
        assertEquals(8, result.size(), "模糊查询也应继承默认 limit");
        assertTrue(result.stream().allMatch(stock -> stock.getStockName().contains("茅台")));
        assertTrue(result.stream().allMatch(stock -> stock.getSignalScore() >= 60));
        assertSortedByScoreThenDate(result);
    }

    @Test
    void queryStockBetweenDateShouldRespectDateWindowAndScoreFilter() {
        LocalDateTime now = LocalDateTime.now();
        String startDate = format(now.minusDays(6).minusHours(12));
        String endDate = format(now.plusHours(1));

        List<AStockRss> result = aStockService.queryStockBetweenDate("600519", startDate, endDate);

        assertNotNull(result);
        assertEquals(8, result.size(), "指定日期范围内应只返回窗口内的高价值公告");
        assertTrue(result.stream().allMatch(stock -> stock.getSignalScore() >= 60));
        assertTrue(result.stream().allMatch(stock -> !stock.getPubDate().isBefore(LocalDateTime.parse(startDate, DATE_TIME_FORMATTER))));
        assertTrue(result.stream().allMatch(stock -> !stock.getPubDate().isAfter(LocalDateTime.parse(endDate, DATE_TIME_FORMATTER))));
        assertSortedByScoreThenDate(result);
    }

    @Test
    void queryStockCountsBetweenDateShouldCountOnlyHighValueNotices() {
        String startDate = format(LocalDateTime.now().minusDays(15));
        String endDate = format(LocalDateTime.now().plusHours(1));

        List<StockCounts> result = aStockService.queryStockCountsBetweenDate(3, startDate, endDate);

        assertNotNull(result);
        assertEquals(2, result.size(), "应只返回高价值公告次数达到阈值的股票");

        Map<String, StockCounts> countsByCode = result.stream()
                .collect(Collectors.toMap(StockCounts::getStockCode, Function.identity()));

        assertEquals(11, countsByCode.get("600519").getOccurCounts());
        assertEquals(3, countsByCode.get("000001").getOccurCounts());
        assertFalse(countsByCode.containsKey("001696"), "低分噪音不应抬高出现次数");
    }

    @Test
    void queryStockByTitleKeywordsShouldReturnOnlyHighValueKeywordMatches() {
        List<AStockRss> result = aStockService.queryStockByTitleKeywords(List.of("回购", "减持"));

        assertNotNull(result);
        assertEquals(4, result.size(), "应命中 4 条高价值关键词公告");
        assertTrue(result.stream().allMatch(stock -> stock.getSignalScore() >= 60));
        assertTrue(result.stream().allMatch(stock ->
                stock.getTitle().contains("回购") || stock.getTitle().contains("减持")));
        assertSortedByScoreThenDate(result);
    }

    @Test
    void queryStockByNameKeywordsShouldApplyLimitAcrossMultipleStocks() {
        List<AStockRss> result = aStockService.queryStockByNameKeywords(List.of("茅台", "平安", "万丰"));

        assertNotNull(result);
        assertEquals(8, result.size(), "多标的查询也应限制返回规模");
        assertTrue(result.stream().allMatch(stock -> stock.getSignalScore() >= 60));
        assertTrue(result.stream().allMatch(stock ->
                stock.getStockName().contains("茅台")
                        || stock.getStockName().contains("平安")
                        || stock.getStockName().contains("万丰")));
        assertEquals(95, result.get(0).getSignalScore());
        assertSortedByScoreThenDate(result);
    }

    @Test
    void queryStockShouldReturnEmptyListForUnknownCode() {
        List<AStockRss> result = aStockService.queryStock("999999");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    private String format(LocalDateTime dateTime) {
        return DATE_TIME_FORMATTER.format(dateTime);
    }

    private void assertSortedByScoreThenDate(List<AStockRss> notices) {
        for (int i = 1; i < notices.size(); i++) {
            AStockRss previous = notices.get(i - 1);
            AStockRss current = notices.get(i);
            assertTrue(previous.getSignalScore() >= current.getSignalScore(),
                    "结果应按 signalScore 倒序排序");
            if (previous.getSignalScore().equals(current.getSignalScore())) {
                assertTrue(!previous.getPubDate().isBefore(current.getPubDate()),
                        "同分结果应按 pubDate 倒序排序");
            }
        }
    }
}
