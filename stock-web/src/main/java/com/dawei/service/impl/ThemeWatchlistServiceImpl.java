package com.dawei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dawei.entity.ThemeWatchlist;
import com.dawei.mapper.ThemeWatchlistMapper;
import com.dawei.service.ThemeWatchlistService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 主题观察池管理服务实现
 */
@Service
public class ThemeWatchlistServiceImpl implements ThemeWatchlistService {

    private static final List<DefaultWatchlistSeed> DEFAULT_SEEDS = List.of(
            seed("低空经济", "000099", "中信海直", 3, "低空运营核心观察池"),
            seed("低空经济", "002085", "万丰奥威", 2, "飞行汽车与低空装备"),
            seed("低空经济", "001696", "宗申动力", 2, "低空动力与无人机配套"),
            seed("算力", "000977", "浪潮信息", 3, "服务器与算力基础设施"),
            seed("算力", "603019", "中科曙光", 3, "国产算力平台"),
            seed("算力", "300308", "中际旭创", 2, "高速光模块与算力链"),
            seed("人工智能", "002230", "科大讯飞", 3, "大模型与 AI 应用"),
            seed("人工智能", "002261", "拓维信息", 2, "AI 行业应用"),
            seed("人工智能", "300496", "中科创达", 2, "智能终端与 AI OS"),
            seed("机器人", "300024", "机器人", 3, "机器人本体龙头"),
            seed("机器人", "002747", "埃斯顿", 3, "工业机器人与伺服"),
            seed("机器人", "688017", "绿的谐波", 2, "谐波减速器"),
            seed("半导体", "603986", "兆易创新", 3, "芯片设计与存储"),
            seed("半导体", "688008", "澜起科技", 2, "服务器芯片"),
            seed("半导体", "603501", "韦尔股份", 2, "图像传感器"),
            seed("国企改革", "600150", "中国船舶", 3, "央企资产整合核心观察池"),
            seed("国企改革", "601989", "中国重工", 2, "央企重组与资产注入"),
            seed("国企改革", "601766", "中国中车", 2, "国资国企改革样本"),
            seed("创新药", "688235", "百济神州", 3, "创新药核心观察池"),
            seed("创新药", "688271", "联影医疗", 2, "创新医疗装备"),
            seed("创新药", "300347", "泰格医药", 2, "创新药研发服务"),
            seed("金融", "600030", "中信证券", 3, "券商龙头与资本市场主线"),
            seed("金融", "300059", "东方财富", 3, "互联网券商与流量平台"),
            seed("金融", "601318", "中国平安", 2, "金融综合平台"),
            seed("稀土", "600111", "北方稀土", 3, "稀土主线龙头"),
            seed("稀土", "000831", "中国稀土", 3, "稀土资源平台"),
            seed("光伏", "601012", "隆基绿能", 3, "光伏主产业链"),
            seed("光伏", "600438", "通威股份", 2, "硅料与电池片"),
            seed("光伏", "300274", "阳光电源", 2, "逆变器与储能"),
            seed("锂电", "300750", "宁德时代", 3, "动力电池龙头"),
            seed("锂电", "002466", "天齐锂业", 2, "锂资源"),
            seed("锂电", "002460", "赣锋锂业", 2, "锂资源与材料"),
            seed("军工", "600760", "中航沈飞", 3, "军工整机核心"),
            seed("军工", "000768", "中航西飞", 2, "军工航空主机"),
            seed("军工", "002179", "中航光电", 2, "军工连接器"),
            seed("稳增长", "601800", "中国交建", 3, "稳增长与基建主线"),
            seed("稳增长", "601390", "中国中铁", 3, "基建投资核心观察池"),
            seed("稳增长", "600031", "三一重工", 2, "工程机械与设备更新"),
            seed("稳增长", "600585", "海螺水泥", 2, "基建链条与建材"),
            seed("黄金", "600489", "中金黄金", 3, "黄金主线龙头"),
            seed("黄金", "600547", "山东黄金", 3, "黄金资源核心"),
            seed("黄金", "601899", "紫金矿业", 2, "贵金属与铜金资源"),
            seed("原油", "600938", "中国海油", 3, "原油价格主线"),
            seed("原油", "600028", "中国石化", 2, "炼化与油气"),
            seed("天然气", "600256", "广汇能源", 3, "LNG 与天然气主线"),
            seed("天然气", "600803", "新奥股份", 2, "城燃与 LNG")
    );

    private final ThemeWatchlistMapper themeWatchlistMapper;

    public ThemeWatchlistServiceImpl(ThemeWatchlistMapper themeWatchlistMapper) {
        this.themeWatchlistMapper = themeWatchlistMapper;
    }

