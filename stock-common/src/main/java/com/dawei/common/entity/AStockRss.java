package com.dawei.common.entity;

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
 * @Description A股公告信息实体类 - 公共模块
 **/
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("a_stock_rss")
public class AStockRss {

    private String id;
    private String stockCode;
    private String stockName;
    private String title;
    private String tag;
    private String link;
    private LocalDateTime pubDate;
    private LocalDateTime createTime;

}
