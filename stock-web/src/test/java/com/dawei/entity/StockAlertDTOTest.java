package com.dawei.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @ClassName StockAlertDTOTest
 * @Author dawei
 * @Description 股票异动数据传输对象单元测试 - 纯JUnit测试
 **/
class StockAlertDTOTest {

    @Test
    @DisplayName("测试活跃度等级 - 极度活跃(>=25)")
    void testActivityLevel_Extreme() {
        StockAlertDTO<?> dto25 = new StockAlertDTO<>(null, 25);
        assertEquals("极度活跃", dto25.getActivityLevel());
        
        StockAlertDTO<?> dto30 = new StockAlertDTO<>(null, 30);
        assertEquals("极度活跃", dto30.getActivityLevel());
        
        StockAlertDTO<?> dto100 = new StockAlertDTO<>(null, 100);
        assertEquals("极度活跃", dto100.getActivityLevel());
    }

    @Test
    @DisplayName("测试活跃度等级 - 高度活跃(15-24)")
    void testActivityLevel_High() {
        StockAlertDTO<?> dto15 = new StockAlertDTO<>(null, 15);
        assertEquals("高度活跃", dto15.getActivityLevel());
        
        StockAlertDTO<?> dto20 = new StockAlertDTO<>(null, 20);
        assertEquals("高度活跃", dto20.getActivityLevel());
        
        StockAlertDTO<?> dto24 = new StockAlertDTO<>(null, 24);
        assertEquals("高度活跃", dto24.getActivityLevel());
    }

    @Test
    @DisplayName("测试活跃度等级 - 中度活跃(10-14)")
    void testActivityLevel_Medium() {
        StockAlertDTO<?> dto10 = new StockAlertDTO<>(null, 10);
        assertEquals("中度活跃", dto10.getActivityLevel());
        
        StockAlertDTO<?> dto12 = new StockAlertDTO<>(null, 12);
        assertEquals("中度活跃", dto12.getActivityLevel());
        
        StockAlertDTO<?> dto14 = new StockAlertDTO<>(null, 14);
        assertEquals("中度活跃", dto14.getActivityLevel());
    }

    @Test
    @DisplayName("测试活跃度等级 - 轻度活跃(<10)")
    void testActivityLevel_Low() {
        StockAlertDTO<?> dto0 = new StockAlertDTO<>(null, 0);
        assertEquals("轻度活跃", dto0.getActivityLevel());
        
        StockAlertDTO<?> dto5 = new StockAlertDTO<>(null, 5);
        assertEquals("轻度活跃", dto5.getActivityLevel());
        
        StockAlertDTO<?> dto9 = new StockAlertDTO<>(null, 9);
        assertEquals("轻度活跃", dto9.getActivityLevel());
    }

    @Test
    @DisplayName("测试颜色标签 - 极度活跃warning(>=25)")
    void testColorTag_Warning() {
        assertEquals("warning", new StockAlertDTO<>(null, 25).getColorTag());
        assertEquals("warning", new StockAlertDTO<>(null, 30).getColorTag());
        assertEquals("warning", new StockAlertDTO<>(null, 50).getColorTag());
    }

    @Test
    @DisplayName("测试颜色标签 - 高度活跃info(15-24)")
    void testColorTag_Info() {
        assertEquals("info", new StockAlertDTO<>(null, 15).getColorTag());
        assertEquals("info", new StockAlertDTO<>(null, 20).getColorTag());
        assertEquals("info", new StockAlertDTO<>(null, 24).getColorTag());
    }

    @Test
    @DisplayName("测试颜色标签 - 中度活跃success(10-14)")
    void testColorTag_Success() {
        assertEquals("success", new StockAlertDTO<>(null, 10).getColorTag());
        assertEquals("success", new StockAlertDTO<>(null, 12).getColorTag());
        assertEquals("success", new StockAlertDTO<>(null, 14).getColorTag());
    }

