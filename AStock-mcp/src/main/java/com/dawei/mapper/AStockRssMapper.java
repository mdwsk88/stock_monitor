package com.dawei.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dawei.entity.AStockRss;
import com.dawei.entity.StockCounts;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @ClassName AStockRssMapper
 * @Author 风间影月
 * @Version 1.0
 * @Description A股RSS数据Mapper
 **/
public interface AStockRssMapper extends BaseMapper<AStockRss> {

    /**
     * 查询指定日期段内股票出现次数统计
     */
    List<StockCounts> queryStockCountsBetweenDate(@Param("paramMap") Map<String, Object> map);

    /**
     * 根据标题关键字查询股票数据
     */
    List<AStockRss> queryStockByTitleKeywords(@Param("keywords") List<String> titleKeywords);

    /**
     * 根据股票名称关键字查询
     */
    List<AStockRss> queryStockByNameKeywords(@Param("keywords") List<String> nameKeywords);

}
