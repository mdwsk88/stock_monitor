package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @ClassName StockCounts
 * @Author dawei
 * @Version 1.0
 * @Description 股票统计次数实体类
 **/
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class StockCounts {

    private String stockCode;
    private String stockName;
    private String occurCounts;

}
