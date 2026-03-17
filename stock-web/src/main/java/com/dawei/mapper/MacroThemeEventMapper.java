package com.dawei.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dawei.entity.MacroThemeEvent;
import org.apache.ibatis.annotations.Insert;

/**
 * 宏观主题事件 Mapper
 */
public interface MacroThemeEventMapper extends BaseMapper<MacroThemeEvent> {

    @Insert("""
        INSERT IGNORE INTO a_macro_theme_event (
            id, source_name, source_type, news_key, title, summary, link, source_tags, pub_date, create_time,
            theme_name, event_type, signal_side, signal_score, importance_level, cluster_key
        ) VALUES (
            #{id}, #{sourceName}, #{sourceType}, #{newsKey}, #{title}, #{summary}, #{link}, #{sourceTags}, #{pubDate}, #{createTime},
            #{themeName}, #{eventType}, #{signalSide}, #{signalScore}, #{importanceLevel}, #{clusterKey}
        )
        """)
    int insertIgnore(MacroThemeEvent event);
}
