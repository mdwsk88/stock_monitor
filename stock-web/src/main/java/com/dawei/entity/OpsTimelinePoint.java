package com.dawei.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运营看板时间线点位。
 */
@Data
public class OpsTimelinePoint {

    private LocalDateTime bucketStart;
    private int highSignalNoticeCount;
    private int decisionCount;
    private int pushCount;
    private int macroEventCount;
}
