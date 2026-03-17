package com.dawei.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dawei.entity.MacroNewsRaw;
import org.apache.ibatis.annotations.Insert;

/**
 * 宏观新闻原始表 Mapper
 */
public interface MacroNewsRawMapper extends BaseMapper<MacroNewsRaw> {

    @Insert("""
        INSERT IGNORE INTO a_macro_news_raw (
            id, source_name, source_type, news_key, title, content, link, source_tags, pub_date, create_time
        ) VALUES (
            #{id}, #{sourceName}, #{sourceType}, #{newsKey}, #{title}, #{content}, #{link}, #{sourceTags}, #{pubDate}, #{createTime}
        )
        """)
    int insertIgnore(MacroNewsRaw raw);
}
