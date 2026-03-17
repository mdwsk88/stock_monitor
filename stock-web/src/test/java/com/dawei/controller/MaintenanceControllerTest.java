package com.dawei.controller;

import com.dawei.service.MacroNewsService;
import com.dawei.service.ThemeAutoPoolService;
import com.dawei.service.impl.AStockHistoryRepairService;
import com.dawei.service.impl.MacroNewsHistoryRepairService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceControllerTest {

    @Mock
    private AStockHistoryRepairService aStockHistoryRepairService;
    @Mock
    private MacroNewsService macroNewsService;
    @Mock
    private MacroNewsHistoryRepairService macroNewsHistoryRepairService;
    @Mock
    private ThemeAutoPoolService themeAutoPoolService;

    @InjectMocks
    private MaintenanceController maintenanceController;

    @Test
    @DisplayName("测试重建主题自动候选池")
    void testRebuildThemeAutoPool() {
        ThemeAutoPoolService.RebuildSummary summary = new ThemeAutoPoolService.RebuildSummary(12, 168);
        summary.incrementInserted();
        summary.incrementEnabled();
        when(themeAutoPoolService.rebuildFromRecentExplicitRelations(168)).thenReturn(summary);

        Map<String, Object> result = maintenanceController.rebuildThemeAutoPool(168);

        assertTrue((Boolean) result.get("success"));
        assertEquals("主题自动候选池重建完成", result.get("message"));
        assertEquals(summary, result.get("summary"));
        verify(themeAutoPoolService).rebuildFromRecentExplicitRelations(168);
    }
}
