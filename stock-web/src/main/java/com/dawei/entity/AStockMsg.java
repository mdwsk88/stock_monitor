package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @ClassName AStockMsg
 * @Author dawei
 * @Version 1.0
 * @Description A股公告消息展示类
 **/
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AStockMsg {

    private String stockCode;
    private String stockName;
    private String title;
    private String tag;
    private String pubDate;

    private Integer counts24Hour;       // 某只股票在24小时内的公告次数
    private Integer counts3Day;         // 3天内的公告次数
    private Integer counts1Week;        // 1周内的公告次数

}
