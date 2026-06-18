# Stock Monitor 技术架构图

本文面向项目维护者、协作者和对外演示场景，给出 `stock_monitor` 的技术架构全景。图中重点覆盖：

- 多模块代码结构
- A 股事件驱动主链路
- 企业微信推送与 MCP 查询双出口
- 群晖 NAS 上的部署拓扑

## 1. 架构概览

`stock_monitor` 是一个以 A 股为主的事件驱动投研系统。系统从公告、宏观快讯等外部源抓取数据，经过规则去噪、事件分类、信号评分、主题映射和共振融合后，分别输出：

- 盘前早报、盘后复盘、盘后风险速递
- 盘中实时预警、宏观实时推送、市场脉冲推送
- 面向企业微信机器人的 A 股 MCP 研究工具

## 2. 模块视图

```mermaid
flowchart LR
    subgraph Repo["Monorepo: stock_monitor"]
        Common["stock-common\n公共配置 / 工具 / 通用实体"]
        Web["stock-web\n主业务服务\n抓取 / 评分 / 报告 / 推送"]
        AMcp["a-stock-mcp\nA 股 MCP Server\n研究问答工具接口"]
        USMcp["us-stock-mcp\n美股 MCP 模块\n当前非主运行焦点"]
    end

    Common --> Web
    Common --> AMcp
    Common --> USMcp

    Web -->|"MySQL 读写"| DB["MySQL"]
    AMcp -->|"MySQL 查询"| DB

    Web -->|"Webhook"| WeCom["企业微信 / 钉钉"]
    AMcp -->|"SSE / MCP Tools"| Agent["企业微信机器人 / Agent / Copilot"]
```

## 3. 系统上下文图

```mermaid
flowchart TB
    Gov["政府 / 政策 / 宏观快讯源"]
    Notice["A 股公告 / RSS / 新闻源"]
    User["运营 / 研究员 / 群成员"]
    Manual["手动测试台\n/manual-push-console.html"]
    WeCom["企业微信测试群 / 正式群"]
    MCPClient["企业微信机器人 / 外部 Agent / MCP Client"]
    Web["stock-web"]
    AMcp["a-stock-mcp"]
    DB["MySQL"]
    LLM["LLM / OpenAI-Compatible API"]

    Gov --> Web
    Notice --> Web
    User --> Manual
    Manual --> Web
    Web --> DB
    Web --> LLM
    Web --> WeCom

    MCPClient --> AMcp
    AMcp --> DB
    AMcp --> LLM

    Web -.共享事件与主题结果.-> AMcp
```

## 4. 核心处理链路

下面是项目最核心的 A 股事件驱动处理主链路。

```mermaid
flowchart LR
    A1["公告抓取\nRssService / StockScheduler"]
    A2["宏观快讯抓取\nMacroNewsService / StockScheduler"]
    B1["规则去噪\n事件分类 / 多空方向 / 信号评分"]
    B2["事件聚类\nclusterKey / 股票聚合分"]
    B3["宏观主题识别\n主题事件 / 影子主题池 / 标的映射"]
    B4["公告-主题共振融合\nAReportFusionService"]
    C1["市场状态机\nMarketStateServiceImpl"]
    C2["推送协调层\nMarketAlertCoordinatorService"]
    D1["AI 报告生成\nAISummaryServiceImpl"]
    D2["盘中实时卡片\nAStockRealtimePushService"]
    D3["宏观实时推送\nMacroRealtimePushService"]
    D4["市场脉冲推送\nMarketPulsePushService"]
    E1["企业微信 Markdown 推送\nWeComApi"]
    E2["A 股 MCP 研究查询\nAStockResearchServiceImpl"]

    A1 --> B1
    A2 --> B3
    B1 --> B2
    B2 --> B4
    B3 --> B4
    B1 --> C1
    B3 --> C1
    C1 --> C2
    B2 --> C2
    B3 --> C2

    B4 --> D1
    B2 --> D2
    B3 --> D3
    C1 --> D4

    D1 --> E1
    D2 --> E1
    D3 --> E1
    D4 --> E1

    B2 --> E2
    B3 --> E2
    B4 --> E2
```

## 5. 运行时组件图

### 5.1 `stock-web` 内部职责

