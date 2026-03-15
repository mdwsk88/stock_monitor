package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.AStockRss;
import com.dawei.mapper.AStockRssMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 修复历史A股公告数据：
 * 1. 删除早期误归属到转债/B股的记录
 * 2. 从链接回填 art_code
 * 3. 为旧记录回填事件类型、方向、评分和聚类键
 */
@Slf4j
@Service
public class AStockHistoryRepairService {

    private final AStockRssMapper aStockRssMapper;
    private final AStockSignalService aStockSignalService;

    public AStockHistoryRepairService(AStockRssMapper aStockRssMapper,
                                      AStockSignalService aStockSignalService) {
        this.aStockRssMapper = aStockRssMapper;
        this.aStockSignalService = aStockSignalService;
    }

    public RepairSummary repairHistoricalNotices() {
        List<AStockRss> records = aStockRssMapper.selectList(
                new QueryWrapper<AStockRss>()
                        .orderByAsc("pub_date")
                        .orderByAsc("id")
        );

        RepairSummary summary = new RepairSummary(records.size());
        for (AStockRss record : records) {
            if (record == null || isBlank(record.getId())) {
                summary.skipped++;
                continue;
            }

            if (isBlank(record.getStockCode())) {
                summary.skipped++;
                continue;
            }

            if (!aStockSignalService.isPreferredEquityCode(record.getStockCode())) {
                aStockRssMapper.deleteById(record.getId());
                summary.deletedNonEquity++;
                continue;
            }

            Snapshot snapshot = Snapshot.capture(record);

            if (isBlank(record.getArtCode())) {
                String artCode = extractArtCode(record.getLink());
                if (!isBlank(artCode)) {
                    record.setArtCode(artCode);
                    summary.backfilledArtCode++;
                }
            }

            if (isBlank(record.getStockName())) {
                String stockName = extractStockName(record.getTitle());
                if (!isBlank(stockName)) {
                    record.setStockName(stockName);
                    summary.backfilledStockName++;
                }
            }

            boolean effective = aStockSignalService.enrichNotice(record);
            if (!effective) {
                summary.markedNoise++;
            }

            if (!isBlank(record.getArtCode()) && existsDuplicate(record)) {
                aStockRssMapper.deleteById(record.getId());
                summary.deletedDuplicate++;
                continue;
            }

            if (!snapshot.equalsCurrent(record)) {
                aStockRssMapper.updateById(record);
                summary.updated++;
            }
        }

        log.info("A股历史数据修复完成: {}", summary);
        return summary;
    }

    private boolean existsDuplicate(AStockRss record) {
        return aStockRssMapper.selectCount(new QueryWrapper<AStockRss>()
                .eq("stock_code", record.getStockCode())
                .eq("art_code", record.getArtCode())
                .ne("id", record.getId())) > 0;
    }

    private String extractArtCode(String link) {
        if (isBlank(link)) {
            return null;
        }
        int slashIndex = link.lastIndexOf('/');
        int suffixIndex = link.lastIndexOf(".html");
        if (slashIndex < 0 || suffixIndex <= slashIndex + 1) {
            return null;
        }
        return link.substring(slashIndex + 1, suffixIndex).trim();
    }

    private String extractStockName(String title) {
        if (isBlank(title)) {
            return null;
        }
        int colonIndex = title.indexOf(':');
        if (colonIndex <= 0) {
            return null;
        }
        return title.substring(0, colonIndex).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static final class RepairSummary {
        private final int scanned;
        private int updated;
        private int deletedNonEquity;
        private int deletedDuplicate;
        private int backfilledArtCode;
        private int backfilledStockName;
        private int markedNoise;
        private int skipped;

        public RepairSummary(int scanned) {
            this.scanned = scanned;
        }

        public int getScanned() {
            return scanned;
        }

        public int getUpdated() {
            return updated;
        }

        public int getDeletedNonEquity() {
            return deletedNonEquity;
        }

        public int getDeletedDuplicate() {
            return deletedDuplicate;
        }

        public int getBackfilledArtCode() {
            return backfilledArtCode;
        }

        public int getBackfilledStockName() {
            return backfilledStockName;
        }

        public int getMarkedNoise() {
            return markedNoise;
        }

        public int getSkipped() {
            return skipped;
        }

        @Override
        public String toString() {
            return "RepairSummary{" +
                    "scanned=" + scanned +
                    ", updated=" + updated +
                    ", deletedNonEquity=" + deletedNonEquity +
                    ", deletedDuplicate=" + deletedDuplicate +
                    ", backfilledArtCode=" + backfilledArtCode +
                    ", backfilledStockName=" + backfilledStockName +
                    ", markedNoise=" + markedNoise +
                    ", skipped=" + skipped +
                    '}';
        }
    }

    private record Snapshot(String artCode,
                            String stockName,
                            String tag,
                            String eventType,
                            String signalSide,
                            Integer signalScore,
                            String clusterKey) {
        private static Snapshot capture(AStockRss record) {
            return new Snapshot(
                    record.getArtCode(),
                    record.getStockName(),
                    record.getTag(),
                    record.getEventType(),
                    record.getSignalSide(),
                    record.getSignalScore(),
                    record.getClusterKey()
            );
        }

        private boolean equalsCurrent(AStockRss record) {
            return Objects.equals(artCode, record.getArtCode())
                    && Objects.equals(stockName, record.getStockName())
                    && Objects.equals(tag, record.getTag())
                    && Objects.equals(eventType, record.getEventType())
                    && Objects.equals(signalSide, record.getSignalSide())
                    && Objects.equals(signalScore, record.getSignalScore())
                    && Objects.equals(clusterKey, record.getClusterKey());
        }
    }
}
