-- A股RSS数据表
CREATE TABLE IF NOT EXISTS a_stock_rss (
    id VARCHAR(64) PRIMARY KEY,
    art_code VARCHAR(64),
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(100),
    title VARCHAR(500),
    tag VARCHAR(100),
    link VARCHAR(500),
    pub_date TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(100),
    signal_side VARCHAR(20),
    signal_score INT DEFAULT 0,
    cluster_key VARCHAR(160)
);

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_stock_code ON a_stock_rss(stock_code);
CREATE INDEX IF NOT EXISTS idx_stock_name ON a_stock_rss(stock_name);
CREATE INDEX IF NOT EXISTS idx_pub_date ON a_stock_rss(pub_date);
CREATE INDEX IF NOT EXISTS idx_signal_score ON a_stock_rss(signal_score);

-- 宏观主题事件表
CREATE TABLE IF NOT EXISTS a_macro_theme_event (
    id VARCHAR(64) PRIMARY KEY,
    source_name VARCHAR(50),
    source_type VARCHAR(20),
    news_key VARCHAR(120),
    title VARCHAR(500),
    summary VARCHAR(500),
    link VARCHAR(500),
    source_tags VARCHAR(255),
    pub_date TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    theme_name VARCHAR(50),
    event_type VARCHAR(50),
    signal_side VARCHAR(20),
    signal_score INT DEFAULT 0,
    importance_level INT DEFAULT 1,
    cluster_key VARCHAR(160)
);

CREATE INDEX IF NOT EXISTS idx_macro_theme_pub_date ON a_macro_theme_event(pub_date);
CREATE INDEX IF NOT EXISTS idx_macro_theme_score ON a_macro_theme_event(signal_score);

-- 宏观主题事件与股票映射关系表
CREATE TABLE IF NOT EXISTS a_macro_theme_stock_rel (
    id VARCHAR(64) PRIMARY KEY,
    theme_event_id VARCHAR(64) NOT NULL,
    theme_name VARCHAR(50),
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    confidence INT DEFAULT 0,
    match_type VARCHAR(20),
    reason VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_macro_rel_event_id ON a_macro_theme_stock_rel(theme_event_id);
CREATE INDEX IF NOT EXISTS idx_macro_rel_theme_stock ON a_macro_theme_stock_rel(theme_name, stock_code);

-- 主题自动候选池
CREATE TABLE IF NOT EXISTS a_theme_auto_pool (
    id VARCHAR(64) PRIMARY KEY,
    theme_name VARCHAR(50) NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    candidate_score INT DEFAULT 0,
    hit_count INT DEFAULT 0,
    enabled TINYINT DEFAULT 0,
    reason VARCHAR(255),
    latest_pub_date TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_theme_auto_pool ON a_theme_auto_pool(theme_name, stock_code);
