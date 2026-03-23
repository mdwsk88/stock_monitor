package com.dawei.service;

import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketState;

/**
 * 市场状态服务。
 */
public interface MarketStateService {

    MarketSnapshot getLatestSnapshot();

    MarketSnapshot refreshSnapshot();

    MarketState resolveState(MarketSnapshot snapshot);
}
