package com.dawei.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 宏观新闻原始落库记录
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("a_macro_news_raw")
public class MacroNewsRaw {

    private String id;
    private String sourceName;
    private String sourceType;
    private String newsKey;
    private String title;
    private String content;
    private String link;
    private String sourceTags;
    private LocalDateTime pubDate;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String sourceLabel;
}
