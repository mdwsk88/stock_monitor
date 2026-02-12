package com.dawei.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dawei.entity.StockCounts;
import com.dawei.entity.USStockRss;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @ClassName USStockRssMapper
 * @Author 风间影月
 * @Version 1.0
 * @Description USStockRssMapper
 **/
public interface USStockRssMapper extends BaseMapper<USStockRss> {

    public List<StockCounts> queryStockCountsBetweenDate(@Param("paramMap") Map<String, Object> map);

    public List<USStockRss> queryStockByTitleKeywords(@Param("keywords") List<String> titleKeywords);

}
