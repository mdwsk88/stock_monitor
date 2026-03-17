package com.dawei.service;

import com.dawei.entity.MacroThemeEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 宏观新闻与主题事件服务
 */
public interface MacroNewsService {

    FetchSummary fetchAndSaveMacroNews() throws Exception;

    List<MacroThemeEvent> getShadowThemeEvents(LocalDateTime startTime, LocalDateTime endTime, int limit);

    final class FetchSummary {
        private int scanned;
        private int savedRaw;
        private int filtered;
        private int savedEvents;
        private int savedRelations;

        public int getScanned() {
            return scanned;
        }

        public int getSavedRaw() {
            return savedRaw;
        }

        public int getFiltered() {
            return filtered;
        }

        public int getSavedEvents() {
            return savedEvents;
        }

        public int getSavedRelations() {
            return savedRelations;
        }

        public void incrementScanned() {
            scanned++;
        }

        public void incrementSavedRaw() {
            savedRaw++;
        }

        public void incrementFiltered() {
            filtered++;
        }

        public void incrementSavedEvents() {
            savedEvents++;
        }

        public void incrementSavedRelations() {
            savedRelations++;
        }

        @Override
        public String toString() {
            return "FetchSummary{" +
                    "scanned=" + scanned +
                    ", savedRaw=" + savedRaw +
                    ", filtered=" + filtered +
                    ", savedEvents=" + savedEvents +
                    ", savedRelations=" + savedRelations +
                    '}';
        }
    }
}
