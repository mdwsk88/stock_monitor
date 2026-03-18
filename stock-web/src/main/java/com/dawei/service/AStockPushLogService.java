package com.dawei.service;

import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;

import java.time.LocalDateTime;

/**
 * A股实时推送日志服务
 */
public interface AStockPushLogService {

    boolean hasRecentPush(String pushKey, AStockPushType pushType, LocalDateTime since);

    void recordPush(AStockPushLog pushLog);
}
