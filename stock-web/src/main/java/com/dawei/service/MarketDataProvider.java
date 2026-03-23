package com.dawei.service;

import com.dawei.entity.MarketSnapshot;

/**
 * 市场数据抓取接口。
 */
public interface MarketDataProvider {

    MarketSnapshot fetchSnapshot();
}
