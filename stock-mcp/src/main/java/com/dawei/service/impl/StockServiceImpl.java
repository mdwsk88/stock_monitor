package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.StockCounts;
import com.dawei.entity.USStockRss;
import com.dawei.mapper.USStockRssMapper;
import com.dawei.service.StockService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ClassName StockServiceImpl
 * @Author 风间影月
 * @Version 1.0
 * @Description StockServiceImpl
 **/
@Service
public class StockServiceImpl implements StockService {

    @Resource
    private USStockRssMapper usStockRssMapper;

    @Override
    public List<USStockRss> queryStock(String stockCode) {

        QueryWrapper<USStockRss> queryWrapper = new QueryWrapper();
        queryWrapper.eq("stock_code", stockCode);

        return usStockRssMapper.selectList(queryWrapper);
    }

    @Override
    public List<USStockRss> queryStockBetweenDate(String stockCode, String startDate, String endDate) {

        QueryWrapper<USStockRss> queryWrapper = new QueryWrapper();
        queryWrapper.eq("stock_code", stockCode);
        queryWrapper.between("pub_date_bj", startDate, endDate);
        queryWrapper.orderByDesc("pub_date_bj");

        return usStockRssMapper.selectList(queryWrapper);
    }

    @Override
    public List<StockCounts> queryStockCountsBetweenDate(Integer targetCounts, String startDate, String endDate) {

        Map<String, Object> map = new HashMap<>();
        map.put("targetCounts", targetCounts);
        map.put("startDate", startDate);
        map.put("endDate", endDate);

        return usStockRssMapper.queryStockCountsBetweenDate(map);
    }

    @Override
    public List<USStockRss> queryStockByTitleKeywords(List<String> titleKeywords) {
        return usStockRssMapper.queryStockByTitleKeywords(titleKeywords);
    }
}
