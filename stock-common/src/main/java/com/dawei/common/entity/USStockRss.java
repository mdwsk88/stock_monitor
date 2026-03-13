package com.dawei.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * @ClassName USStockRss
 * @Author dawei
 * @Version 1.0
 * @Description 美股RSS实体类 - 公共模块
 **/
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("us_stock_rss")
public class USStockRss {

    private String id;
    private String stockCode;
    private String title;
    private String titleZh;
    private String link;
    private LocalDateTime pubDateGmt;
    private LocalDateTime pubDateBj;
    private String tags;

}
