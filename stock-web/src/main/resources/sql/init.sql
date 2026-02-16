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
  KEY `idx_stock_code` (`stock_code`),
  KEY `idx_pub_date_gmt` (`pub_date_gmt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='美股异动公告信息表';


-- --------------------------------------------------------
-- 2. A股公告信息表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `a_stock_rss`;

CREATE TABLE `a_stock_rss` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `stock_code` varchar(20) DEFAULT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `title` varchar(500) DEFAULT NULL COMMENT '公告标题',
  `tag` varchar(100) DEFAULT NULL COMMENT '公告分类标签',
  `link` varchar(500) DEFAULT NULL COMMENT '公告链接',
  `pub_date` datetime DEFAULT NULL COMMENT '发布时间',
  PRIMARY KEY (`id`),
  KEY `idx_stock_code` (`stock_code`),
  KEY `idx_pub_date` (`pub_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A股公告信息表';
