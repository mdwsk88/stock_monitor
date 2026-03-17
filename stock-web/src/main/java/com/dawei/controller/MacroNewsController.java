package com.dawei.controller;

import com.dawei.entity.AReportFusionContext;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.entity.ThemeWatchlist;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.service.AReportFusionService;
import com.dawei.service.AISummaryService;
import com.dawei.service.MacroNewsService;
import com.dawei.service.ThemeAutoPoolService;
import com.dawei.service.ThemeWatchlistService;
import com.dawei.service.impl.MacroNewsHistoryRepairService;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 宏观主题影子榜接口
 */
@RestController
@RequestMapping("/api/macro-news")
public class MacroNewsController {

    private final MacroNewsService macroNewsService;
    private final AReportFusionService aReportFusionService;
    private final AISummaryService aiSummaryService;
    private final ThemeWatchlistService themeWatchlistService;
    private final ThemeAutoPoolService themeAutoPoolService;
    private final MacroNewsHistoryRepairService macroNewsHistoryRepairService;

    public MacroNewsController(MacroNewsService macroNewsService,
                               AReportFusionService aReportFusionService,
                               AISummaryService aiSummaryService,
                               ThemeWatchlistService themeWatchlistService,
                               ThemeAutoPoolService themeAutoPoolService,
                               MacroNewsHistoryRepairService macroNewsHistoryRepairService) {
        this.macroNewsService = macroNewsService;
        this.aReportFusionService = aReportFusionService;
        this.aiSummaryService = aiSummaryService;
        this.themeWatchlistService = themeWatchlistService;
        this.themeAutoPoolService = themeAutoPoolService;
        this.macroNewsHistoryRepairService = macroNewsHistoryRepairService;
    }

