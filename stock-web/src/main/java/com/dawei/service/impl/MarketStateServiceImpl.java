package com.dawei.service.impl;

import com.dawei.config.StockFilterConfig;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketAlertFamily;
import com.dawei.entity.MarketSnapshotHealth;
import com.dawei.entity.MarketState;
import com.dawei.service.MarketDataProvider;
import com.dawei.service.MarketStateService;
import com.dawei.utils.AStockMarketClock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 市场状态机实现。
 */
@Slf4j
@Service
public class MarketStateServiceImpl implements MarketStateService {

    private final MarketDataProvider marketDataProvider;
    private final StockFilterConfig filterConfig;

    private volatile MarketSnapshot cachedSnapshot;
    private volatile LocalDateTime effectiveStateSince;
    private volatile LocalDateTime lastSuccessAt;
    private volatile LocalDateTime lastFailureAt;
    private volatile String lastFailureReason;
    private volatile int consecutiveFailureCount;
    private final Deque<StateObservation> rawStateHistory = new ArrayDeque<>();

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
            MarketState rawState = resolveState(latest);
            latest.setRawMarketState(rawState);
            latest.setMarketState(resolveEffectiveState(latest, rawState));
            latest.setStateConfirmedAt(effectiveStateSince != null ? effectiveStateSince : latest.getCapturedAt());
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
                log.info("市场状态切换：{} -> {}，raw={}, 指数均值={}",
                        previousState != null ? previousState.getLabel() : "未初始化",
                        latest.getMarketState().getLabel(),
                        rawState.getLabel(),
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
        snapshot.setRawMarketState(source.getRawMarketState());
        snapshot.setSnapshotHealth(source.getSnapshotHealth());
        snapshot.setCapturedAt(source.getCapturedAt());
        snapshot.setStateConfirmedAt(source.getStateConfirmedAt());
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

    private MarketState resolveEffectiveState(MarketSnapshot snapshot, MarketState rawState) {
        LocalDateTime capturedAt = snapshot != null && snapshot.getCapturedAt() != null
                ? snapshot.getCapturedAt()
                : LocalDateTime.now();
        recordObservation(capturedAt, rawState);

        MarketState current = cachedSnapshot != null && cachedSnapshot.getMarketState() != null
                ? cachedSnapshot.getMarketState()
                : null;
        if (current == null) {
            effectiveStateSince = capturedAt;
            return rawState;
        }
        if (current == rawState) {
            if (effectiveStateSince == null) {
                effectiveStateSince = capturedAt;
            }
            return current;
        }
        if (isEmergencyRiskOverride(snapshot, rawState)) {
            effectiveStateSince = capturedAt;
            return rawState;
        }

        MarketState candidate = resolveConfirmedCandidate(current, capturedAt);
        if (candidate != current) {
            effectiveStateSince = capturedAt;
        }
        return candidate;
    }

    private void recordObservation(LocalDateTime capturedAt, MarketState rawState) {
        rawStateHistory.addLast(new StateObservation(capturedAt, rawState));
        long retentionMinutes = List.of(
                filterConfig.getMarketStateDefensiveConfirmMinutes(),
                filterConfig.getMarketStateNeutralConfirmMinutes(),
                filterConfig.getMarketStateRiskOnConfirmMinutes(),
                filterConfig.getMarketStateOverheatConfirmMinutes()
        ).stream().mapToLong(Integer::longValue).max().orElse(120L) + 10L;
        LocalDateTime cutoff = capturedAt.minusMinutes(retentionMinutes);
        while (!rawStateHistory.isEmpty() && rawStateHistory.peekFirst().capturedAt().isBefore(cutoff)) {
            rawStateHistory.removeFirst();
        }
    }

    private MarketState resolveConfirmedCandidate(MarketState current, LocalDateTime capturedAt) {
        if (isConfirmed(MarketState.DEFENSIVE, capturedAt) && canSwitch(current, MarketState.DEFENSIVE, capturedAt)) {
            return MarketState.DEFENSIVE;
        }
        if (isConfirmed(MarketState.OVERHEAT, capturedAt) && canSwitch(current, MarketState.OVERHEAT, capturedAt)) {
            return MarketState.OVERHEAT;
        }
        if (isConfirmed(MarketState.RISK_ON, capturedAt) && canSwitch(current, MarketState.RISK_ON, capturedAt)) {
            return MarketState.RISK_ON;
        }
        if (isConfirmed(MarketState.NEUTRAL, capturedAt)) {
            return MarketState.NEUTRAL;
        }
        return current;
    }

    private boolean canSwitch(MarketState current, MarketState candidate, LocalDateTime capturedAt) {
        MarketAlertFamily currentFamily = MarketAlertFamily.fromMarketState(current);
        MarketAlertFamily candidateFamily = MarketAlertFamily.fromMarketState(candidate);
        if (!currentFamily.conflictsWith(candidateFamily)) {
            return true;
        }
        if (effectiveStateSince == null) {
            return true;
        }
        return !effectiveStateSince.plusMinutes(filterConfig.getMarketStateFamilyMinDwellMinutes()).isAfter(capturedAt);
    }

    private boolean isConfirmed(MarketState targetState, LocalDateTime capturedAt) {
        int confirmMinutes = confirmWindowMinutes(targetState);
        LocalDateTime windowStart = capturedAt.minusMinutes(confirmMinutes);
        List<StateObservation> window = rawStateHistory.stream()
                .filter(item -> !item.capturedAt().isBefore(windowStart))
                .toList();
        if (window.size() < filterConfig.getMarketStateConfirmMinObservations()) {
            return false;
        }
        StateObservation latest = window.get(window.size() - 1);
        if (!supportsTarget(latest.rawState(), targetState)) {
            return false;
        }
        long matched = window.stream()
                .filter(item -> supportsTarget(item.rawState(), targetState))
                .count();
        double ratio = matched * 1.0d / window.size();
        return ratio >= filterConfig.getMarketStateConfirmRatio();
    }

    private int confirmWindowMinutes(MarketState targetState) {
        return switch (targetState) {
            case DEFENSIVE -> filterConfig.getMarketStateDefensiveConfirmMinutes();
            case RISK_ON -> filterConfig.getMarketStateRiskOnConfirmMinutes();
            case OVERHEAT -> filterConfig.getMarketStateOverheatConfirmMinutes();
            case NEUTRAL -> filterConfig.getMarketStateNeutralConfirmMinutes();
        };
    }

    private boolean supportsTarget(MarketState rawState, MarketState targetState) {
        if (rawState == null || targetState == null) {
            return false;
        }
        if (targetState == MarketState.RISK_ON) {
            return rawState == MarketState.RISK_ON || rawState == MarketState.OVERHEAT;
        }
        return rawState == targetState;
    }

    private boolean isEmergencyRiskOverride(MarketSnapshot snapshot, MarketState rawState) {
        if (snapshot == null || rawState != MarketState.DEFENSIVE) {
            return false;
        }
        if (snapshot.getWeakestIndexChangePct() <= filterConfig.getMarketPanicIndexDropThreshold()) {
            return true;
        }
        if (snapshot.getLimitDownCount() >= filterConfig.getMarketPanicLimitDownThreshold()) {
            return true;
        }
        return snapshot.hasBreadthData()
                && snapshot.getAdvanceDeclineRatio() <= filterConfig.getMarketPanicBreadthThreshold();
    }

    private record StateObservation(LocalDateTime capturedAt, MarketState rawState) {
    }
}
