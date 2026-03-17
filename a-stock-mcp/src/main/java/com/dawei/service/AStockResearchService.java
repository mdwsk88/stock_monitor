package com.dawei.service;

import com.dawei.dto.AStockEventCard;
import com.dawei.dto.AStockSignalSummary;
import com.dawei.dto.StockResolveResult;
import com.dawei.entity.AStockRss;

import java.util.List;

public interface AStockResearchService {

    List<StockResolveResult> resolveStocks(String stockQuery, Integer limit);

    AStockSignalSummary getStockSignalSummary(String stockQuery, Integer days);

    List<AStockEventCard> getRecentEventCards(String stockQuery, Integer days, Integer minSignalScore, Integer limit);

    List<AStockEventCard> getOpportunityBoard(Integer hours, Integer minSignalScore, Integer limit);

    List<AStockEventCard> getRiskBoard(Integer hours, Integer minSignalScore, Integer limit);

    List<AStockRss> queryRawAStockNotices(String stockQuery, Integer days, Integer limit);
}
