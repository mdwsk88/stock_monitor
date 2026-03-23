package com.dawei.service;

import com.dawei.entity.AStockPushDecisionLog;

/**
 * A股实时推送决策日志服务
 */
public interface AStockPushDecisionLogService {

    void recordDecision(AStockPushDecisionLog decisionLog);
}
