package com.dawei.service.impl;

import com.dawei.entity.AStockPushDecisionLog;
import com.dawei.mapper.AStockPushDecisionLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AStockPushDecisionLogServiceImplTest {

    @Mock
    private AStockPushDecisionLogMapper aStockPushDecisionLogMapper;

    @InjectMocks
    private AStockPushDecisionLogServiceImpl service;

    @Test
    void recordDecision_ShouldIgnoreNull() {
        service.recordDecision(null);

        verify(aStockPushDecisionLogMapper, never()).insert(org.mockito.ArgumentMatchers.<AStockPushDecisionLog>any());
    }

    @Test
    void recordDecision_ShouldInsertLog() {
        AStockPushDecisionLog log = new AStockPushDecisionLog();
        log.setId("log-1");

        service.recordDecision(log);

        verify(aStockPushDecisionLogMapper).insert(log);
    }
}
