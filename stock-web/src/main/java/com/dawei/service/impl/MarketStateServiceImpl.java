package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.service.MarketDataProvider;
import com.dawei.service.MarketStateService;
import com.dawei.utils.AStockMarketClock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 市场状态机实现。
 */
@Slf4j
@Service
public class MarketStateServiceImpl implements MarketStateService {

    private final MarketDataProvider marketDataProvider;
    private final StockFilterConfig filterConfig;

    private volatile MarketSnapshot cachedSnapshot;
    private volatile LocalDateTime lastSuccessAt;
    private volatile LocalDateTime lastFailureAt;
    private volatile String lastFailureReason;
    private volatile int consecutiveFailureCount;

    public MarketStateServiceImpl(MarketDataProvider marketDataProvider,
                                  StockFilterConfig filterConfig) {
        this.marketDataProvider = marketDataProvider;
        this.filterConfig = filterConfig;
    }

    @Override
    public MarketSnapshot getLatestSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        MarketSnapshot snapshot = cachedSnapshot;
        if (snapshot == null || isStale(snapshot, now)) {
            if (shouldSkipAutoRefresh(now)) {
                MarketSnapshot fallback = fallbackSnapshot("FAILURE_COOLDOWN", now);
                cachedSnapshot = fallback;
                return fallback;
            }
            return refreshSnapshot();
        }
        return snapshot;
    }

    @Override
    public synchronized MarketSnapshot refreshSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        try {
            MarketSnapshot latest = marketDataProvider.fetchSnapshot();
            if (latest == null) {
                return handleRefreshFailure(now, "快照为空", "EMPTY");
            }
            if (latest.getCapturedAt() == null) {
                latest.setCapturedAt(now);
            }
            MarketState previousState = cachedSnapshot != null ? cachedSnapshot.getMarketState() : null;
            latest.setMarketState(resolveState(latest));
            lastSuccessAt = latest.getCapturedAt();
            latest.setLastSuccessAt(lastSuccessAt);
            latest.setLastFailureAt(lastFailureAt);
            latest.setLastFailureReason(lastFailureReason);
            latest.setNextRetryAt(null);
            latest.setConsecutiveFailureCount(0);
            latest.setFallback(false);
            latest.setStale(false);
            latest.setSnapshotHealth(MarketSnapshotHealth.LIVE);
            consecutiveFailureCount = 0;
            cachedSnapshot = latest;
            if (previousState != latest.getMarketState()) {
                log.info("市场状态切换：{} -> {}，指数均值={}",
                        previousState != null ? previousState.getLabel() : "未初始化",
                        latest.getMarketState().getLabel(),
                        String.format("%.2f", latest.getAverageIndexChangePct()));
            }
            return latest;
        } catch (Exception ex) {
            return handleRefreshFailure(now, ex.getMessage(), "STALE_FALLBACK");
        }
    }

    @Override
    public MarketState resolveState(MarketSnapshot snapshot) {
        if (snapshot == null) {
            return MarketState.NEUTRAL;
        }
        double weakestIndex = snapshot.getWeakestIndexChangePct();
        double strongestIndex = snapshot.getStrongestIndexChangePct();
        double breadthRatio = snapshot.getAdvanceDeclineRatio();

        boolean defensive = weakestIndex <= filterConfig.getMarketDefensiveIndexDropThreshold()
                || snapshot.getLimitDownCount() >= filterConfig.getMarketDefensiveLimitDownThreshold()
                || (snapshot.hasBreadthData() && breadthRatio <= filterConfig.getMarketDefensiveBreadthThreshold());
        if (defensive) {
            return MarketState.DEFENSIVE;
        }

        boolean overheat = strongestIndex >= filterConfig.getMarketOverheatIndexRiseThreshold()
                && ((snapshot.hasBreadthData() && breadthRatio >= filterConfig.getMarketOverheatBreadthThreshold())
                    || snapshot.getLimitUpCount() >= filterConfig.getMarketOverheatLimitUpThreshold());
        if (overheat) {
            return MarketState.OVERHEAT;
        }

        boolean riskOn = strongestIndex >= filterConfig.getMarketRiskOnIndexRiseThreshold()
                && (!snapshot.hasBreadthData()
                    || breadthRatio >= filterConfig.getMarketRiskOnBreadthThreshold());
        if (riskOn) {
            return MarketState.RISK_ON;
        }

        return MarketState.NEUTRAL;
    }

    private MarketSnapshot handleRefreshFailure(LocalDateTime now, String reason, String fallbackSource) {
        consecutiveFailureCount++;
        lastFailureAt = now;
        lastFailureReason = reason;
        logRefreshFailure(now, reason);
        MarketSnapshot fallback = fallbackSnapshot(fallbackSource, now);
        cachedSnapshot = fallback;
        return fallback;
    }

    private boolean isStale(MarketSnapshot snapshot, LocalDateTime now) {
        return snapshot == null
                || snapshot.getCapturedAt() == null
                || snapshot.getCapturedAt().isBefore(now.minusMinutes(filterConfig.getMarketSnapshotRefreshMinutes()));
    }

    private boolean shouldSkipAutoRefresh(LocalDateTime now) {
        if (lastFailureAt == null) {
            return false;
        }
        return lastFailureAt.plusSeconds(Math.max(0, filterConfig.getMarketSnapshotFailureCooldownSeconds()))
                .isAfter(now);
    }

    private MarketSnapshot fallbackSnapshot(String source, LocalDateTime now) {
        MarketSnapshot snapshot = cachedSnapshot != null ? copyOf(cachedSnapshot) : MarketSnapshot.neutral(null, source);
        if (snapshot.getSource() == null || snapshot.getSource().isBlank()) {
            snapshot.setSource(source);
        }
        snapshot.setLastSuccessAt(lastSuccessAt);
        snapshot.setLastFailureAt(lastFailureAt);
        snapshot.setLastFailureReason(lastFailureReason);
        snapshot.setConsecutiveFailureCount(consecutiveFailureCount);
        snapshot.setNextRetryAt(lastFailureAt == null
                ? null
                : lastFailureAt.plusSeconds(Math.max(0, filterConfig.getMarketSnapshotFailureCooldownSeconds())));
        snapshot.setFallback(true);
        snapshot.setStale(isStale(snapshot, now));
        snapshot.setSnapshotHealth(resolveSnapshotHealth(snapshot, now));
        return snapshot;
    }

    private MarketSnapshotHealth resolveSnapshotHealth(MarketSnapshot snapshot, LocalDateTime now) {
        boolean missingCapturedAt = snapshot == null || snapshot.getCapturedAt() == null;
        boolean severelyStale = snapshot == null
                || snapshot.getCapturedAt() == null
                || snapshot.getCapturedAt().isBefore(now.minusMinutes(filterConfig.getMarketSnapshotRefreshMinutes() * 2L));
        if (missingCapturedAt
                || consecutiveFailureCount >= filterConfig.getMarketSnapshotDisconnectFailureThreshold()
                || severelyStale) {
            return MarketSnapshotHealth.DISCONNECTED;
        }
        if (isStale(snapshot, now) || consecutiveFailureCount > 0) {
            return MarketSnapshotHealth.DEGRADED;
        }
        return MarketSnapshotHealth.LIVE;
    }

    private void logRefreshFailure(LocalDateTime now, String reason) {
        LocalDateTime nextRetryAt = lastFailureAt == null
                ? null
                : lastFailureAt.plusSeconds(Math.max(0, filterConfig.getMarketSnapshotFailureCooldownSeconds()));
        if (AStockMarketClock.isMarketAttentionWindow(now)
                || consecutiveFailureCount >= filterConfig.getMarketSnapshotDisconnectFailureThreshold()) {
            log.warn("刷新市场状态失败，连续失败={}，nextRetryAt={}, reason={}",
                    consecutiveFailureCount, nextRetryAt, reason);
        } else {
            log.info("刷新市场状态失败，当前非交易关注时段，连续失败={}，nextRetryAt={}, reason={}",
                    consecutiveFailureCount, nextRetryAt, reason);
        }
    }

    private MarketSnapshot copyOf(MarketSnapshot source) {
        if (source == null) {
            return null;
        }
        MarketSnapshot snapshot = new MarketSnapshot();
        snapshot.setMarketState(source.getMarketState());
        snapshot.setSnapshotHealth(source.getSnapshotHealth());
        snapshot.setCapturedAt(source.getCapturedAt());
        snapshot.setLastSuccessAt(source.getLastSuccessAt());
        snapshot.setLastFailureAt(source.getLastFailureAt());
        snapshot.setNextRetryAt(source.getNextRetryAt());
        snapshot.setShChangePct(source.getShChangePct());
        snapshot.setSzChangePct(source.getSzChangePct());
        snapshot.setCybChangePct(source.getCybChangePct());
        snapshot.setUpCount(source.getUpCount());
        snapshot.setDownCount(source.getDownCount());
        snapshot.setFlatCount(source.getFlatCount());
        snapshot.setLimitUpCount(source.getLimitUpCount());
        snapshot.setLimitDownCount(source.getLimitDownCount());
        snapshot.setBreadthSampleSize(source.getBreadthSampleSize());
        snapshot.setSource(source.getSource());
        snapshot.setConsecutiveFailureCount(source.getConsecutiveFailureCount());
        snapshot.setFallback(source.isFallback());
        snapshot.setStale(source.isStale());
        snapshot.setLastFailureReason(source.getLastFailureReason());
        return snapshot;
    }
}
