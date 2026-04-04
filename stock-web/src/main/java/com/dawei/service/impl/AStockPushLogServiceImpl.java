package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.AStockPushLog;
import com.dawei.entity.AStockPushType;
import com.dawei.mapper.AStockPushLogMapper;
import com.dawei.service.AStockPushLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A股实时推送日志服务实现
 */
@Service
public class AStockPushLogServiceImpl implements AStockPushLogService {

    private final AStockPushLogMapper aStockPushLogMapper;

    public AStockPushLogServiceImpl(AStockPushLogMapper aStockPushLogMapper) {
        this.aStockPushLogMapper = aStockPushLogMapper;
    }

    @Override
    public boolean hasRecentPush(String pushKey, AStockPushType pushType, LocalDateTime since) {
        if (pushKey == null || pushKey.isBlank() || pushType == null || since == null) {
            return false;
        }
        QueryWrapper<AStockPushLog> queryWrapper = new QueryWrapper<AStockPushLog>()
                .eq("push_key", pushKey)
                .eq("push_type", pushType.name())
                .ge("pushed_at", since)
                .last("LIMIT 1");
        return aStockPushLogMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public AStockPushLog findLatestPush(List<AStockPushType> pushTypes, LocalDateTime since) {
        if (pushTypes == null || pushTypes.isEmpty() || since == null) {
            return null;
        }
        QueryWrapper<AStockPushLog> queryWrapper = new QueryWrapper<AStockPushLog>()
                .in("push_type", pushTypes.stream().map(Enum::name).toList())
                .ge("pushed_at", since)
                .orderByDesc("pushed_at")
                .last("LIMIT 1");
        return aStockPushLogMapper.selectOne(queryWrapper);
    }

    @Override
    public void recordPush(AStockPushLog pushLog) {
        if (pushLog == null) {
            return;
        }
        aStockPushLogMapper.insert(pushLog);
    }
}