    @Test
    @DisplayName("测试颜色标签 - 轻度活跃comment(<10)")
    void testColorTag_Comment() {
        assertEquals("comment", new StockAlertDTO<>(null, 0).getColorTag());
        assertEquals("comment", new StockAlertDTO<>(null, 5).getColorTag());
        assertEquals("comment", new StockAlertDTO<>(null, 9).getColorTag());
    }

    @Test
    @DisplayName("测试激增比例描述 - >=25次(400%+)")
    void testSurgeDescription_Extreme() {
        StockAlertDTO<?> dto25 = new StockAlertDTO<>(null, 25);
        assertEquals("较昨日激增 400%+", dto25.getSurgeDescription());
        
        StockAlertDTO<?> dto30 = new StockAlertDTO<>(null, 30);
        assertEquals("较昨日激增 400%+", dto30.getSurgeDescription());
        
        StockAlertDTO<?> dto50 = new StockAlertDTO<>(null, 50);
        assertEquals("较昨日激增 400%+", dto50.getSurgeDescription());
    }

    @Test
    @DisplayName("测试激增比例描述 - 15-24次(200%+)")
    void testSurgeDescription_High() {
        StockAlertDTO<?> dto15 = new StockAlertDTO<>(null, 15);
        assertEquals("较昨日激增 200%+", dto15.getSurgeDescription());
        
        StockAlertDTO<?> dto20 = new StockAlertDTO<>(null, 20);
        assertEquals("较昨日激增 200%+", dto20.getSurgeDescription());
        
        StockAlertDTO<?> dto24 = new StockAlertDTO<>(null, 24);
        assertEquals("较昨日激增 200%+", dto24.getSurgeDescription());
    }

    @Test
    @DisplayName("测试激增比例描述 - 10-14次(显著)")
    void testSurgeDescription_Medium() {
        StockAlertDTO<?> dto10 = new StockAlertDTO<>(null, 10);
        assertEquals("较昨日显著上升", dto10.getSurgeDescription());
        
        StockAlertDTO<?> dto12 = new StockAlertDTO<>(null, 12);
        assertEquals("较昨日显著上升", dto12.getSurgeDescription());
        
        StockAlertDTO<?> dto14 = new StockAlertDTO<>(null, 14);
        assertEquals("较昨日显著上升", dto14.getSurgeDescription());
    }

    @Test
    @DisplayName("测试激增比例描述 - <10次(一般)")
    void testSurgeDescription_Low() {
        StockAlertDTO<?> dto0 = new StockAlertDTO<>(null, 0);
        assertEquals("活跃度一般", dto0.getSurgeDescription());
        
        StockAlertDTO<?> dto5 = new StockAlertDTO<>(null, 5);
        assertEquals("活跃度一般", dto5.getSurgeDescription());
        
        StockAlertDTO<?> dto9 = new StockAlertDTO<>(null, 9);
        assertEquals("活跃度一般", dto9.getSurgeDescription());
    }

    @Test
    @DisplayName("测试数据对象完整创建 - USStockRss")
    void testStockAlertDTO_CreationWithUSStock() {
        USStockRss stock = new USStockRss();
        stock.setStockCode("NVDA");
        stock.setTitle("AI Chip Breakthrough");
        stock.setTitleZh("AI芯片突破");
        
        StockAlertDTO<USStockRss> dto = new StockAlertDTO<>(stock, 28);
        
        assertEquals(stock, dto.getStock());
        assertEquals(28, dto.getFrequency());
        assertEquals("极度活跃", dto.getActivityLevel());
        assertEquals("warning", dto.getColorTag());
        assertEquals("较昨日激增 400%+", dto.getSurgeDescription());
        
        // 验证股票数据
        assertEquals("NVDA", dto.getStock().getStockCode());
        assertEquals("AI芯片突破", dto.getStock().getTitleZh());
    }