    @Override
    public List<ThemeWatchlist> list(String themeName, Integer enabled) {
        QueryWrapper<ThemeWatchlist> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(themeName)) {
            queryWrapper.eq("theme_name", normalize(themeName));
        }
        if (enabled != null) {
            queryWrapper.eq("enabled", enabled > 0 ? 1 : 0);
        }
        queryWrapper.orderByAsc("theme_name").orderByDesc("priority").orderByAsc("stock_code");
        return themeWatchlistMapper.selectList(queryWrapper);
    }

    @Override
    public ThemeWatchlist upsert(ThemeWatchlist watchlist) {
        String themeName = normalize(watchlist.getThemeName());
        String stockCode = normalize(watchlist.getStockCode());
        if (StringUtils.isBlank(themeName) || StringUtils.isBlank(stockCode)) {
            throw new IllegalArgumentException("themeName 和 stockCode 不能为空");
        }

        ThemeWatchlist existing = findByThemeAndStock(themeName, stockCode);
        if (existing == null) {
            ThemeWatchlist candidate = new ThemeWatchlist();
            candidate.setId(UUID.randomUUID().toString().replace("-", ""));
            candidate.setThemeName(themeName);
            candidate.setStockCode(stockCode);
            candidate.setStockName(normalize(watchlist.getStockName()));
            candidate.setPriority(defaultPriority(watchlist.getPriority()));
            candidate.setEnabled(defaultEnabled(watchlist.getEnabled()));
            candidate.setReason(normalize(watchlist.getReason()));
            candidate.setCreateTime(LocalDateTime.now());
            themeWatchlistMapper.insert(candidate);
            return candidate;
        }

        existing.setStockName(preferInput(watchlist.getStockName(), existing.getStockName()));
        existing.setPriority(defaultPriority(watchlist.getPriority()));
        existing.setEnabled(defaultEnabled(watchlist.getEnabled()));
        existing.setReason(preferInput(watchlist.getReason(), existing.getReason()));
        themeWatchlistMapper.updateById(existing);
        return existing;
    }

    @Override
    public SeedSummary seedDefaults(boolean overwriteExisting) {
        SeedSummary summary = new SeedSummary(DEFAULT_SEEDS.size());
        for (DefaultWatchlistSeed seed : DEFAULT_SEEDS) {
            ThemeWatchlist existing = findByThemeAndStock(seed.themeName(), seed.stockCode());
            if (existing == null) {
                ThemeWatchlist candidate = new ThemeWatchlist();
                candidate.setId(UUID.randomUUID().toString().replace("-", ""));
                candidate.setThemeName(seed.themeName());
                candidate.setStockCode(seed.stockCode());
                candidate.setStockName(seed.stockName());
                candidate.setPriority(seed.priority());
                candidate.setEnabled(1);
                candidate.setReason(seed.reason());
                candidate.setCreateTime(LocalDateTime.now());
                themeWatchlistMapper.insert(candidate);
                summary.incrementInserted();
                continue;
            }

            if (!overwriteExisting) {
                summary.incrementSkipped();
                continue;
            }

            existing.setStockName(seed.stockName());
            existing.setPriority(seed.priority());
            existing.setEnabled(1);
            existing.setReason(seed.reason());
            themeWatchlistMapper.updateById(existing);
            summary.incrementUpdated();
        }
        return summary;
    }

    @Override
    public boolean updateEnabled(String id, boolean enabled) {
        ThemeWatchlist existing = themeWatchlistMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        existing.setEnabled(enabled ? 1 : 0);
        return themeWatchlistMapper.updateById(existing) > 0;
    }

    @Override
    public boolean deleteById(String id) {
        return themeWatchlistMapper.deleteById(id) > 0;
    }

    private ThemeWatchlist findByThemeAndStock(String themeName, String stockCode) {
        return themeWatchlistMapper.selectOne(new QueryWrapper<ThemeWatchlist>()
                .eq("theme_name", themeName)
                .eq("stock_code", stockCode)
                .last("LIMIT 1"));
    }

    private String normalize(String value) {
        return StringUtils.trimToNull(value);
    }

    private int defaultPriority(Integer priority) {
        return priority == null ? 0 : priority;
    }

    private int defaultEnabled(Integer enabled) {
        return enabled == null || enabled > 0 ? 1 : 0;
    }

    private String preferInput(String input, String fallback) {
        return StringUtils.trimToNull(input) != null ? StringUtils.trimToNull(input) : fallback;
    }

    private static DefaultWatchlistSeed seed(String themeName,
                                             String stockCode,
                                             String stockName,
                                             int priority,
                                             String reason) {
        return new DefaultWatchlistSeed(themeName, stockCode, stockName, priority, reason);
    }

    private record DefaultWatchlistSeed(String themeName,
                                        String stockCode,
                                        String stockName,
                                        int priority,
                                        String reason) {
    }
}
