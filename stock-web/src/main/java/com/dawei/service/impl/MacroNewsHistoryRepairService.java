package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.MacroNewsRaw;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.ThemeWatchlist;
import com.dawei.mapper.MacroNewsRawMapper;
import com.dawei.mapper.MacroThemeEventMapper;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.service.ThemeAutoPoolService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 修复历史宏观事件数据：
 * 1. 用最新规则重新计算 theme/eventType/score
 * 2. 删除已不再满足规则的历史事件
 * 3. 按最新主题观察池重新生成映射关系
 */
@Slf4j
@Service
public class MacroNewsHistoryRepairService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MacroNewsRawMapper macroNewsRawMapper;
    private final MacroThemeEventMapper macroThemeEventMapper;
    private final MacroThemeStockRelMapper macroThemeStockRelMapper;
    private final MacroNewsSignalService macroNewsSignalService;
    private final MacroThemeRelationService macroThemeRelationService;
    private final ThemeAutoPoolService themeAutoPoolService;

    public MacroNewsHistoryRepairService(MacroNewsRawMapper macroNewsRawMapper,
                                         MacroThemeEventMapper macroThemeEventMapper,
                                         MacroThemeStockRelMapper macroThemeStockRelMapper,
                                         MacroThemeRelationService macroThemeRelationService,
                                         MacroNewsSignalService macroNewsSignalService,
                                         ThemeAutoPoolService themeAutoPoolService) {
        this.macroNewsRawMapper = macroNewsRawMapper;
        this.macroThemeEventMapper = macroThemeEventMapper;
        this.macroThemeStockRelMapper = macroThemeStockRelMapper;
        this.macroThemeRelationService = macroThemeRelationService;
        this.macroNewsSignalService = macroNewsSignalService;
        this.themeAutoPoolService = themeAutoPoolService;
    }

    public RepairSummary repairRecentEvents(int hours) {
        int effectiveHours = Math.max(1, hours);
        LocalDateTime startTime = LocalDateTime.now().minusHours(effectiveHours);
        List<MacroNewsRaw> raws = macroNewsRawMapper.selectList(new QueryWrapper<MacroNewsRaw>()
                .ge("pub_date", format(startTime))
                .orderByAsc("pub_date")
                .orderByAsc("id"));
        Map<String, List<ThemeWatchlist>> watchlistByTheme = macroThemeRelationService.loadEnabledWatchlist();
        List<MacroThemeRelationService.StockReference> explicitReferences =
                macroThemeRelationService.loadExplicitReferencePool(watchlistByTheme);
        List<ReplayContext> replayContexts = new ArrayList<>();

        RepairSummary summary = new RepairSummary(raws.size(), effectiveHours);
        for (MacroNewsRaw raw : raws) {
            if (raw == null || isBlank(raw.getSourceName()) || isBlank(raw.getNewsKey())) {
                summary.skipped++;
                continue;
            }

            MacroThemeEvent existing = macroThemeEventMapper.selectOne(new QueryWrapper<MacroThemeEvent>()
                    .eq("source_name", raw.getSourceName())
                    .eq("news_key", raw.getNewsKey())
                    .last("LIMIT 1"));

            MacroThemeEvent candidate = existing != null ? copyExisting(existing) : buildFromRaw(raw);
            Snapshot snapshot = Snapshot.capture(existing);

            candidate.setTitle(raw.getTitle());
            candidate.setSummary(summarize(raw));
            candidate.setLink(raw.getLink());
            candidate.setSourceTags(raw.getSourceTags());
            candidate.setPubDate(raw.getPubDate());

            if (!macroNewsSignalService.enrichEvent(candidate, raw)) {
                summary.filtered++;
                if (existing != null) {
                    summary.deletedRelations += macroThemeStockRelMapper.delete(new QueryWrapper<MacroThemeStockRel>()
                            .eq("theme_event_id", existing.getId()));
                    macroThemeEventMapper.deleteById(existing.getId());
                    summary.deletedEvents++;
                }
                continue;
            }

            if (existing == null) {
                candidate.setId(UUID.randomUUID().toString().replace("-", ""));
                candidate.setCreateTime(LocalDateTime.now());
                if (macroThemeEventMapper.insertIgnore(candidate) > 0) {
                    summary.insertedEvents++;
                }
            } else if (!snapshot.equalsCurrent(candidate)) {
                macroThemeEventMapper.updateById(candidate);
                summary.updatedEvents++;
            }

            summary.deletedRelations += macroThemeStockRelMapper.delete(new QueryWrapper<MacroThemeStockRel>()
                    .eq("theme_event_id", candidate.getId()));
            summary.insertedRelations += rebuildRelations(candidate, raw, watchlistByTheme, explicitReferences);
            replayContexts.add(new ReplayContext(candidate, raw));
        }
        ThemeAutoPoolService.RebuildSummary autoPoolSummary =
                themeAutoPoolService.rebuildFromRecentExplicitRelations(effectiveHours);
        summary.autoPoolInserted = autoPoolSummary.getInserted();
        summary.autoPoolEnabled = autoPoolSummary.getEnabled();
        if (summary.autoPoolEnabled > 0 && !replayContexts.isEmpty()) {
            Map<String, List<ThemeWatchlist>> refreshedWatchlistByTheme = macroThemeRelationService.loadEnabledWatchlist();
            List<MacroThemeRelationService.StockReference> refreshedExplicitReferences =
                    macroThemeRelationService.loadExplicitReferencePool(refreshedWatchlistByTheme);
            for (ReplayContext context : replayContexts) {
                summary.replayedRelations += rebuildRelations(
                        context.event(),
                        context.raw(),
                        refreshedWatchlistByTheme,
                        refreshedExplicitReferences
                );
            }
        }

        log.info("宏观历史事件修复完成: {}", summary);
        return summary;
    }

    private int rebuildRelations(MacroThemeEvent event,
                                 MacroNewsRaw raw,
                                 Map<String, List<ThemeWatchlist>> watchlistByTheme,
                                 List<MacroThemeRelationService.StockReference> explicitReferences) {
        int inserted = 0;
        for (MacroThemeStockRel rel : macroThemeRelationService.buildRelations(event, raw, watchlistByTheme, explicitReferences)) {
            inserted += macroThemeStockRelMapper.insertIgnore(rel);
        }
        return inserted;
    }

    private MacroThemeEvent buildFromRaw(MacroNewsRaw raw) {
        MacroThemeEvent event = new MacroThemeEvent();
        event.setSourceName(raw.getSourceName());
        event.setSourceType(raw.getSourceType());
        event.setNewsKey(raw.getNewsKey());
        event.setTitle(raw.getTitle());
        event.setSummary(summarize(raw));
        event.setLink(raw.getLink());
        event.setSourceTags(raw.getSourceTags());
        event.setPubDate(raw.getPubDate());
        event.setCreateTime(LocalDateTime.now());
        return event;
    }

    private MacroThemeEvent copyExisting(MacroThemeEvent existing) {
        MacroThemeEvent copy = new MacroThemeEvent();
        copy.setId(existing.getId());
        copy.setSourceName(existing.getSourceName());
        copy.setSourceType(existing.getSourceType());
        copy.setNewsKey(existing.getNewsKey());
        copy.setTitle(existing.getTitle());
        copy.setSummary(existing.getSummary());
        copy.setLink(existing.getLink());
        copy.setSourceTags(existing.getSourceTags());
        copy.setPubDate(existing.getPubDate());
        copy.setCreateTime(existing.getCreateTime());
        copy.setThemeName(existing.getThemeName());
        copy.setEventType(existing.getEventType());
        copy.setSignalSide(existing.getSignalSide());
        copy.setSignalScore(existing.getSignalScore());
        copy.setImportanceLevel(existing.getImportanceLevel());
        copy.setClusterKey(existing.getClusterKey());
        return copy;
    }

    private String summarize(MacroNewsRaw raw) {
        String content = cleanText(raw.getContent());
        if (StringUtils.isBlank(content)) {
            return cleanText(raw.getTitle());
        }
        return content.length() <= 180 ? content : content.substring(0, 180);
    }

    private String cleanText(String value) {
        return StringUtils.defaultString(value)
                .replace('\u00A0', ' ')
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String format(LocalDateTime value) {
        return value.format(DATE_TIME_FORMATTER);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static final class RepairSummary {
        private final int scanned;
        private final int hours;
        private int filtered;
        private int insertedEvents;
        private int updatedEvents;
        private int deletedEvents;
        private int deletedRelations;
        private int insertedRelations;
        private int skipped;
        private int autoPoolInserted;
        private int autoPoolEnabled;
        private int replayedRelations;

        public RepairSummary(int scanned, int hours) {
            this.scanned = scanned;
            this.hours = hours;
        }

        public int getScanned() {
            return scanned;
        }

        public int getHours() {
            return hours;
        }

        public int getFiltered() {
            return filtered;
        }

        public int getInsertedEvents() {
            return insertedEvents;
        }

        public int getUpdatedEvents() {
            return updatedEvents;
        }

        public int getDeletedEvents() {
            return deletedEvents;
        }

        public int getDeletedRelations() {
            return deletedRelations;
        }

        public int getInsertedRelations() {
            return insertedRelations;
        }

        public int getSkipped() {
            return skipped;
        }

        public int getAutoPoolInserted() {
            return autoPoolInserted;
        }

        public int getAutoPoolEnabled() {
            return autoPoolEnabled;
        }

        public int getReplayedRelations() {
            return replayedRelations;
        }

        @Override
        public String toString() {
            return "RepairSummary{" +
                    "scanned=" + scanned +
                    ", hours=" + hours +
                    ", filtered=" + filtered +
                    ", insertedEvents=" + insertedEvents +
                    ", updatedEvents=" + updatedEvents +
                    ", deletedEvents=" + deletedEvents +
                    ", deletedRelations=" + deletedRelations +
                    ", insertedRelations=" + insertedRelations +
                    ", skipped=" + skipped +
                    ", autoPoolInserted=" + autoPoolInserted +
                    ", autoPoolEnabled=" + autoPoolEnabled +
                    ", replayedRelations=" + replayedRelations +
                    '}';
        }
    }

    private record ReplayContext(MacroThemeEvent event, MacroNewsRaw raw) {
    }

    private record Snapshot(String themeName,
                            String eventType,
                            String signalSide,
                            Integer signalScore,
                            Integer importanceLevel,
                            String clusterKey,
                            String summary,
                            String link,
                            String sourceTags,
                            LocalDateTime pubDate) {
        private static Snapshot capture(MacroThemeEvent event) {
            if (event == null) {
                return new Snapshot(null, null, null, null, null, null, null, null, null, null);
            }
            return new Snapshot(
                    event.getThemeName(),
                    event.getEventType(),
                    event.getSignalSide(),
                    event.getSignalScore(),
                    event.getImportanceLevel(),
                    event.getClusterKey(),
                    event.getSummary(),
                    event.getLink(),
                    event.getSourceTags(),
                    event.getPubDate()
            );
        }

        private boolean equalsCurrent(MacroThemeEvent event) {
            return Objects.equals(themeName, event.getThemeName())
                    && Objects.equals(eventType, event.getEventType())
                    && Objects.equals(signalSide, event.getSignalSide())
                    && Objects.equals(signalScore, event.getSignalScore())
                    && Objects.equals(importanceLevel, event.getImportanceLevel())
                    && Objects.equals(clusterKey, event.getClusterKey())
                    && Objects.equals(summary, event.getSummary())
                    && Objects.equals(link, event.getLink())
                    && Objects.equals(sourceTags, event.getSourceTags())
                    && Objects.equals(pubDate, event.getPubDate());
        }
    }
}
