package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @ClassName StockAlertDTO
 * @Author dawei
 * @Version 1.0
 * @Description 股票异动数据传输对象，包含股票信息和异动频次统计
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertDTO<T> {

    /**
     * 股票信息（USStockRss 或 AStockRss）
     */
    private T stock;

    /**
     * 异动频次（过去24小时内该股票出现的次数）
     */
    private int frequency;

    /**
     * A股事件评分。美股沿用0，保持原有频次逻辑。
     */
    private int signalScore;

    /**
     * 归并后的事件簇数量
     */
    private int eventCount;

    /**
     * A股信号方向：利多/利空/中性
     */
    private String signalSide;

    public StockAlertDTO(T stock, int frequency) {
        this(stock, frequency, 0, 0, "中性");
    }

    /**
     * 活跃度等级描述
     * 注意：由于已过滤阈值<10的记录，实际不会出现"轻度活跃"
     */
    public String getActivityLevel() {
        if (frequency >= 25) {
            return "极度活跃";
        } else if (frequency >= 15) {
            return "高度活跃";
        } else if (frequency >= 10) {
            return "中度活跃";
        } else {
            return "轻度活跃";
        }
    }

    /**
     * 获取企业微信颜色标签
     */
    public String getColorTag() {
        if (frequency >= 25) {
            return "warning";
        } else if (frequency >= 15) {
            return "info";
        } else if (frequency >= 10) {
            return "success";
        } else {
            return "comment";
        }
    }

    /**
     * 获取激增比例描述
     * 用于展示异动频次的相对变化程度
     */
    public String getSurgeDescription() {
        if (frequency >= 25) {
            return "较昨日激增 400%+";
        } else if (frequency >= 15) {
            return "较昨日激增 200%+";
        } else if (frequency >= 10) {
            return "较昨日显著上升";
        } else {
            return "活跃度一般";
        }
    }

    public String getSignalLevel() {
        if (signalScore >= 110) {
            return "主线级";
        } else if (signalScore >= 80) {
            return "高优先级";
        } else if (signalScore >= 60) {
            return "边际催化";
        } else {
            return "常规事项";
        }
    }

    public String getSignalColorTag() {
        if ("利空".equals(signalSide)) {
            return "warning";
        }
        if (signalScore >= 110) {
            return "warning";
        } else if (signalScore >= 80) {
            return "info";
        } else if (signalScore >= 60) {
            return "success";
        } else {
            return "comment";
        }
    }
}
