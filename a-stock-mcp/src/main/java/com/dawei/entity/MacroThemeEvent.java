package com.dawei.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("a_macro_theme_event")
public class MacroThemeEvent {

    private String id;
    private String sourceName;
    private String sourceType;
    private String newsKey;
    private String title;
    private String summary;
    private String link;
    private String sourceTags;
    private LocalDateTime pubDate;
    private LocalDateTime createTime;
    private String themeName;
    private String eventType;
    private String signalSide;
    private Integer signalScore;
    private Integer importanceLevel;
    private String clusterKey;
}
