package com.dawei.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 宏观主题事件与股票映射关系
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("a_macro_theme_stock_rel")
public class MacroThemeStockRel {

    private String id;
    private String themeEventId;
    private String themeName;
    private String stockCode;
    private String stockName;
    private Integer confidence;
    private String matchType;
    private String reason;
    private LocalDateTime createTime;
}
