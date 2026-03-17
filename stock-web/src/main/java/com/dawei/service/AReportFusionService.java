package com.dawei.service;

import com.dawei.entity.AReportFusionContext;

import java.time.LocalDateTime;

/**
 * A股报告融合服务：将公告线与宏观主题线拼成统一上下文
 */
public interface AReportFusionService {

    AReportFusionContext buildContext(LocalDateTime startTime,
                                      LocalDateTime endTime,
                                      int stockLimit,
                                      int macroThemeLimit,
                                      int resonanceLimit);
}
