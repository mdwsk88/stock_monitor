package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.MacroThemeStockRel;
import com.dawei.entity.ThemeAutoPoolCandidate;
import com.dawei.mapper.MacroThemeStockRelMapper;
import com.dawei.mapper.ThemeAutoPoolCandidateMapper;
import com.dawei.service.ThemeAutoPoolService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 主题自动候选池服务实现
 */
@Service
public class ThemeAutoPoolServiceImpl implements ThemeAutoPoolService {

    private static final int AUTO_POOL_ENABLE_SCORE = 85;
    private static final int AUTO_POOL_MAX_SCORE = 100;

    private final ThemeAutoPoolCandidateMapper themeAutoPoolCandidateMapper;
    private final MacroThemeStockRelMapper macroThemeStockRelMapper;

    public ThemeAutoPoolServiceImpl(ThemeAutoPoolCandidateMapper themeAutoPoolCandidateMapper,
                                    MacroThemeStockRelMapper macroThemeStockRelMapper) {
        this.themeAutoPoolCandidateMapper = themeAutoPoolCandidateMapper;
        this.macroThemeStockRelMapper = macroThemeStockRelMapper;
    }

    @Override
    public List<ThemeAutoPoolCandidate> list(String themeName, Integer enabled) {
        QueryWrapper<ThemeAutoPoolCandidate> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(themeName)) {
            queryWrapper.eq("theme_name", normalize(themeName));
        }
        if (enabled != null) {
            queryWrapper.eq("enabled", enabled > 0 ? 1 : 0);
        }
        queryWrapper.orderByDesc("enabled")
                .orderByDesc("candidate_score")
                .orderByDesc("hit_count")
                .orderByDesc("latest_pub_date")
                .orderByAsc("theme_name")
                .orderByAsc("stock_code");
        return themeAutoPoolCandidateMapper.selectList(queryWrapper);
    }

    @Override
    public List<ThemeAutoPoolCandidate> listEnabled() {
        return list(null, 1);
    }

    @Override
    public ThemeAutoPoolCandidate recordExplicitHit(String themeName,
                                                    String stockCode,
                                                    String stockName,
                                                    Integer confidence,
                                                    String reason,
                                                    LocalDateTime latestPubDate) {
        String normalizedTheme = normalize(themeName);
        String normalizedStockCode = normalize(stockCode);
        if (StringUtils.isBlank(normalizedTheme) || StringUtils.isBlank(normalizedStockCode)) {
            return null;
        }

        ThemeAutoPoolCandidate existing = findByThemeAndStock(normalizedTheme, normalizedStockCode);
        int baseConfidence = safeConfidence(confidence);
        if (existing == null) {
            ThemeAutoPoolCandidate candidate = new ThemeAutoPoolCandidate();
            candidate.setId(UUID.randomUUID().toString().replace("-", ""));
            candidate.setThemeName(normalizedTheme);
            candidate.setStockCode(normalizedStockCode);
            candidate.setStockName(normalize(stockName));
            candidate.setHitCount(1);
            candidate.setCandidateScore(computeCandidateScore(baseConfidence, 1));
            candidate.setEnabled(isEnabled(candidate.getCandidateScore(), 1) ? 1 : 0);
            candidate.setReason(buildReason(reason, 1));
            candidate.setLatestPubDate(latestPubDate);
            candidate.setCreateTime(LocalDateTime.now());
            candidate.setUpdateTime(LocalDateTime.now());
            themeAutoPoolCandidateMapper.insert(candidate);
            return candidate;
        }

        int nextHitCount = safeInt(existing.getHitCount()) + 1;
        existing.setStockName(preferInput(stockName, existing.getStockName()));
        existing.setHitCount(nextHitCount);
        existing.setCandidateScore(computeCandidateScore(Math.max(safeInt(existing.getCandidateScore()), baseConfidence), nextHitCount));
        existing.setEnabled(isEnabled(existing.getCandidateScore(), nextHitCount) ? 1 : safeInt(existing.getEnabled()));
        existing.setReason(buildReason(reason, nextHitCount));
        existing.setLatestPubDate(maxDate(existing.getLatestPubDate(), latestPubDate));
        existing.setUpdateTime(LocalDateTime.now());
        themeAutoPoolCandidateMapper.updateById(existing);
        return existing;
    }

    @Override
    public RebuildSummary rebuildFromRecentExplicitRelations(int hours) {
        int effectiveHours = Math.max(1, hours);
        LocalDateTime startTime = LocalDateTime.now().minusHours(effectiveHours);
        List<MacroThemeStockRel> explicitRelations = macroThemeStockRelMapper.selectList(new QueryWrapper<MacroThemeStockRel>()
                .eq("match_type", "EXPLICIT")
                .ge("create_time", startTime)
                .orderByAsc("create_time")
                .orderByAsc("theme_name")
                .orderByAsc("stock_code"));

        Map<String, AggregateCandidate> aggregates = new LinkedHashMap<>();
        for (MacroThemeStockRel relation : explicitRelations) {
            if (relation == null || StringUtils.isBlank(relation.getThemeName()) || StringUtils.isBlank(relation.getStockCode())) {
                continue;
            }
            String key = normalize(relation.getThemeName()) + "|" + normalize(relation.getStockCode());
            aggregates.computeIfAbsent(key, unused -> new AggregateCandidate(
                    normalize(relation.getThemeName()),
                    normalize(relation.getStockCode()),
                    normalize(relation.getStockName())
            )).accept(relation);
        }

        themeAutoPoolCandidateMapper.delete(new QueryWrapper<>());
        RebuildSummary summary = new RebuildSummary(explicitRelations.size(), effectiveHours);
        for (AggregateCandidate aggregate : aggregates.values()) {
            ThemeAutoPoolCandidate candidate = aggregate.toCandidate();
            themeAutoPoolCandidateMapper.insert(candidate);
            summary.incrementInserted();
            if (safeInt(candidate.getEnabled()) > 0) {
                summary.incrementEnabled();
            }
        }
        return summary;
    }

    private ThemeAutoPoolCandidate findByThemeAndStock(String themeName, String stockCode) {
        return themeAutoPoolCandidateMapper.selectOne(new QueryWrapper<ThemeAutoPoolCandidate>()
                .eq("theme_name", themeName)
                .eq("stock_code", stockCode)
                .last("LIMIT 1"));
    }

    private int computeCandidateScore(int baseConfidence, int hitCount) {
        int normalizedConfidence = Math.max(70, baseConfidence);
        int bonus = Math.min(20, Math.max(0, hitCount - 1) * 5);
        return Math.min(AUTO_POOL_MAX_SCORE, normalizedConfidence + bonus);
    }

    private boolean isEnabled(Integer candidateScore, int hitCount) {
        return safeInt(candidateScore) >= AUTO_POOL_ENABLE_SCORE || hitCount >= 2;
    }

    private int safeConfidence(Integer confidence) {
        return Math.max(0, confidence == null ? 0 : confidence);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        return StringUtils.trimToNull(value);
    }

    private String preferInput(String input, String fallback) {
        String normalized = normalize(input);
        return normalized != null ? normalized : fallback;
    }

    private LocalDateTime maxDate(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private String buildReason(String reason, int hitCount) {
        String normalized = normalize(reason);
        if (StringUtils.isBlank(normalized)) {
            return "自动候选池：显式提股累计命中 " + hitCount + " 次";
        }
        return normalized + "（累计命中 " + hitCount + " 次）";
    }

    private final class AggregateCandidate {
        private final String themeName;
        private final String stockCode;
        private String stockName;
        private int maxConfidence;
        private int hitCount;
        private LocalDateTime latestPubDate;
        private String reason;

        private AggregateCandidate(String themeName, String stockCode, String stockName) {
            this.themeName = themeName;
            this.stockCode = stockCode;
            this.stockName = stockName;
        }

        private void accept(MacroThemeStockRel relation) {
            hitCount++;
            stockName = preferInput(relation.getStockName(), stockName);
            maxConfidence = Math.max(maxConfidence, safeInt(relation.getConfidence()));
            latestPubDate = maxDate(latestPubDate, relation.getCreateTime());
            reason = preferInput(relation.getReason(), reason);
        }

        private ThemeAutoPoolCandidate toCandidate() {
            ThemeAutoPoolCandidate candidate = new ThemeAutoPoolCandidate();
            candidate.setId(UUID.randomUUID().toString().replace("-", ""));
            candidate.setThemeName(themeName);
            candidate.setStockCode(stockCode);
            candidate.setStockName(stockName);
            candidate.setHitCount(hitCount);
            candidate.setCandidateScore(computeCandidateScore(maxConfidence, hitCount));
            candidate.setEnabled(isEnabled(candidate.getCandidateScore(), hitCount) ? 1 : 0);
            candidate.setReason(buildReason(reason, hitCount));
            candidate.setLatestPubDate(latestPubDate);
            candidate.setCreateTime(LocalDateTime.now());
            candidate.setUpdateTime(LocalDateTime.now());
            return candidate;
        }
    }
}
