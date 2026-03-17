package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A股报告融合上下文
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AReportFusionContext {

    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private List<MacroThemeEvent> macroThemes = new ArrayList<>();
    private List<AReportResonanceCard> resonanceCandidates = new ArrayList<>();
    private List<StockAlertDTO<AStockRss>> opportunityAlerts = new ArrayList<>();
    private List<StockAlertDTO<AStockRss>> riskAlerts = new ArrayList<>();

    public boolean isEmpty() {
        return macroThemes == null || macroThemes.isEmpty()
                ? (resonanceCandidates == null || resonanceCandidates.isEmpty())
                    && (opportunityAlerts == null || opportunityAlerts.isEmpty())
                    && (riskAlerts == null || riskAlerts.isEmpty())
                : false;
    }

    public int getAlertCount() {
        return sizeOf(opportunityAlerts) + sizeOf(riskAlerts);
    }

    public int getMacroThemeCount() {
        return sizeOf(macroThemes);
    }

    public int getResonanceCount() {
        return sizeOf(resonanceCandidates);
    }

    private int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
