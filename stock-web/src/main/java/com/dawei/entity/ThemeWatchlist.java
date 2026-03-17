package com.dawei.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 主题与标的人工映射表
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("a_theme_watchlist")
public class ThemeWatchlist {

    private String id;
    private String themeName;
    private String stockCode;
    private String stockName;
    private Integer priority;
    private Integer enabled;
    private String reason;
    private LocalDateTime createTime;
}
