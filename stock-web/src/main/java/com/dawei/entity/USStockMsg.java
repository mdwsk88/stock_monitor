package com.dawei.entity;

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
 * @Description USStockRss
 **/
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class USStockMsg {

    private String stockCode;

    private String title;
    private String titleZh;

    private String pubDateBj;
    private String tags;

    private Integer counts24Hour;       // 某只股票在24小时内的异动次数（数据库中保存的记录数）
    private Integer counts3Day;         // 3天内的异动次数
    private Integer counts1Week;        // 1周内的异动次数

}
