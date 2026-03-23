# A-Stock MCP

这个模块现在定位为 A 股研究型 MCP Server，不再是原始公告查表工具。

## 当前工具集

- 个股解析：`resolveAStock`
- 个股摘要：`getAStockSignalSummary`
- 个股事件卡：`getAStockRecentEventCards`
- 机会榜：`getAStockOpportunityBoard`
- 风险榜：`getAStockRiskBoard`
- 原始公告兜底：`queryRawAStockNotices`
- 宏观主线：`getMacroThemeBoard`
- 正向共振：`getThemeResonanceBoard`

## 接入建议

如果你在企业微信机器人、大模型工作流平台或自建 Agent 里接这个 MCP：

1. 把系统提示词固定成 [wecom-a-stock-robot-system-prompt.md](src/main/resources/prompts/wecom-a-stock-robot-system-prompt.md)
2. 让模型优先走摘要、事件卡、主线和共振工具
3. 只在需要原始公告时再调用 `queryRawAStockNotices`
4. 不要再把 `WebFetchTool` 暴露给 A 股问答链路
5. 对“什么是换手率”“主线退潮怎么看”这类通用概念题，允许模型直接回答，不要强行走工具
6. 对估值、盘口、实时价格、K线形态这类当前工具不覆盖的问题，要明确能力边界，不要编造
7. 在系统提示词最前面写清楚边界控制：禁止跨界闲聊、禁止无数据股评、禁止确定性交易指令、失败时必须附替代问法

补充两条关键口径：

- `getAStockSignalSummary` 里的 `aggregateSignalScore` 是晚报同款股票聚合分，`topRawSignalScore` 才是单条公告原始分
- 如果用户明确在问“今天”“晚报”“盘后”“复盘”“为什么上榜”，应把 `getAStockSignalSummary` 或 `getAStockRecentEventCards` 的 `days` 设为 `1`

## 提示词模板

代码里提供了可复用模板类：

- [AStockRobotPromptTemplate.java](src/main/java/com/dawei/prompt/AStockRobotPromptTemplate.java)

外部 client 没在这个仓库里，所以这里不强行捏一个假的接入层；建议你的企业微信问答服务直接加载这份模板，作为系统提示词。

## 使用文档

- 企业微信系统提示词（直接复制纯文本）：[wecom-robot-system-prompt.txt](docs/wecom-robot-system-prompt.txt)
- 企业微信系统提示词说明页：[wecom-robot-system-prompt.md](docs/wecom-robot-system-prompt.md)
- 系统提示词模板：[wecom-a-stock-robot-system-prompt.md](src/main/resources/prompts/wecom-a-stock-robot-system-prompt.md)
- 群公告文档：[wecom-group-announcement.md](docs/wecom-group-announcement.md)
- 外层边界控制实施方案：[wecom-boundary-control-plan.md](docs/wecom-boundary-control-plan.md)

## MCP 端点

- SSE path: `/sse`
- Message path: `/mcp/message`
- 默认本地 SSE URL：`http://127.0.0.1:8091/sse`
- 默认本地 Message URL：`http://127.0.0.1:8091/mcp/message`

说明：

- `sse-endpoint` 来自 [application.yml](src/main/resources/application.yml)
- `sse-message-endpoint` 来自 [application.yml](src/main/resources/application.yml)
- `application-dev.yml` 和 `application-prod.yml` 都显式配置了 `server.port: 8091`
- 如果你部署时改了端口、做了反向代理，最终地址应替换成：
  - `http://<你的主机或域名>:<你的端口>/sse`
  - `http://<你的主机或域名>:<你的端口>/mcp/message`

## 开发验证

```bash
mvn -pl a-stock-mcp -am test
```
