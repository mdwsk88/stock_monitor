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
