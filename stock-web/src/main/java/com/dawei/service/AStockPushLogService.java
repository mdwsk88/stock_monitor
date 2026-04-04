package com.dawei.service;

import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A股实时推送日志服务
 */
public interface AStockPushLogService {

    boolean hasRecentPush(String pushKey, AStockPushType pushType, LocalDateTime since);

    AStockPushLog findLatestPush(List<AStockPushType> pushTypes, LocalDateTime since);

    void recordPush(AStockPushLog pushLog);
}
