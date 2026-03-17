package com.dawei.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dawei.entity.MacroThemeStockRel;
import org.apache.ibatis.annotations.Insert;

/**
 * 宏观主题事件与股票映射表 Mapper
 */
public interface MacroThemeStockRelMapper extends BaseMapper<MacroThemeStockRel> {

    @Insert("""
        INSERT IGNORE INTO a_macro_theme_stock_rel (
            id, theme_event_id, theme_name, stock_code, stock_name, confidence, match_type, reason, create_time
        ) VALUES (
            #{id}, #{themeEventId}, #{themeName}, #{stockCode}, #{stockName}, #{confidence}, #{matchType}, #{reason}, #{createTime}
        )
        """)
    int insertIgnore(MacroThemeStockRel rel);
}
