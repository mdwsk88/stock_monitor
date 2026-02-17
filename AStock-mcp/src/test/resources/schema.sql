-- A股RSS数据表
CREATE TABLE IF NOT EXISTS a_stock_rss (
    id VARCHAR(64) PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(100),
    title VARCHAR(500),
    link VARCHAR(500),
    pub_date TIMESTAMP,
    tag VARCHAR(100)
);

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_stock_code ON a_stock_rss(stock_code);
CREATE INDEX IF NOT EXISTS idx_stock_name ON a_stock_rss(stock_name);
CREATE INDEX IF NOT EXISTS idx_pub_date ON a_stock_rss(pub_date);