```mermaid
flowchart TB
    subgraph StockWeb["stock-web"]
        Scheduler["定时调度层\nStockScheduler\nMorningReportScheduler\nMacroRealtimePushScheduler\nMarketPulseScheduler"]
        Controller["接口层\nReportPushController\nOpsDashboardController"]
        Fetch["抓取层\nRssService\nMacroNewsService"]
        Domain["理解层\n分类 / 评分 / 聚类 / 排名 / 主题映射"]
        Fusion["融合层\nAReportFusionService"]
        State["状态层\nMarketStateServiceImpl\nMarketAlertCoordinatorService"]
        Push["输出层\nWeComApi\nAStockRealtimePushService\nMacroRealtimePushService\nMarketPulsePushService"]
        Demo["演示层\nAStockIntradayDemoPushService\nmanual-push-console.html"]
    end

    Scheduler --> Fetch
    Scheduler --> Fusion
    Scheduler --> State
    Controller --> Fusion
    Controller --> Push
    Controller --> Demo
    Fetch --> Domain
    Domain --> Fusion
    Domain --> State
    Fusion --> Push
    State --> Push
    Demo --> Push
```

### 5.2 `a-stock-mcp` 内部职责

```mermaid
flowchart TB
    subgraph AMCP["a-stock-mcp"]
        Prompt["Prompt Template\nAStockRobotPromptTemplate"]
        ToolReg["Tool Registration\nApplication"]
        ToolZh["中文工具集\nAStockTool / MacroThemeTool"]
        ToolEn["英文工具集\nAStockToolEnglish / MacroThemeToolEnglish"]
        Research["研究服务\nAStockResearchServiceImpl"]
        SSE["Spring AI MCP Server SSE\n/sse + /mcp/message"]
    end

    Prompt --> ToolReg
    ToolReg --> ToolZh
    ToolReg --> ToolEn
    ToolZh --> Research
    ToolEn --> Research
    Research --> SSE
```

## 6. 推送与查询双出口

项目的一个关键设计点，是“同一套结构化研究结果，同时服务推送和问答”。

```mermaid
flowchart LR
    Structured["结构化事件结果\n公告聚类 / 宏观主题 / 共振结果 / 市场状态"]
    Report["早报 / 晚报 / 风险速递"]
    Alert["盘中实时预警\n宏观实时 / 市场脉冲 / Demo Push"]
    MCP["MCP 研究工具\n个股摘要 / 机会榜 / 风险榜 / 主线榜"]
    WeCom["企业微信消息"]
    Agent["企业微信机器人 / 外部 Agent"]

    Structured --> Report
    Structured --> Alert
    Structured --> MCP

    Report --> WeCom
    Alert --> WeCom
    MCP --> Agent
```

## 7. 部署拓扑图

当前线上部署在群晖 NAS，`stock-web` 与 `a-stock-mcp` 分别以独立容器运行，均使用 host 网络。

```mermaid
flowchart TB
    subgraph NAS["群晖 NAS / Docker Host"]
        subgraph Compose["docker compose / host network"]
            WebC["stock-monitor-web\nport 8888\nSpring Boot"]
            MCPC["stock-monitor-a-stock-mcp\nport 8091\nSpring AI MCP SSE"]
        end

        DB["MySQL\nport 3306"]
    end

    Browser["浏览器\nmanual-push-console / ops-dashboard"] --> WebC
    WeCom["企业微信机器人 Webhook"] <-->|HTTP Markdown| WebC
    MCPClient["MCP Client / 企业微信机器人"] --> MCPC

    WebC --> DB
    MCPC --> DB

    WebC --> LLM["LLM API"]
    MCPC --> LLM
```

## 8. 关键设计说明

### 8.1 为什么拆成 `stock-web` + `a-stock-mcp`

- `stock-web` 负责事件驱动主业务和消息分发，偏“生产工作流”
- `a-stock-mcp` 负责把研究能力标准化成工具接口，偏“对外能力输出”
- 两者共享 MySQL 中间结果，避免重复抓取和重复理解

### 8.2 为什么是“规则先行，LLM 后置”

- 原始公告和快讯噪声很大，直接交给模型容易产生幻觉和不稳定判断
- 先经过规则分类、事件评分、聚类、主题映射之后，LLM 只负责高质量表达与解释
- 这样既降低成本，也显著提升报告和问答的一致性

### 8.3 为什么需要市场状态机与协调层

- 解决短时间内多空信号来回切换
- 解决宏观实时推送、市场脉冲推送之间互相打架
- 通过冷却时间、家族互斥和确认窗口，减少告警风暴

### 8.4 为什么保留手动测试台

- 可绕过定时任务等待，直接验证盘前、盘后、盘中推送链路
- 支持中英文切换，适合产品演示和对外展示
- 新增的 intraday demo push 可以用 mock 数据走真实 WeCom 发送路径

## 9. 建议展示顺序

如果要在汇报或演示中使用，推荐按这个顺序讲：

1. 先讲“模块视图”，说明仓库为什么拆成多模块
2. 再讲“核心处理链路”，说明系统如何把公告变成研究结果
3. 然后讲“推送与查询双出口”，体现产品化能力
4. 最后讲“部署拓扑图”，说明如何在线上运行和扩展
