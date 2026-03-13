package com.dawei.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dawei.entity.AStockRss;
import com.dawei.entity.AStockRssAggregate;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @ClassName AStockRssMapper
 * @Author dawei
 * @Version 1.0
 * @Description A股公告信息Mapper
 **/
public interface AStockRssMapper extends BaseMapper<AStockRss> {

    int insertIgnore(AStockRss aStockNews);

    List<AStockRssAggregate> selectTopStocksByFrequency(@Param("startTime") String startTime,
                                                        @Param("endTime") String endTime,
                                                        @Param("threshold") int threshold,
                                                        @Param("limit") int limit,
                                                        @Param("blacklistKeywords") List<String> blacklistKeywords);

    List<AStockRss> selectFilteredByStockCodes(@Param("stockCodes") List<String> stockCodes,
                                               @Param("startTime") String startTime,
                                               @Param("endTime") String endTime,
                                               @Param("blacklistKeywords") List<String> blacklistKeywords);

}
