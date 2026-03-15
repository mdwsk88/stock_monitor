package com.dawei.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * @ClassName AStockRss
 * @Author dawei
 * @Version 1.0
 * @Description A股公告信息实体类
 **/
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("a_stock_rss")
public class AStockRss {

    private String id;
    private String artCode;
    private String stockCode;
    private String stockName;
    private String title;
    private String tag;
    private String link;
    private LocalDateTime pubDate;
    private LocalDateTime createTime;
    private String eventType;
    private String signalSide;
    private Integer signalScore;
    private String clusterKey;

    @TableField(exist = false)
    private String relatedTitles;

    @TableField(exist = false)
    private Integer eventCount;

    @TableField(exist = false)
    private Integer rawNoticeCount;

    @TableField(exist = false)
    private String analysisHint;

    @TableField(exist = false)
    private String clusterHighlights;

}
