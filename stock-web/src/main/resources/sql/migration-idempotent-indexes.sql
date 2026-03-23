-- ========================================================
-- 幂等写入与聚合查询索引迁移脚本
-- 执行前请先在目标库备份。
-- 支持重复执行：已存在的列/索引会自动跳过。
-- ========================================================

SET @schema_name = DATABASE();

-- --------------------------------------------------------
-- 1. 清理美股重复记录：同一 stock_code + link 只保留一条
-- --------------------------------------------------------
DELETE t1
FROM us_stock_rss t1
JOIN us_stock_rss t2
  ON t1.stock_code = t2.stock_code
 AND t1.link = t2.link
 AND t1.id > t2.id;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'us_stock_rss'
          AND index_name = 'uk_stock_code_link'
    ),
    'SELECT ''skip uk_stock_code_link''',
    'ALTER TABLE us_stock_rss ADD UNIQUE KEY uk_stock_code_link (stock_code, link)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- --------------------------------------------------------
-- 4. 宏观线新增表
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `a_macro_news_raw` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `source_name` varchar(50) NOT NULL COMMENT '来源名称',
  `source_type` varchar(20) NOT NULL COMMENT '来源类型：OFFICIAL/QUICK',
  `news_key` varchar(120) NOT NULL COMMENT '来源内唯一键',
  `title` varchar(500) DEFAULT NULL COMMENT '标题',
  `content` mediumtext DEFAULT NULL COMMENT '正文或摘要',
  `link` varchar(500) DEFAULT NULL COMMENT '原文链接',
  `source_tags` varchar(255) DEFAULT NULL COMMENT '来源标签',
  `pub_date` datetime DEFAULT NULL COMMENT '发布时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_name_news_key` (`source_name`,`news_key`),
  KEY `idx_pub_date` (`pub_date`),
  KEY `idx_source_type_pub_date` (`source_type`,`pub_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宏观新闻原始表';

CREATE TABLE IF NOT EXISTS `a_macro_theme_event` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `source_name` varchar(50) NOT NULL COMMENT '来源名称',
  `source_type` varchar(20) NOT NULL COMMENT '来源类型：OFFICIAL/QUICK',
  `news_key` varchar(120) NOT NULL COMMENT '来源内唯一键',
  `title` varchar(500) DEFAULT NULL COMMENT '标题',
  `summary` varchar(500) DEFAULT NULL COMMENT '主题摘要',
  `link` varchar(500) DEFAULT NULL COMMENT '原文链接',
  `source_tags` varchar(255) DEFAULT NULL COMMENT '来源标签',
  `pub_date` datetime DEFAULT NULL COMMENT '发布时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `theme_name` varchar(50) DEFAULT NULL COMMENT '主题名称',
  `event_type` varchar(50) DEFAULT NULL COMMENT '事件类型',
  `signal_side` varchar(20) DEFAULT NULL COMMENT '信号方向：利多/利空/中性',
  `signal_score` int DEFAULT 0 COMMENT '主题评分',
  `importance_level` int DEFAULT 1 COMMENT '重要级别：1-5',
  `cluster_key` varchar(160) DEFAULT NULL COMMENT '主题聚类键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_macro_source_news_key` (`source_name`,`news_key`),
  KEY `idx_macro_pub_date` (`pub_date`),
  KEY `idx_macro_signal_score` (`signal_score`),
  KEY `idx_macro_cluster_key` (`cluster_key`),
  KEY `idx_macro_theme_name` (`theme_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宏观主题事件表';

CREATE TABLE IF NOT EXISTS `a_macro_theme_stock_rel` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `theme_event_id` varchar(32) NOT NULL COMMENT '主题事件ID',
  `theme_name` varchar(50) DEFAULT NULL COMMENT '主题名称',
  `stock_code` varchar(20) NOT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `confidence` int DEFAULT 0 COMMENT '匹配置信度',
  `match_type` varchar(20) DEFAULT NULL COMMENT '匹配方式：WATCHLIST/EXPLICIT',
  `reason` varchar(255) DEFAULT NULL COMMENT '匹配原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_macro_event_stock_match` (`theme_event_id`,`stock_code`,`match_type`),
  KEY `idx_macro_event_id` (`theme_event_id`),
  KEY `idx_macro_theme_stock` (`theme_name`,`stock_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宏观主题事件与股票映射关系表';

CREATE TABLE IF NOT EXISTS `a_theme_watchlist` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `theme_name` varchar(50) NOT NULL COMMENT '主题名称',
  `stock_code` varchar(20) NOT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `priority` int DEFAULT 0 COMMENT '优先级',
  `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用',
  `reason` varchar(255) DEFAULT NULL COMMENT '映射原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_theme_stock_code` (`theme_name`,`stock_code`),
  KEY `idx_enabled_theme_name` (`enabled`,`theme_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主题观察池映射表';

CREATE TABLE IF NOT EXISTS `a_theme_auto_pool` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `theme_name` varchar(50) NOT NULL COMMENT '主题名称',
  `stock_code` varchar(20) NOT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `candidate_score` int DEFAULT 0 COMMENT '候选分数',
  `hit_count` int DEFAULT 0 COMMENT '累计命中次数',
  `enabled` tinyint(1) DEFAULT 0 COMMENT '是否自动启用',
  `reason` varchar(255) DEFAULT NULL COMMENT '自动入池原因',
  `latest_pub_date` datetime DEFAULT NULL COMMENT '最近命中时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_theme_auto_pool` (`theme_name`,`stock_code`),
  KEY `idx_auto_pool_enabled_theme` (`enabled`,`theme_name`),
  KEY `idx_auto_pool_latest_pub_date` (`latest_pub_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主题自动候选池';

CREATE TABLE IF NOT EXISTS `a_stock_push_decision_log` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `push_key` varchar(180) DEFAULT NULL COMMENT '实时推送去重键',
  `stock_code` varchar(20) DEFAULT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `push_type` varchar(40) DEFAULT NULL COMMENT '最终推送类型',
  `signal_side` varchar(20) DEFAULT NULL COMMENT '信号方向：利多/利空/中性',
  `signal_score` int DEFAULT 0 COMMENT '公告信号分',
  `event_type` varchar(50) DEFAULT NULL COMMENT '事件类型',
  `title` varchar(500) DEFAULT NULL COMMENT '代表公告标题',
  `market_state` varchar(20) DEFAULT NULL COMMENT '市场状态',
  `should_send_realtime` tinyint(1) DEFAULT 0 COMMENT '是否进入实时推送',
  `critical` tinyint(1) DEFAULT 0 COMMENT '是否核弹级事件',
  `within_trading_session` tinyint(1) DEFAULT 0 COMMENT '是否命中交易时段',
  `cooldown_hit` tinyint(1) DEFAULT 0 COMMENT '是否命中冷却期',
  `send_status` varchar(20) DEFAULT NULL COMMENT '最终结果：SKIPPED/SENT/FAILED',
  `failure_reason` varchar(255) DEFAULT NULL COMMENT '发送失败原因',
  `macro_theme_name` varchar(50) DEFAULT NULL COMMENT '命中的宏观主题',
  `resonance_score` int DEFAULT 0 COMMENT '共振分',
  `position_label` varchar(32) DEFAULT NULL COMMENT '身位标签',
  `decision_reason` varchar(255) DEFAULT NULL COMMENT '决策原因',
  `event_time` datetime DEFAULT NULL COMMENT '公告事件时间',
  `decided_at` datetime DEFAULT NULL COMMENT '做出决策时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_push_decision_time` (`decided_at`),
  KEY `idx_push_decision_stock_time` (`stock_code`,`decided_at`),
  KEY `idx_push_decision_status_time` (`send_status`,`decided_at`),
  KEY `idx_push_decision_key_time` (`push_key`,`decided_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A股实时推送决策日志';

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'us_stock_rss'
          AND index_name = 'idx_pub_date_gmt_stock_code'
    ),
    'SELECT ''skip idx_pub_date_gmt_stock_code''',
    'ALTER TABLE us_stock_rss ADD KEY idx_pub_date_gmt_stock_code (pub_date_gmt, stock_code)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- --------------------------------------------------------
-- 2. A股补字段：先补列，再去重，再补索引
-- --------------------------------------------------------
SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'a_stock_rss'
          AND column_name = 'art_code'
    ),
    'SELECT ''skip column art_code''',
    'ALTER TABLE a_stock_rss ADD COLUMN art_code varchar(32) DEFAULT NULL COMMENT ''东方财富公告ID'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'a_stock_rss'
          AND column_name = 'event_type'
    ),
    'SELECT ''skip column event_type''',
    'ALTER TABLE a_stock_rss ADD COLUMN event_type varchar(50) DEFAULT NULL COMMENT ''事件类型'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'a_stock_rss'
          AND column_name = 'signal_side'
    ),
    'SELECT ''skip column signal_side''',
    'ALTER TABLE a_stock_rss ADD COLUMN signal_side varchar(20) DEFAULT NULL COMMENT ''信号方向：利多/利空/中性'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'a_stock_rss'
          AND column_name = 'signal_score'
    ),
    'SELECT ''skip column signal_score''',
    'ALTER TABLE a_stock_rss ADD COLUMN signal_score int DEFAULT 0 COMMENT ''规则信号分'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'a_stock_rss'
          AND column_name = 'cluster_key'
    ),
    'SELECT ''skip column cluster_key''',
    'ALTER TABLE a_stock_rss ADD COLUMN cluster_key varchar(120) DEFAULT NULL COMMENT ''批量公告聚类键'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE a_stock_rss
  MODIFY COLUMN tag varchar(255) DEFAULT NULL COMMENT '公告分类标签';

-- --------------------------------------------------------
-- 3. 清理A股重复记录：优先按 stock_code + art_code 去重
-- --------------------------------------------------------
DELETE t1
FROM a_stock_rss t1
JOIN a_stock_rss t2
  ON t1.stock_code = t2.stock_code
 AND (
      (t1.art_code IS NOT NULL AND t1.art_code <> '' AND t1.art_code = t2.art_code)
      OR (
          (t1.art_code IS NULL OR t1.art_code = '')
      AND (t2.art_code IS NULL OR t2.art_code = '')
      AND t1.title = t2.title
      AND t1.pub_date = t2.pub_date
      )
 )
 AND t1.id > t2.id;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'a_stock_rss'
          AND index_name = 'uk_stock_code_art_code'
    ),
    'SELECT ''skip uk_stock_code_art_code''',
    'ALTER TABLE a_stock_rss ADD UNIQUE KEY uk_stock_code_art_code (stock_code, art_code)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'a_stock_rss'
          AND index_name = 'idx_pub_date_stock_code'
    ),
    'SELECT ''skip idx_pub_date_stock_code''',
    'ALTER TABLE a_stock_rss ADD KEY idx_pub_date_stock_code (pub_date, stock_code)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'a_stock_rss'
          AND index_name = 'idx_signal_score'
    ),
    'SELECT ''skip idx_signal_score''',
    'ALTER TABLE a_stock_rss ADD KEY idx_signal_score (signal_score)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'a_stock_rss'
          AND index_name = 'idx_cluster_key'
    ),
    'SELECT ''skip idx_cluster_key''',
    'ALTER TABLE a_stock_rss ADD KEY idx_cluster_key (cluster_key)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
