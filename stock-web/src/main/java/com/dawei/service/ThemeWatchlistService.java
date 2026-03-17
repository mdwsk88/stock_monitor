package com.dawei.service;

import com.dawei.entity.ThemeWatchlist;

import java.util.List;

/**
 * 主题观察池管理服务
 */
public interface ThemeWatchlistService {

    List<ThemeWatchlist> list(String themeName, Integer enabled);

    ThemeWatchlist upsert(ThemeWatchlist watchlist);

    SeedSummary seedDefaults(boolean overwriteExisting);

    boolean updateEnabled(String id, boolean enabled);

    boolean deleteById(String id);

    final class SeedSummary {
        private final int totalTemplates;
        private int inserted;
        private int updated;
        private int skipped;

        public SeedSummary(int totalTemplates) {
            this.totalTemplates = totalTemplates;
        }

        public int getTotalTemplates() {
            return totalTemplates;
        }

        public int getInserted() {
            return inserted;
        }

        public int getUpdated() {
            return updated;
        }

        public int getSkipped() {
            return skipped;
        }

        public void incrementInserted() {
            inserted++;
        }

        public void incrementUpdated() {
            updated++;
        }

        public void incrementSkipped() {
            skipped++;
        }

        @Override
        public String toString() {
            return "SeedSummary{" +
                    "totalTemplates=" + totalTemplates +
                    ", inserted=" + inserted +
                    ", updated=" + updated +
                    ", skipped=" + skipped +
                    '}';
        }
    }
}
