package com.dawei.service.impl;

import com.dawei.entity.AStockPushDecisionLog;
import com.dawei.mapper.AStockPushDecisionLogMapper;
import com.dawei.service.AStockPushDecisionLogService;
import org.springframework.stereotype.Service;

/**
 * A股实时推送决策日志服务实现
 */
@Service
public class AStockPushDecisionLogServiceImpl implements AStockPushDecisionLogService {

    private final AStockPushDecisionLogMapper aStockPushDecisionLogMapper;

    public AStockPushDecisionLogServiceImpl(AStockPushDecisionLogMapper aStockPushDecisionLogMapper) {
        this.aStockPushDecisionLogMapper = aStockPushDecisionLogMapper;
    }

    @Override
    public void recordDecision(AStockPushDecisionLog decisionLog) {
        if (decisionLog == null) {
            return;
        }
        aStockPushDecisionLogMapper.insert(decisionLog);
    }
}