    @Test
    @DisplayName("测试数据对象完整创建 - AStockRss")
    void testStockAlertDTO_CreationWithAStock() {
        AStockRss stock = new AStockRss();
        stock.setStockCode("601127");
        stock.setStockName("赛力斯");
        stock.setTitle("交付量创历史新高");
        
        StockAlertDTO<AStockRss> dto = new StockAlertDTO<>(stock, 19);
        
        assertEquals(stock, dto.getStock());
        assertEquals(19, dto.getFrequency());
        assertEquals("高度活跃", dto.getActivityLevel());
        assertEquals("info", dto.getColorTag());
        assertEquals("较昨日激增 200%+", dto.getSurgeDescription());
        
        // 验证股票数据
        assertEquals("601127", dto.getStock().getStockCode());
        assertEquals("赛力斯", dto.getStock().getStockName());
    }

    @Test
    @DisplayName("测试边界值 - 阈值临界点")
    void testBoundaryValues() {
        // 9次 -> 轻度活跃
        StockAlertDTO<?> dto9 = new StockAlertDTO<>(null, 9);
        assertEquals("轻度活跃", dto9.getActivityLevel());
        assertEquals("comment", dto9.getColorTag());
        assertEquals("活跃度一般", dto9.getSurgeDescription());
        
        // 10次 -> 中度活跃（我们的过滤阈值）
        StockAlertDTO<?> dto10 = new StockAlertDTO<>(null, 10);
        assertEquals("中度活跃", dto10.getActivityLevel());
        assertEquals("success", dto10.getColorTag());
        assertEquals("较昨日显著上升", dto10.getSurgeDescription());
        
        // 14次 -> 中度活跃
        StockAlertDTO<?> dto14 = new StockAlertDTO<>(null, 14);
        assertEquals("中度活跃", dto14.getActivityLevel());
        
        // 15次 -> 高度活跃
        StockAlertDTO<?> dto15 = new StockAlertDTO<>(null, 15);
        assertEquals("高度活跃", dto15.getActivityLevel());
        assertEquals("info", dto15.getColorTag());
        assertEquals("较昨日激增 200%+", dto15.getSurgeDescription());
        
        // 24次 -> 高度活跃
        StockAlertDTO<?> dto24 = new StockAlertDTO<>(null, 24);
        assertEquals("高度活跃", dto24.getActivityLevel());
        
        // 25次 -> 极度活跃
        StockAlertDTO<?> dto25 = new StockAlertDTO<>(null, 25);
        assertEquals("极度活跃", dto25.getActivityLevel());
        assertEquals("warning", dto25.getColorTag());
        assertEquals("较昨日激增 400%+", dto25.getSurgeDescription());
    }

    @Test
    @DisplayName("测试A股事件评分等级口径与Prompt一致")
    void testSignalLevel_ForAStockScoring() {
        StockAlertDTO<?> dto59 = new StockAlertDTO<>(null, 0, 59, 0, "中性");
        assertEquals("常规事项", dto59.getSignalLevel());
        assertEquals("comment", dto59.getSignalColorTag());

        StockAlertDTO<?> dto60 = new StockAlertDTO<>(null, 0, 60, 1, "中性");
        assertEquals("边际催化", dto60.getSignalLevel());
        assertEquals("success", dto60.getSignalColorTag());

        StockAlertDTO<?> dto80 = new StockAlertDTO<>(null, 0, 80, 1, "利多");
        assertEquals("高优先级", dto80.getSignalLevel());
        assertEquals("info", dto80.getSignalColorTag());

        StockAlertDTO<?> dto110 = new StockAlertDTO<>(null, 0, 110, 2, "利多");
        assertEquals("主线级", dto110.getSignalLevel());
        assertEquals("warning", dto110.getSignalColorTag());
    }

    @Test
    @DisplayName("测试A股利空事件优先使用warning颜色")
    void testSignalColorTag_BearishAlwaysWarning() {
        StockAlertDTO<?> dto = new StockAlertDTO<>(null, 0, 80, 1, "利空");
        assertEquals("高优先级", dto.getSignalLevel());
        assertEquals("warning", dto.getSignalColorTag());
    }
}
