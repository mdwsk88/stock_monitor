-- ========================================================
-- 美股和A股监控系统 - 数据库初始化脚本
-- ========================================================

-- --------------------------------------------------------
-- 1. 美股异动公告信息表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `us_stock_rss`;

CREATE TABLE `us_stock_rss` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `stock_code` varchar(20) DEFAULT NULL COMMENT '股票代码',
  `title` varchar(500) DEFAULT NULL COMMENT '英文标题',
  `title_zh` varchar(500) DEFAULT NULL COMMENT '中文标题',
  `link` varchar(500) DEFAULT NULL COMMENT '公告链接',
  `pub_date_gmt` datetime DEFAULT NULL COMMENT 'GMT发布时间',
  `pub_date_bj` datetime DEFAULT NULL COMMENT '北京时间',
  `tags` varchar(200) DEFAULT NULL COMMENT '标签',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock_code_link` (`stock_code`,`link`),
  KEY `idx_stock_code` (`stock_code`),
  KEY `idx_pub_date_gmt` (`pub_date_gmt`),
  KEY `idx_pub_date_gmt_stock_code` (`pub_date_gmt`,`stock_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='美股异动公告信息表';


-- --------------------------------------------------------
-- 2. A股公告信息表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `a_stock_rss`;

CREATE TABLE `a_stock_rss` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `art_code` varchar(32) DEFAULT NULL COMMENT '东方财富公告ID',
  `stock_code` varchar(20) DEFAULT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `title` varchar(500) DEFAULT NULL COMMENT '公告标题',
  `tag` varchar(255) DEFAULT NULL COMMENT '公告分类标签',
  `link` varchar(500) DEFAULT NULL COMMENT '公告链接',
  `pub_date` datetime DEFAULT NULL COMMENT '发布时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `event_type` varchar(50) DEFAULT NULL COMMENT '事件类型',
  `signal_side` varchar(20) DEFAULT NULL COMMENT '信号方向：利多/利空/中性',
  `signal_score` int DEFAULT 0 COMMENT '规则信号分',
  `cluster_key` varchar(120) DEFAULT NULL COMMENT '批量公告聚类键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock_code_art_code` (`stock_code`,`art_code`),
  KEY `idx_stock_code` (`stock_code`),
  KEY `idx_pub_date` (`pub_date`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_pub_date_stock_code` (`pub_date`,`stock_code`),
  KEY `idx_signal_score` (`signal_score`),
  KEY `idx_cluster_key` (`cluster_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A股公告信息表';
