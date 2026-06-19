-- ========================================================
-- 美股和A股监控系统 - 数据库初始化脚本
-- ========================================================

-- --------------------------------------------------------
-- 1. 美股异动公告信息表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `us_stock_rss`;

CREATE TABLE `us_stock_rss` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `stock_code` varchar(20) NOT NULL DEFAULT '' COMMENT '股票代码',
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


-- --------------------------------------------------------
-- 3. 宏观新闻原始表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `a_macro_news_raw`;

CREATE TABLE `a_macro_news_raw` (
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


-- --------------------------------------------------------
-- 4. 宏观主题事件表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `a_macro_theme_event`;

CREATE TABLE `a_macro_theme_event` (
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


-- --------------------------------------------------------
-- 5. 宏观主题事件与股票映射关系表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `a_macro_theme_stock_rel`;

CREATE TABLE `a_macro_theme_stock_rel` (
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


-- --------------------------------------------------------
-- 6. 主题自动候选池
-- --------------------------------------------------------
DROP TABLE IF EXISTS `a_theme_auto_pool`;

CREATE TABLE `a_theme_auto_pool` (
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


-- --------------------------------------------------------
-- 7. A股实时推送日志
-- --------------------------------------------------------
DROP TABLE IF EXISTS `a_stock_push_log`;

CREATE TABLE `a_stock_push_log` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `push_key` varchar(180) NOT NULL COMMENT '实时推送去重键',
  `stock_code` varchar(20) DEFAULT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `push_type` varchar(40) DEFAULT NULL COMMENT '推送类型：REALTIME_OPPORTUNITY/REALTIME_RISK',
  `signal_side` varchar(20) DEFAULT NULL COMMENT '信号方向：利多/利空/中性',
  `signal_score` int DEFAULT 0 COMMENT '公告信号分',
  `event_type` varchar(50) DEFAULT NULL COMMENT '事件类型',
  `title` varchar(500) DEFAULT NULL COMMENT '代表公告标题',
  `macro_theme_name` varchar(50) DEFAULT NULL COMMENT '命中的宏观主题',
  `resonance_score` int DEFAULT 0 COMMENT '共振分',
  `decision_reason` varchar(255) DEFAULT NULL COMMENT '推送决策原因',
  `pushed_at` datetime DEFAULT NULL COMMENT '实际推送时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_push_key_type_time` (`push_key`,`push_type`,`pushed_at`),
  KEY `idx_push_time` (`pushed_at`),
  KEY `idx_push_stock_time` (`stock_code`,`pushed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A股实时推送日志';


-- --------------------------------------------------------
-- 8. A股实时推送决策日志
-- --------------------------------------------------------
DROP TABLE IF EXISTS `a_stock_push_decision_log`;

CREATE TABLE `a_stock_push_decision_log` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `push_key` varchar(180) DEFAULT NULL COMMENT '实时推送去重键',
  `stock_code` varchar(20) DEFAULT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `push_type` varchar(40) DEFAULT NULL COMMENT '最终推送类型：SILENT/REPORT_ONLY/REALTIME_OPPORTUNITY/REALTIME_RISK',
  `signal_side` varchar(20) DEFAULT NULL COMMENT '信号方向：利多/利空/中性',
  `signal_score` int DEFAULT 0 COMMENT '公告信号分',
  `event_type` varchar(50) DEFAULT NULL COMMENT '事件类型',
  `title` varchar(500) DEFAULT NULL COMMENT '代表公告标题',
  `market_state` varchar(20) DEFAULT NULL COMMENT '市场状态：DEFENSIVE/NEUTRAL/RISK_ON/OVERHEAT',
  `should_send_realtime` tinyint(1) DEFAULT 0 COMMENT '是否进入实时推送',
  `critical` tinyint(1) DEFAULT 0 COMMENT '是否核弹级事件',
  `within_trading_session` tinyint(1) DEFAULT 0 COMMENT '是否命中交易时段',
  `cooldown_hit` tinyint(1) DEFAULT 0 COMMENT '是否命中冷却期',
  `send_status` varchar(20) DEFAULT NULL COMMENT '最终结果：SKIPPED/SENT/FAILED',
  `failure_reason` varchar(255) DEFAULT NULL COMMENT '发送失败原因',
  `macro_theme_name` varchar(50) DEFAULT NULL COMMENT '命中的宏观主题',
  `resonance_score` int DEFAULT 0 COMMENT '共振分',
  `position_label` varchar(32) DEFAULT NULL COMMENT '身位标签：领军核心/高弹性跟风/观察名单',
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


-- --------------------------------------------------------
-- 9. 主题观察池映射表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `a_theme_watchlist`;
DROP TABLE IF EXISTS `a_wecom_subscription`;
DROP TABLE IF EXISTS `a_wecom_feedback`;

CREATE TABLE `a_theme_watchlist` (
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

CREATE TABLE `a_wecom_subscription` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `subscription_type` varchar(20) NOT NULL COMMENT '关注类型：THEME/STOCK',
  `target_name` varchar(80) NOT NULL COMMENT '关注对象名称',
  `stock_code` varchar(20) DEFAULT NULL COMMENT '股票代码',
  `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用',
  `source` varchar(50) DEFAULT NULL COMMENT '来源：机器人/原型页/运营台',
  `reason` varchar(255) DEFAULT NULL COMMENT '关注原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wecom_subscription_target` (`subscription_type`,`target_name`,`stock_code`),
  KEY `idx_wecom_subscription_enabled` (`enabled`,`subscription_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业微信群关注项';

CREATE TABLE `a_wecom_feedback` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `feedback_type` varchar(20) NOT NULL COMMENT '反馈类型：USEFUL/NOISY/LATE/IRRELEVANT/FOLLOW/OTHER',
  `target_type` varchar(20) DEFAULT NULL COMMENT '对象类型：PUSH/THEME/STOCK',
  `target_name` varchar(100) DEFAULT NULL COMMENT '对象名称',
  `stock_code` varchar(20) DEFAULT NULL COMMENT '股票代码',
  `theme_name` varchar(80) DEFAULT NULL COMMENT '主题名称',
  `push_type` varchar(40) DEFAULT NULL COMMENT '推送类型',
  `push_key` varchar(180) DEFAULT NULL COMMENT '推送去重键',
  `source` varchar(50) DEFAULT NULL COMMENT '来源：机器人/原型页/运营台',
  `comment` varchar(500) DEFAULT NULL COMMENT '补充说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_wecom_feedback_time` (`create_time`),
  KEY `idx_wecom_feedback_type_time` (`feedback_type`,`create_time`),
  KEY `idx_wecom_feedback_target` (`target_type`,`target_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业微信群推送轻反馈';
