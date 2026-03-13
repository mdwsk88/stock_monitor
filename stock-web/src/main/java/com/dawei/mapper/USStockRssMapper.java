package com.dawei.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dawei.entity.USStockRssAggregate;
import com.dawei.entity.USStockRss;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @ClassName USStockRssMapper
 * @Author dawei
 * @Version 1.0
 * @Description USStockRssMapper
 **/
public interface USStockRssMapper extends BaseMapper<USStockRss> {

    int insertIgnore(USStockRss stockNews);

    List<USStockRssAggregate> selectTopStocksByFrequency(@Param("startTime") String startTime,
                                                         @Param("endTime") String endTime,
                                                         @Param("threshold") int threshold,
                                                         @Param("limit") int limit,
                                                         @Param("blacklistKeywords") List<String> blacklistKeywords);

    List<USStockRss> selectFilteredByStockCodes(@Param("stockCodes") List<String> stockCodes,
                                                @Param("startTime") String startTime,
                                                @Param("endTime") String endTime,
                                                @Param("blacklistKeywords") List<String> blacklistKeywords);

}
