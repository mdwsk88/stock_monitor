package com.dawei.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * @ClassName USStockRss
 * @Author 风间影月
 * @Version 1.0
 * @Description USStockRss
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
