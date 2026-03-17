package com.dawei.service;

import com.dawei.entity.ThemeAutoPoolCandidate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 主题自动候选池服务
 */
public interface ThemeAutoPoolService {

    List<ThemeAutoPoolCandidate> list(String themeName, Integer enabled);

    List<ThemeAutoPoolCandidate> listEnabled();

    ThemeAutoPoolCandidate recordExplicitHit(String themeName,
                                             String stockCode,
                                             String stockName,
                                             Integer confidence,
                                             String reason,
                                             LocalDateTime latestPubDate);

    RebuildSummary rebuildFromRecentExplicitRelations(int hours);

    final class RebuildSummary {
        private final int scanned;
        private final int hours;
        private int inserted;
        private int enabled;

        public RebuildSummary(int scanned, int hours) {
            this.scanned = scanned;
            this.hours = hours;
        }

        public int getScanned() {
            return scanned;
        }

        public int getHours() {
            return hours;
        }

        public int getInserted() {
            return inserted;
        }

        public int getEnabled() {
            return enabled;
        }

        public void incrementInserted() {
            inserted++;
        }

        public void incrementEnabled() {
            enabled++;
        }

        @Override
        public String toString() {
            return "RebuildSummary{" +
                    "scanned=" + scanned +
                    ", hours=" + hours +
                    ", inserted=" + inserted +
                    ", enabled=" + enabled +
                    '}';
        }
    }
}