    @GetMapping("/shadow")
    public Map<String, Object> getShadowReport(@RequestParam(defaultValue = "24") int hours,
                                               @RequestParam(defaultValue = "10") int limit) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(Math.max(1, hours));
        List<MacroThemeEvent> events = macroNewsService.getShadowThemeEvents(startTime, endTime, limit);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("windowStart", startTime);
        result.put("windowEnd", endTime);
        result.put("count", events.size());
        result.put("events", events);
        return result;
    }

    @GetMapping("/fusion-preview")
    public Map<String, Object> getFusionPreview(@RequestParam(defaultValue = "24") int hours,
                                                @RequestParam(defaultValue = "8") int stockLimit,
                                                @RequestParam(defaultValue = "3") int macroLimit,
                                                @RequestParam(defaultValue = "3") int resonanceLimit) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(Math.max(1, hours));
        AReportFusionContext context = aReportFusionService.buildContext(
                startTime,
                endTime,
                stockLimit,
                macroLimit,
                resonanceLimit
        );

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("windowStart", startTime);
        result.put("windowEnd", endTime);
        result.put("macroThemeCount", context.getMacroThemeCount());
        result.put("resonanceCount", context.getResonanceCount());
        result.put("opportunityCount", context.getOpportunityAlerts() != null ? context.getOpportunityAlerts().size() : 0);
        result.put("riskCount", context.getRiskAlerts() != null ? context.getRiskAlerts().size() : 0);
        result.put("context", context);
        return result;
    }

    @GetMapping("/report-preview")
    public Map<String, Object> getReportPreview(@RequestParam(defaultValue = "morning") String reportType,
                                                @RequestParam(defaultValue = "24") int hours,
                                                @RequestParam(defaultValue = "8") int stockLimit,
                                                @RequestParam(defaultValue = "3") int macroLimit,
                                                @RequestParam(defaultValue = "3") int resonanceLimit,
                                                @RequestParam(required = false) String reportDate) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(Math.max(1, hours));
        AReportFusionContext context = aReportFusionService.buildContext(
                startTime,
                endTime,
                stockLimit,
                macroLimit,
                resonanceLimit
        );

        boolean morning = !"evening".equalsIgnoreCase(StringUtils.defaultString(reportType));
        String resolvedDate = StringUtils.defaultIfBlank(StringUtils.trimToNull(reportDate), endTime.toLocalDate().toString());
        String markdown = morning
                ? aiSummaryService.generateAMorningReportMarkdown(context, resolvedDate)
                : aiSummaryService.generateAEveningReportMarkdown(context, resolvedDate);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("reportType", morning ? "morning" : "evening");
        result.put("reportDate", resolvedDate);
        result.put("windowStart", startTime);
        result.put("windowEnd", endTime);
        result.put("macroThemeCount", context.getMacroThemeCount());
        result.put("resonanceCount", context.getResonanceCount());
        result.put("opportunityCount", context.getOpportunityAlerts() != null ? context.getOpportunityAlerts().size() : 0);
        result.put("riskCount", context.getRiskAlerts() != null ? context.getRiskAlerts().size() : 0);
        result.put("markdown", markdown);
        result.put("context", context);
        return result;
    }

    @GetMapping("/watchlist")
    public Map<String, Object> getWatchlist(@RequestParam(required = false) String themeName,
                                            @RequestParam(required = false) String enabled) {
        List<ThemeWatchlist> mappings = themeWatchlistService.list(themeName, parseEnabled(enabled));
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", mappings.size());
        result.put("watchlist", mappings);
        return result;
    }

    @GetMapping("/auto-pool")
    public Map<String, Object> getAutoPool(@RequestParam(required = false) String themeName,
                                           @RequestParam(required = false) String enabled) {
        List<ThemeAutoPoolCandidate> candidates = themeAutoPoolService.list(themeName, parseEnabled(enabled));
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", candidates.size());
        result.put("autoPool", candidates);
        return result;
    }

    @PostMapping("/watchlist")
    public Map<String, Object> upsertWatchlist(@RequestBody WatchlistUpsertRequest request) {
        ThemeWatchlist watchlist = new ThemeWatchlist();
        watchlist.setThemeName(request.getThemeName());
        watchlist.setStockCode(request.getStockCode());
        watchlist.setStockName(request.getStockName());
        watchlist.setPriority(request.getPriority());
        watchlist.setEnabled(request.getEnabled());
        watchlist.setReason(request.getReason());

        ThemeWatchlist saved = themeWatchlistService.upsert(watchlist);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "主题观察池已保存");
        result.put("watchlist", saved);
        return result;
    }

    @PostMapping("/watchlist/seed-defaults")
    public Map<String, Object> seedDefaultWatchlist(@RequestParam(defaultValue = "false") boolean overwriteExisting,
                                                    @RequestParam(defaultValue = "72") int repairHours) {
        ThemeWatchlistService.SeedSummary seedSummary = themeWatchlistService.seedDefaults(overwriteExisting);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "默认主题观察池已写入");
        result.put("seedSummary", seedSummary);
        if (repairHours > 0) {
            result.put("repairSummary", macroNewsHistoryRepairService.repairRecentEvents(repairHours));
        }
        return result;
    }

    @PostMapping("/watchlist/{id}/enabled")
    public Map<String, Object> updateWatchlistEnabled(@PathVariable String id,
                                                      @RequestParam(defaultValue = "true") boolean enabled) {
        boolean updated = themeWatchlistService.updateEnabled(id, enabled);
        Map<String, Object> result = new HashMap<>();
        result.put("success", updated);
        result.put("message", updated ? "主题观察池状态已更新" : "未找到对应的主题观察池记录");
        result.put("id", id);
        result.put("enabled", enabled);
        return result;
    }

    @DeleteMapping("/watchlist/{id}")
    public Map<String, Object> deleteWatchlist(@PathVariable String id) {
        boolean deleted = themeWatchlistService.deleteById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", deleted);
        result.put("message", deleted ? "主题观察池记录已删除" : "未找到对应的主题观察池记录");
        result.put("id", id);
        return result;
    }

    @Data
    public static class WatchlistUpsertRequest {
        private String themeName;
        private String stockCode;
        private String stockName;
        private Integer priority;
        private Integer enabled;
        private String reason;

        public void setThemeName(String themeName) {
            this.themeName = StringUtils.trimToNull(themeName);
        }

        public void setStockCode(String stockCode) {
            this.stockCode = StringUtils.trimToNull(stockCode);
        }

        public void setStockName(String stockName) {
            this.stockName = StringUtils.trimToNull(stockName);
        }

        public void setReason(String reason) {
            this.reason = StringUtils.trimToNull(reason);
        }
    }

    private Integer parseEnabled(String enabled) {
        String normalized = StringUtils.trimToNull(enabled);
        if (normalized == null) {
            return null;
        }
        if ("1".equals(normalized) || "true".equalsIgnoreCase(normalized) || "yes".equalsIgnoreCase(normalized)) {
            return 1;
        }
        if ("0".equals(normalized) || "false".equalsIgnoreCase(normalized) || "no".equalsIgnoreCase(normalized)) {
            return 0;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled 仅支持 1/0/true/false");
    }
}
