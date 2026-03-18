package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A股实时预警上下文
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AStockRealtimeContext {

    private String themeName;
    private String macroTitle;
    private String macroSummary;
    private Integer macroSignalScore;
    private Integer resonanceScore;
    private String relationReason;
    private String relationType;

    public static AStockRealtimeContext empty() {
        return new AStockRealtimeContext(null, null, null, 0, 0, null, null);
    }

    public boolean hasResonance() {
        return themeName != null && !themeName.isBlank();
    }
}
