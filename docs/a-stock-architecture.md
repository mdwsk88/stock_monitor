# A 股技术架构图

下面这张图聚焦当前项目的 A 股主链路，覆盖数据源、抓取调度、规则理解、主题共振、LLM 生成、MCP 工具化和消息输出。

```mermaid
flowchart TB
    subgraph DS["数据源"]
        A1["东方财富 A 股公告"]
        A2["宏观政策 / 产业 / 快讯源"]
        A3["企业微信 / Agent 问答请求"]
    end

    subgraph IN["采集与调度层 | stock-web"]
        B1["StartupRunner<br/>启动补抓"]
        B2["StockScheduler<br/>定时调度"]
        B3["RssService<br/>A 股公告抓取入库"]
        B4["MacroNewsService<br/>宏观快讯抓取入库"]
    end

    subgraph RULE["事件理解层 | 规则引擎"]
        C1["AStockSignalService<br/>公告去噪 / 事件分类 / 方向判断 / 信号评分 / clusterKey"]
        C2["MacroNewsSignalService<br/>主题识别 / 事件类型 / 方向判断 / 主题评分"]
        C3["StockRankService<br/>事件簇聚合 / 个股聚合分 / 机会榜风险榜排序"]
    end

    subgraph THEME["主题关系与共振层"]
        D1["MacroThemeRelationService<br/>主题-标的映射构建"]
        D2["ThemeAutoPoolService<br/>自动候选池累积命中"]
        D3["AReportFusionService<br/>宏观主线 + 公告事件 + 共振标的融合"]
        D4["AStockRealtimeContextService<br/>盘中实时共振上下文"]
    end

    subgraph DB["MySQL 数据层"]
        E1[("a_stock_rss")]
        E2[("macro_news_raw")]
        E3[("a_macro_theme_event")]
        E4[("a_macro_theme_stock_rel")]
        E5[("theme_auto_pool_candidate")]
        E6[("a_stock_push_log")]
    end

    subgraph AI["AI 生成层"]
        F1["AISummaryService<br/>结构化上下文组装"]
        F2["Spring AI ChatClient"]
        F3["OpenAI Compatible Model<br/>报告生成 / 摘要生成"]
    end

    subgraph OUT["输出层"]
        G1["MorningReportScheduler<br/>A 股盘前早报 / 盘后复盘"]
        G2["AStockRealtimePushService<br/>盘中实时预警"]
        G3["WeComApi / DingTalkApi<br/>群消息推送"]
        G4["ReportPushController<br/>手动触发接口"]
    end

    subgraph MCP["研究能力产品化 | a-stock-mcp"]
        H1["AStockTool"]
        H2["MacroThemeTool"]
        H3["AStockResearchService<br/>个股解析 / 事件卡 / 机会榜 / 风险榜"]
        H4["MacroThemeResearchService<br/>主线榜 / 共振榜"]
    end

    subgraph USER["消费端"]
        I1["企业微信机器人"]
        I2["内部 Agent / Copilot"]
        I3["投研问答场景"]
    end

    A1 --> B3
    A2 --> B4
    A3 --> H1
    A3 --> H2

    B1 --> B3
    B1 --> B4
    B2 --> B3
    B2 --> B4

    B3 --> C1
    B4 --> C2

    C1 --> E1
    C2 --> E2
    C2 --> E3

    E1 --> C3
    E3 --> D1
    E1 --> D1
    D1 --> E4
    D1 --> D2
    D2 --> E5

    C3 --> D3
    E3 --> D3
    E4 --> D3
    E5 --> D3

    C1 --> D4
    E3 --> D4
    E4 --> D4
    E5 --> D4

    D3 --> F1
    C3 --> F1
    F1 --> F2
    F2 --> F3

    F3 --> G1
    D4 --> G2
    C1 --> G2
    G1 --> G3
    G2 --> G3
    G4 --> G1

    E1 --> H3
    E3 --> H3
    E4 --> H3
    E5 --> H3
    E3 --> H4
    E4 --> H4
    E5 --> H4
    H1 --> H3
    H2 --> H4

    G3 --> I1
    H3 --> I2
    H4 --> I2
    H3 --> I3
    H4 --> I3
    G2 --> E6
```

## 核心说明

- `stock-web` 是主业务服务，负责抓取、规则理解、榜单计算、盘前盘后报告和盘中推送。
- `AStockSignalService` 是 A 股事件引擎核心，先去噪，再做事件类型、方向和评分判断，并生成事件聚类键。
- `MacroNewsSignalService` 负责把宏观快讯转成“可交易主题”，为后续主线分析和共振识别提供输入。
- `AReportFusionService` 把个股公告、宏观主题、主题映射和共振标的融合成一份高价值上下文，再交给 LLM 生成盘前早报或盘后复盘。
- `AISummaryService` 不是直接把原始公告扔给模型，而是先喂给模型结构化后的上下文，降低噪音和幻觉。
- `a-stock-mcp` 把研究能力封装成 MCP 工具，支持个股解析、事件卡、机会榜、风险榜、主线榜和共振榜，便于 Agent 调用。

## 一句话版本

这个项目的技术架构本质上是一条“金融事件理解流水线”：

`数据抓取 -> 规则去噪与评分 -> 事件聚类 -> 主题映射 -> 共振融合 -> LLM 生成 -> 群推送 / MCP 问答输出`
