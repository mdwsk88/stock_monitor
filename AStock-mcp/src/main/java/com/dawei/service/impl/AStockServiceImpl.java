package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.AStockRss;
import com.dawei.entity.StockCounts;
import com.dawei.mapper.AStockRssMapper;
import com.dawei.service.AStockService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ClassName AStockServiceImpl
 * @Author dawei
 * @Version 1.0
 * @Description A股数据服务实现类
 **/
@Service
public class AStockServiceImpl implements AStockService {

    @Resource
    private AStockRssMapper aStockRssMapper;

    @Override
    public List<AStockRss> queryStock(String stockCode) {
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("stock_code", stockCode);
        queryWrapper.orderByDesc("pub_date");
        return aStockRssMapper.selectList(queryWrapper);
    }

    @Override
    public List<AStockRss> queryStockByName(String stockName) {
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("stock_name", stockName);
        queryWrapper.orderByDesc("pub_date");
        return aStockRssMapper.selectList(queryWrapper);
    }

    @Override
    public List<AStockRss> queryStockBetweenDate(String stockCode, String startDate, String endDate) {
        QueryWrapper<AStockRss> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("stock_code", stockCode);
        queryWrapper.between("pub_date", startDate, endDate);
        queryWrapper.orderByDesc("pub_date");
        return aStockRssMapper.selectList(queryWrapper);
    }

    @Override
    public List<StockCounts> queryStockCountsBetweenDate(Integer targetCounts, String startDate, String endDate) {
        Map<String, Object> map = new HashMap<>();
        map.put("targetCounts", targetCounts);
        map.put("startDate", startDate);
        map.put("endDate", endDate);
        return aStockRssMapper.queryStockCountsBetweenDate(map);
    }

    @Override
    public List<AStockRss> queryStockByTitleKeywords(List<String> titleKeywords) {
        return aStockRssMapper.queryStockByTitleKeywords(titleKeywords);
    }

    @Override
    public List<AStockRss> queryStockByNameKeywords(List<String> nameKeywords) {
        return aStockRssMapper.queryStockByNameKeywords(nameKeywords);
    }

}
