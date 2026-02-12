package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @ClassName StockCounts
 * @Author 风间影月
 * @Version 1.0
 * @Description StockCounts
 **/
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class StockCounts {

    private String stockCode;       // 股票代码
    private String occurCounts;     // 异动次数

}
