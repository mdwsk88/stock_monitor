-- 贵州茅台：9 条高价值公告，2 条噪音公告，1 条超出默认时间窗的旧公告
INSERT INTO a_stock_rss (id, art_code, stock_code, stock_name, title, tag, link, pub_date, create_time, event_type, signal_side, signal_score, cluster_key) VALUES
('1', 'MT-001', '600519', '贵州茅台', '贵州茅台2025年报业绩超预期', '业绩快报', 'http://example.com/mt/1', DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '业绩超预期', '利多', 95, '600519:earnings'),
('2', 'MT-002', '600519', '贵州茅台', '贵州茅台拟大手笔分红提升股东回报', '分红方案', 'http://example.com/mt/2', DATEADD('DAY', -2, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '分红', '利多', 92, '600519:dividend'),
('3', 'MT-003', '600519', '贵州茅台', '贵州茅台直营渠道改革成效显现', '经营更新', 'http://example.com/mt/3', DATEADD('DAY', -3, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '渠道改革', '利多', 89, '600519:channel'),
('4', 'MT-004', '600519', '贵州茅台', '贵州茅台回购注销部分限制性股票', '回购注销', 'http://example.com/mt/4', DATEADD('DAY', -4, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '回购', '利多', 86, '600519:buyback'),
('5', 'MT-005', '600519', '贵州茅台', '贵州茅台与头部平台达成战略合作', '战略合作', 'http://example.com/mt/5', DATEADD('DAY', -5, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '合作订单', '利多', 82, '600519:cooperation'),
('6', 'MT-006', '600519', '贵州茅台', '贵州茅台春节动销超预期', '销售数据', 'http://example.com/mt/6', DATEADD('DAY', -6, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '销售超预期', '利多', 79, '600519:sales'),
('23', 'MT-013', '600519', '贵州茅台', '贵州茅台直营网点持续扩容', '经营更新', 'http://example.com/mt/13', DATEADD('DAY', -2, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '渠道改革', '利多', 81, '600519:channel'),
('24', 'MT-014', '600519', '贵州茅台', '贵州茅台回购计划实施进展公告', '回购进展', 'http://example.com/mt/14', DATEADD('DAY', -3, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '回购', '利多', 80, '600519:buyback'),
('7', 'MT-007', '600519', '贵州茅台', '贵州茅台核心单品批价回升', '渠道跟踪', 'http://example.com/mt/7', DATEADD('DAY', -7, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '价格跟踪', '利多', 76, '600519:pricing'),
('8', 'MT-008', '600519', '贵州茅台', '贵州茅台新增产能建设稳步推进', '产能建设', 'http://example.com/mt/8', DATEADD('DAY', -8, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '产能扩张', '利多', 72, '600519:capacity'),
('9', 'MT-009', '600519', '贵州茅台', '贵州茅台高端酒国际渠道扩张', '国际化', 'http://example.com/mt/9', DATEADD('DAY', -9, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '渠道扩张', '利多', 68, '600519:global'),
('10', 'MT-010', '600519', '贵州茅台', '贵州茅台换发营业执照', '工商变更', 'http://example.com/mt/10', DATEADD('DAY', -10, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '行政公告', '中性', 18, '600519:admin'),
('11', 'MT-011', '600519', '贵州茅台', '贵州茅台董事会会议通知', '会议通知', 'http://example.com/mt/11', DATEADD('DAY', -11, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '会议通知', '中性', 25, '600519:board'),
('12', 'MT-012', '600519', '贵州茅台', '贵州茅台签订海外大单', '订单公告', 'http://example.com/mt/12', DATEADD('DAY', -45, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '重大订单', '利多', 90, '600519:legacy-order');

-- 平安银行：3 条高价值公告，1 条噪音公告
INSERT INTO a_stock_rss (id, art_code, stock_code, stock_name, title, tag, link, pub_date, create_time, event_type, signal_side, signal_score, cluster_key) VALUES
('13', 'PA-001', '000001', '平安银行', '平安银行零售贷款不良率继续下降', '资产质量', 'http://example.com/pa/1', DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '资产质量改善', '利多', 84, '000001:asset-quality'),
('14', 'PA-002', '000001', '平安银行', '平安银行获险资增持', '股东增持', 'http://example.com/pa/2', DATEADD('DAY', -2, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '股东增持', '利多', 78, '000001:shareholder-buy'),
('15', 'PA-003', '000001', '平安银行', '平安银行发行二级资本债获批', '资本补充', 'http://example.com/pa/3', DATEADD('DAY', -3, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '资本补充', '中性', 74, '000001:capital'),
('16', 'PA-004', '000001', '平安银行', '平安银行召开股东大会', '会议公告', 'http://example.com/pa/4', DATEADD('DAY', -4, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '会议通知', '中性', 22, '000001:meeting');

-- 其他高价值公告：用于关键词与多标的场景
INSERT INTO a_stock_rss (id, art_code, stock_code, stock_name, title, tag, link, pub_date, create_time, event_type, signal_side, signal_score, cluster_key) VALUES
('17', 'JF-001', '300308', '中际旭创', '中际旭创控股股东拟减持不超过1%', '减持计划', 'http://example.com/jf/1', DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '股东减持', '利空', 88, '300308:reduce'),
('18', 'WF-001', '002085', '万丰奥威', '万丰奥威回购股份方案获股东大会通过', '回购方案', 'http://example.com/wf/1', DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '回购', '利多', 83, '002085:buyback'),
('19', 'ZS-001', '001696', '宗申动力', '宗申动力低空经济订单落地', '重大订单', 'http://example.com/zs/1', DATEADD('DAY', -2, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '重大订单', '利多', 91, '001696:order'),
('20', 'CX-001', '300308', '中际旭创', '中际旭创算力光模块订单持续增长', '订单进展', 'http://example.com/jf/2', DATEADD('DAY', -2, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '订单增长', '利多', 87, '300308:order'),
('21', 'WF-002', '002085', '万丰奥威', '万丰奥威参股公司获低空经济大单', '订单公告', 'http://example.com/wf/2', DATEADD('DAY', -2, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '重大订单', '利多', 90, '002085:order'),
('22', 'ZS-002', '001696', '宗申动力', '宗申动力完成工商变更登记', '工商变更', 'http://example.com/zs/2', DATEADD('DAY', -3, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '行政公告', '中性', 18, '001696:admin');

-- 宏观主题事件
INSERT INTO a_macro_theme_event (id, source_name, source_type, news_key, title, summary, link, source_tags, pub_date, create_time, theme_name, event_type, signal_side, signal_score, importance_level, cluster_key) VALUES
('M1', '新华社', 'OFFICIAL', 'macro-1', '低空经济扶持政策加码', '政策进一步明确低空经济基础设施和试点城市建设。', 'http://example.com/macro/1', '政策,低空经济', DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '低空经济', '政策扶持', '利多', 93, 5, 'macro:low-altitude'),
('M2', '财联社', 'QUICK', 'macro-2', '多地发布低空经济试点方案', '地方试点推进，产业链景气度提升。', 'http://example.com/macro/2', '快讯,低空经济', DATEADD('DAY', -2, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '低空经济', '试点扩容', '利多', 90, 4, 'macro:low-altitude'),
('M3', '新华社', 'OFFICIAL', 'macro-3', '算力基础设施建设持续加码', '新一轮算力中心项目启动，光模块和服务器链条受益。', 'http://example.com/macro/3', '政策,算力', DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '算力', '产业政策', '利多', 88, 4, 'macro:compute'),
('M4', '财联社', 'QUICK', 'macro-4', '创新药集采规则趋严', '部分创新药企业短期承压，市场风险偏好下降。', 'http://example.com/macro/4', '快讯,医药', DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, '创新药', '政策扰动', '利空', 82, 3, 'macro:pharma-risk');

-- 宏观主题事件与股票映射关系
INSERT INTO a_macro_theme_stock_rel (id, theme_event_id, theme_name, stock_code, stock_name, confidence, match_type, reason, create_time) VALUES
('R1', 'M1', '低空经济', '001696', '宗申动力', 95, 'EXPLICIT', '无人机动力系统受益', CURRENT_TIMESTAMP),
('R2', 'M1', '低空经济', '002085', '万丰奥威', 92, 'WATCHLIST', 'eVTOL 和低空飞行器链条受益', CURRENT_TIMESTAMP),
('R3', 'M2', '低空经济', '002085', '万丰奥威', 90, 'EXPLICIT', '地方试点落地提升景气度', CURRENT_TIMESTAMP),
('R4', 'M3', '算力', '300308', '中际旭创', 94, 'EXPLICIT', '高速光模块和算力互联受益', CURRENT_TIMESTAMP),
('R5', 'M4', '创新药', '688235', '百济神州', 85, 'WATCHLIST', '创新药政策敏感度较高', CURRENT_TIMESTAMP);

-- 主题自动候选池
INSERT INTO a_theme_auto_pool (id, theme_name, stock_code, stock_name, candidate_score, hit_count, enabled, reason, latest_pub_date, create_time, update_time) VALUES
('P1', '低空经济', '001696', '宗申动力', 96, 4, 1, '政策扶持与订单公告形成共振', DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('P2', '低空经济', '002085', '万丰奥威', 93, 3, 1, '政策扶持与低空经济大单共振', DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('P3', '算力', '300308', '中际旭创', 89, 3, 1, '算力主线与订单增长共振', DATEADD('DAY', -2, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('P4', '创新药', '688235', '百济神州', 78, 2, 1, '政策扰动形成风险共振', DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
