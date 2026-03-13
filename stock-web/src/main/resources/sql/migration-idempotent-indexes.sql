-- ========================================================
-- 幂等写入与聚合查询索引迁移脚本
-- 执行前请先在目标库备份。
-- ========================================================

-- --------------------------------------------------------
-- 1. 清理美股重复记录：同一 stock_code + link 只保留一条
-- --------------------------------------------------------
DELETE t1
FROM us_stock_rss t1
JOIN us_stock_rss t2
  ON t1.stock_code = t2.stock_code
 AND t1.link = t2.link
 AND t1.id > t2.id;

ALTER TABLE us_stock_rss
  ADD UNIQUE KEY uk_stock_code_link (stock_code, link),
  ADD KEY idx_pub_date_gmt_stock_code (pub_date_gmt, stock_code);

-- --------------------------------------------------------
-- 2. 清理A股重复记录：同一 stock_code + title + pub_date 只保留一条
-- --------------------------------------------------------
DELETE t1
FROM a_stock_rss t1
JOIN a_stock_rss t2
  ON t1.stock_code = t2.stock_code
 AND t1.title = t2.title
 AND t1.pub_date = t2.pub_date
 AND t1.id > t2.id;

ALTER TABLE a_stock_rss
  ADD UNIQUE KEY uk_stock_code_title_pub_date (stock_code, title, pub_date),
  ADD KEY idx_pub_date_stock_code (pub_date, stock_code);
