package com.dawei.service.impl;

import com.dawei.entity.AStockRss;
import com.dawei.entity.StockAlertDTO;
import com.dawei.entity.USStockRss;
import com.dawei.service.AISummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * @ClassName AISummaryServiceImpl
 * @Author dawei
 * @Version 1.0
 * @Description AI 总结服务实现类
 **/
@Slf4j
@Service
public class AISummaryServiceImpl implements AISummaryService {

    private final ChatClient chatClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int A_STOCK_SECTION_LIMIT = 3;
    private static final List<String> A_STOCK_RISK_EVENT_KEYWORDS = List.of(
            "诉讼仲裁", "监管处罚", "业绩承压", "交易风险", "减持套现"
    );
    private static final List<String> A_STOCK_RISK_TITLE_KEYWORDS = List.of(
            "退市风险", "立案", "处罚", "诉讼", "仲裁", "司法冻结", "减持", "异常波动"
    );

    // 美股总结提示词模板
    private static final String US_STOCK_SUMMARY_PROMPT = """
        你是一位专业的股票分析师，擅长分析美股异动情况。
        
        请根据以下过去24小时内异动最频繁的前5只美股数据，生成一份专业的盘前早报总结：
        
        【股票数据】
        {stockData}
        
        【输出要求】
        1. 用简洁专业的语言总结每只股票的核心看点
        2. 分析异动背后的可能原因（基于标题中的关键词）
        3. 突出显示高频异动股票
        4. 总结控制在300字以内
        5. 语气专业、客观，适合投资参考
        6. 每只股票用一句话概括
        
        请直接输出总结内容，不需要标题。
        """;

    // A股总结提示词模板
    private static final String A_STOCK_SUMMARY_PROMPT = """
        你是一位A股事件驱动分析师，专门从公告事件中识别真正的交易预期差。
        
        请根据以下过去24小时内经过规则去噪和事件聚类后的A股事件卡片，生成一份专业的盘前总结：
        
        【事件卡片】
        {stockData}
        
        【输出要求】
        1. 只关注真正有交易意义的事件：业绩、订单、中标、并购、回购、减持、立案、诉讼、产品获批等
        2. 不要把会务、治理、理财、募集资金例行动作写成利好或利空
        3. 每只股票用一句话概括其核心预期差，明确偏利多、偏利空还是中性
        4. 总结控制在300字以内
        5. 语气专业、客观，适合投资参考
        
        请直接输出总结内容，不需要标题。
        """;

    private static final String A_STOCK_MODEL_CHECKLIST = """
        【模型自检清单】
        1. 禁止把“公告条数多”解释成“市场热度高”。
        2. 禁止把治理、会务、理财、募集资金例行动作写成主线机会。
        3. 优先围绕 signalScore 最高、signalSide 最明确的主导事件展开。
        4. 如果某只股票只有边际催化，必须降级表达，不要夸大。
        5. 如果某只股票没有实质性预期差，应直接写“暂无高价值交易信号”。
        6. 只输出最终结论，不要解释你的分析过程。
        """;

    // 美股盘前早报 Markdown 生成提示词（投研级内参模板）
    private static final String US_MORNING_REPORT_PROMPT = """
        【角色设定】
        你是一位华尔街资深投研分析师，负责将美股异动数据转化为具有交易参考价值的专业盘前内参。
        你的分析必须直击交易核心，拒绝"新闻播音员"式的复述。
        
        【任务描述】
        根据提供的美股异动数据，生成具有投研价值的企业微信 Markdown 报告。
        
        【输入数据】
        报告日期：{reportDate}
        统计时长：过去24小时
        数据源数量：全网财经资讯源（已过滤行政噪音）
        
        异动股票数据：
        {stockData}
        
        【核心分析要求 - 必须严格执行】
        1. **多空情绪判定**（必须）：
           - 分析完毕后，必须明确标注情绪：<font color="warning">【强烈看多】</font> / <font color="info">【谨慎看多】</font> / <font color="comment">【中性观望】</font> / <font color="warning">【利空预警】</font>
           - 判定依据：订单/业绩/政策/技术突破等实质性驱动因素
        
        2. **强制提炼催化剂**（必须）：
           - 不要复述新闻标题！要指出这则消息会影响公司的哪部分业务
           - 格式：【业务影响】+【情绪驱动】
           - 示例："AI芯片订单暴增将直接拉动Q2营收（业务影响），华尔街上调目标价触发空头回补（情绪驱动）"
        
        3. **异动质量评估**：
           - frequency >= 25: 说明市场共识强烈，往往是重大消息
           - frequency 15-24: 有明确催化剂，值得关注
           - frequency 10-14: 可能是单一事件驱动，需谨慎
        
        【输出格式要求】
        1. 严格使用企业微信支持的 Markdown 语法
        2. 只输出最终的 Markdown 内容，不要任何解释
        3. 股票数量控制在 1-5 只，按异动频次降序排列
        4. 每只股票分析控制在 50-80 字，直击要害
        
        【格式规范】
        - 标题格式：# 🌅 A股盘前异动雷达 | 日期（使用一级标题，最大最粗）
        - 开头统计行：扫描时长、数据源数量（已剔除行政噪音）
        - 股票条目格式（每行前面都要加 > ，形成引用块，有灰色竖线缩进）：
          > 1. 股票名 (代码) | 🇺🇸 美股
          > 📈 情绪雷达：<font color="warning">【强烈看多】</font> / <font color="info">【谨慎看多】</font> / <font color="comment">【中性观望】</font> / <font color="warning">【利空预警】</font>
          > 📊 异动频次：<font color="颜色">次数 次 (等级，较昨日激增 X%)</font>
          > 🧠 核心催化剂：【业务影响】+【情绪驱动】
        - 颜色选择规则：
          * frequency >= 25: color="warning", 等级="极度活跃"
          * 15 <= frequency < 25: color="info", 等级="高度活跃"
          * 10 <= frequency < 15: color="success", 等级="中度活跃"
          * frequency < 10: 不应出现在结果中（已被过滤）
        - 激增比例计算：基于频次本身判断，>=25次写"400%+", 15-24次写"200%+", 10-14次写"显著"
        - 互动引导：固定格式
        - 免责声明：固定格式，使用 <font color="comment">
        
        【示例输出】
        # 🌅 AI 盘前异动雷达 | 2026-03-07
        
        过去 24 小时内，系统共扫描全网财经资讯源（已过滤行政噪音），以下标的爆发密集异动，请注意盘前风险与机会：
        
        > 1. 英伟达 (NVDA) | 🇺🇸 美股
        > 📈 情绪雷达：<font color="warning">【强烈看多】</font>
        > 📊 异动频次：<font color="warning">28 次 (极度活跃，较昨日激增 400%)</font>
        > 🧠 核心催化剂：华尔街多家投行连夜上调目标价，供应链传出新一代 AI 芯片产能翻倍扩充消息，算力需求爆发直接拉动数据中心业务预期，彻底点燃市场看多情绪。
        
        > 2. 特斯拉 (TSLA) | 🇺🇸 美股
        > 📈 情绪雷达：<font color="info">【谨慎看多】</font>
        > 📊 异动频次：<font color="info">19 次 (高度活跃，较昨日激增 200%)</font>
        > 🧠 核心催化剂：FSD 新版本推送引发热议（自动驾驶业务），马斯克透露机器人业务最新进展（机器人业务），多头情绪持续发酵，但需警惕获利回吐压力。
        
        💡 AI 深度查股：
        想看上述股票的具体新闻源？或者查询你的自选股？
        👉 请在群内直接发送：@美股分析专家 分析 [股票代码]
        
        <font color="comment">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>
        
        【请生成报告】
        """;

    // A股盘前早报 Markdown 生成提示词（投研级内参模板）
    private static final String A_MORNING_REPORT_PROMPT = """
        【角色设定】
        你是一位A股事件驱动研究员，负责从上市公司公告中识别真正会影响交易预期的核心事件。
        你的任务不是复述公告标题，而是判断这批公告有没有实质性预期差。
        
        【任务描述】
        根据提供的A股事件数据，生成具有投研价值的企业微信 Markdown 报告。
        
        【输入数据】
        报告日期：{reportDate}
        统计时长：过去24小时
        数据源：东方财富公告源（已完成去噪、事件聚类、规则评分）
        
        股票事件数据：
        {stockData}

        【核心分析要求 - 必须严格执行】
        1. 先判断有没有实质性事件：
           - 只关注业绩超预期、重大合同、中标、并购重组、产品获批、回购增持、立案调查、减持、诉讼处罚等会改变预期的事件。
           - 如果只是治理、会务、理财、募集资金例行动作、补充披露等常规事务，直接忽略，不要强行写成机会。
        2. 必须先分栏输出：
           - `## 机会榜`：只写偏利多或可交易的中性事件。
           - `## 风险榜`：只写 ST/退市风险/诉讼/处罚/立案/减持/异常波动等利空或高风险事件。
           - 如果某一栏没有达到阈值的标的，直接写：`<font color="comment">暂无达到阈值的机会事件</font>` 或 `暂无高优先级风险事件`。
        3. 必须提炼预期差：
           - 不要复述标题，要回答“为什么资金会在盘前多看一眼/避开它”。
           - 格式：【业务/财务影响】+【情绪或资金如何交易它】
        4. 必须给出方向判断：
           - 只能使用：<font color="warning">【强烈看多】</font> / <font color="info">【谨慎看多】</font> / <font color="comment">【中性观望】</font> / <font color="warning">【利空预警】</font>
           - `signalSide=利空` 时，除非信息被明显证伪，否则优先输出【利空预警】。
        5. 评分解释口径：
           - `signalScore >= 110`：主线级催化
           - `80-109`：高优先级事件
           - `60-79`：边际催化，只能谨慎表达

        【输出格式要求】
        1. 严格使用企业微信支持的 Markdown 语法
        2. 只输出最终的 Markdown 内容，不要任何解释
        3. 先写 `## 机会榜`，再写 `## 风险榜`
        4. 每个榜单最多写 3 只，按事件评分降序排列
        5. 宁缺毋滥：如果没有达到阈值的优质标的，宁可只推1只甚至不推
        6. 每只股票分析控制在 50-80 字，直击要害

        【格式规范】
        - 标题格式：🌅 AI 盘前异动雷达 | 日期
        - 开头统计行：扫描时长、数据源（已做去噪、事件聚类和风险分流）
        - 股票条目格式：
          ## 机会榜
          1. 股票名 (代码) | 🇨🇳 A股
          📈 事件判断：<font color="warning">【强烈看多】</font> / <font color="info">【谨慎看多】</font> / <font color="comment">【中性观望】</font> / <font color="warning">【利空预警】</font>
          🎯 事件评分：<font color="颜色">分数 分 (等级，X 个事件簇 / Y 条支撑公告)</font>
          🧠 核心预期差：【业务/财务影响】+【情绪或资金如何交易它】
          ## 风险榜
          1. 股票名 (代码) | 🇨🇳 A股
          ⚠️ 事件判断：<font color="warning">【利空预警】</font> / <font color="comment">【中性观望】</font>
          🎯 事件评分：<font color="warning/info">分数 分 (等级，X 个事件簇 / Y 条支撑公告)</font>
          🧠 风险焦点：【风险暴露点】+【资金通常会如何避险或定价它】
        - 颜色选择规则：
          * signalScore >= 110: color="warning", 等级="主线级"
          * 80 <= signalScore < 110: color="info", 等级="高优先级"
          * 60 <= signalScore < 80: color="success", 等级="边际催化"
        - 互动引导：固定格式
        - 免责声明：固定格式，使用 <font color="comment">
        
        """ + A_STOCK_MODEL_CHECKLIST + """
        
        【示例输出】
        # 🌅 A股盘前异动雷达 | 2026-03-07

        过去 24 小时内，系统完成了公告去噪、事件聚类和风险分流，以下标的是盘前最值得关注的机会与风险：

        ## 机会榜

        > 1. 赛力斯 (601127) | 🇨🇳 A股
        > 📈 事件判断：<font color="warning">【强烈看多】</font>
        > 🎯 事件评分：<font color="warning">118 分 (主线级，2 个事件簇 / 4 条支撑公告)</font>
        > 🧠 核心预期差：核心产品销量和订单兑现将直接改善收入确认节奏（业务影响），高分事件叠加主线板块热度，容易吸引盘前资金抢先定价。
        
        > 2. 浪潮信息 (000977) | 🇨🇳 A股
        > 📈 事件判断：<font color="info">【谨慎看多】</font>
        > 🎯 事件评分：<font color="info">92 分 (高优先级，1 个事件簇 / 2 条支撑公告)</font>
        > 🧠 核心预期差：新增算力基础设施订单有望增厚后续收入（业务影响），但仍需观察订单兑现节奏与板块拥挤度。

        ## 风险榜

        > 1. *ST某股 (600000) | 🇨🇳 A股
        > ⚠️ 事件判断：<font color="warning">【利空预警】</font>
        > 🎯 事件评分：<font color="warning">112 分 (主线级，1 个事件簇 / 2 条支撑公告)</font>
        > 🧠 风险焦点：退市与处罚风险同时暴露（风险影响），盘前资金通常会优先回避并强化负反馈定价。

        💡 AI 深度查股：
        想看上述股票的具体新闻源？或者查询你的自选股？
        👉 请在群内直接发送：@A股分析专家 分析 [股票代码]
        
        <font color="comment">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>
        
        【请生成报告】
        """;

    // A股盘后复盘 Markdown 生成提示词（晚间复盘专属模板）
    private static final String A_EVENING_REPORT_PROMPT = """
        【角色设定】
        你是一位资深的A股盘后复盘专家，专门把公告事件翻译成当天市场为什么这样走。
        你的分析必须聚焦今日已发生的交易逻辑，而不是预测明日走势。
        
        【任务描述】
        根据提供的A股日内事件数据，生成盘后复盘报告，解释今日价格波动的核心驱动原因。
        
        【输入数据】
        报告日期：{reportDate}
        统计时段：日内（9:00-15:00）
        数据源：东方财富公告源（已完成去噪、事件聚类、规则评分）
        
        股票事件数据：
        {stockData}

        【核心分析要求 - 必须严格执行】
        1. **涨跌逻辑解码**（必须）：
           - 作为盘后复盘专家，结合事件数据解释该股票今日价格波动的核心驱动原因
           - 格式必须严格按照：【核心原因词】+ 具体逻辑解释
           - 示例："【业绩兑现】盘中公布的季度利润率远超预期，叠加管理层在日内电话会中确认了海外AI服务器追加大单，直接引发午后资金抢筹封板。"
           - 示例："【债务疑云】受外媒关于其非标债务展期谈判遇阻的传闻影响，引发市场对流动性的担忧，导致板块情绪承压。"
           - 示例："【地缘博弈】受美国相关生物安全法案最新进展的扰动，多空双方今日分歧极大，新闻面上澄清公告与外媒小作文交织，呈现宽幅震荡。"
        2. **先拆成双榜**：
           - `## 机会榜`：解释当天偏利多或可交易的中性事件为何被资金关注。
           - `## 风险榜`：解释 ST/退市风险/诉讼/处罚/立案/减持/异常波动等事件为何压制情绪。
           - 如果某一栏为空，直接写：`<font color="comment">暂无达到阈值的机会事件</font>` 或 `暂无高优先级风险事件`。
        3. **热度等级判定**：
           - 基于signalScore判断热度：
             * signalScore >= 110: 🔥 主线级
             * 80 <= signalScore < 110: 📉 高优先级（如果是利空）/ 📈 高优先级（如果是利好）
             * 60 <= signalScore < 80: ⚖️ 边际催化
        4. **结果导向**：
           - 晚间看的是"结果"（业绩兑现、利空砸盘、多空分歧），不是早间的"预期"
           - 分析要切中"今天发生了什么"，而不是"明天可能会怎样"
           - 如果只是例行公告或治理动作，不要硬写成交易主线

        【输出格式要求】
        1. 严格使用企业微信支持的 Markdown 语法
        2. 只输出最终的 Markdown 内容，不要任何解释
        3. 先写 `## 机会榜`，再写 `## 风险榜`
        4. 每个榜单最多写 3 只，按事件评分降序排列
        5. 宁缺毋滥：如果没有达到阈值的优质标的，宁可只推1只甚至不推
        6. 每只股票分析控制在 60-100 字，直击要害

        【格式规范】
        - 标题格式：# 🌆 A股盘后情绪解码 | 日期（使用傍晚图标，一级标题）
        - 开头文案："今日 A 股已收盘。系统回溯了日内（9:00-15:00）公告事件，并拆分出机会与风险两条主线："
        - 先输出 `## 机会榜`，再输出 `## 风险榜`
        - 股票条目格式（每行前面都要加 > ，形成引用块）：
          > 1. 股票名 (代码) | 🇨🇳 A股
          > 🔥/📈/📉/⚖️ 当日热度：<font color="颜色">等级 (事件评分 X 分，Y 个事件簇 / Z 条支撑公告)</font>
          > 🧠 涨跌逻辑解码：【核心原因词】+ 具体逻辑解释
        - 颜色选择规则：
          * 爆发/极度活跃: color="warning"
          * 高热/高度活跃: color="info"
          * 活跃/中度活跃: color="success"
        - 互动引导（切中散户"手里的股票被套了/卖飞了，想找原因"的心理）：
          💡 持仓深度体检：
          今天的行情让你看不懂？想查查你手里被套的股票今天有没有出什么暗雷？
          👉 请在群内直接发送：@A股分析专家 分析 [你的股票代码]
        - 免责声明：<font color="comment">⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于盘后逻辑梳理，绝不构成任何投资或交易建议。</font>
        
        """ + A_STOCK_MODEL_CHECKLIST + """
        
        【示例输出】
        # 🌆 A股盘后情绪解码 | 2026-03-12

        今日 A 股已收盘。系统回溯了日内（9:00-15:00）公告事件，并拆分出机会与风险两条主线：

        ## 机会榜

        > 1. 工业富联 (601138) | 🇨🇳 A股
        > 🔥 当日热度：<font color="warning">主线级 (事件评分 124 分，2 个事件簇 / 5 条支撑公告)</font>
        > 🧠 涨跌逻辑解码：【业绩兑现】盘中公布的季度利润率远超预期，叠加管理层在日内电话会中确认了海外 AI 服务器追加大单，直接引发午后资金抢筹封板。

        ## 风险榜

        > 2. 万科 A (000002) | 🇨🇳 A股
        > 📉 当日热度：<font color="info">高优先级 (事件评分 95 分，1 个事件簇 / 2 条支撑公告)</font>
        > 🧠 涨跌逻辑解码：【债务疑云】受外媒关于其非标债务展期谈判遇阻的传闻影响，引发市场对流动性的担忧，导致板块情绪承压。
        
        > 3. 药明康德 (603259) | 🇨🇳 A股
        > ⚖️ 当日热度：<font color="success">边际催化 (事件评分 72 分，1 个事件簇 / 2 条支撑公告)</font>
        > 🧠 涨跌逻辑解码：【地缘博弈】受美国相关生物安全法案最新进展的扰动，多空双方今日分歧极大，新闻面上澄清公告与外媒小作文交织，呈现宽幅震荡。
        
        💡 持仓深度体检：
        今天的行情让你看不懂？想查查你手里被套的股票今天有没有出什么暗雷？
        👉 请在群内直接发送：@A股分析专家 分析 [你的股票代码]
        
        <font color="comment">⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于盘后逻辑梳理，绝不构成任何投资或交易建议。</font>
        
        【请生成报告】
        """;

    // 美股夜报（盘前预警）Markdown 生成提示词（晚间模板）
    private static final String US_EVENING_REPORT_PROMPT = """
        【角色设定】
        你是一位华尔街资深投研分析师，负责将美股异动数据转化为具有交易参考价值的专业晚间雷达报告。
        你的分析必须直击交易核心，为即将开盘的美股提供预警信号。
        
        【任务描述】
        根据提供的美股异动数据，生成符合规范的企业微信推送文案。标题使用 🌃 美股夜报。
        
        【输入数据】
        报告日期：{reportDate}
        统计时长：过去24小时
        数据源数量：全网财经资讯源（已过滤行政噪音）
        
        异动股票数据：
        {stockData}
        
        【核心分析要求 - 必须严格执行】
        1. **多空情绪判定**（必须）：
           - 分析完毕后，必须明确标注情绪：<font color="warning">【强烈看多】</font> / <font color="info">【谨慎看多】</font> / <font color="comment">【中性观望】</font> / <font color="warning">【利空预警】</font>
           - 判定依据：订单/业绩/政策/技术突破等实质性驱动因素
        
        2. **强制提炼催化剂**（必须）：
           - 不要复述新闻标题！要指出这则消息会影响公司的哪部分业务
           - 格式：【业务影响】+【情绪驱动】
        
        【输出格式要求】
        1. 严格使用企业微信支持的 Markdown 语法
        2. 只输出最终的 Markdown 内容，不要任何解释
        3. 股票数量控制在 1-5 只，按异动频次降序排列
        4. 每只股票分析控制在 50-80 字，直击要害
        
        【格式规范】
        - 标题格式：# 🌃 美股夜报 | 日期（使用夜晚图标，一级标题）
        - 开头文案：系统扫描全网财经资讯源，以下标的今夜可能爆发密集异动，请注意盘前风险与机会：
        - 股票条目格式（每行前面都要加 > ，形成引用块）：
          > 1. 股票名 (代码) | 🇺🇸 美股
          > 📈 情绪雷达：<font color="warning/info/comment">【情绪判定】</font>
          > 📊 异动频次：<font color="颜色">次数 次 (等级，较昨日激增 X%)</font>
          > 🧠 核心催化剂：【业务影响】+【情绪驱动】
        - 互动引导：
          💡 AI 深度查股：
          想看上述股票的具体新闻源？或者查询你的自选股？
          👉 请在群内直接发送：@美股分析专家 分析 [股票代码]
        - 免责声明：<font color="comment">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>
        
        【请生成报告】
        """;

    // 美股早报（隔夜复盘）Markdown 生成提示词（复盘模板）
    private static final String US_OVERNIGHT_RECAP_PROMPT = """
        【角色设定】
        你是一位华尔街资深复盘专家，擅长解读昨夜美股已经发生的事实，帮助投资者理解 overnight 价格波动的核心逻辑。
        你的分析必须聚焦于"解码"昨夜盘面，解释发生了什么以及为什么会这样走。
        
        【任务描述】
        根据提供的美股异动数据，生成隔夜复盘报告，解释昨夜价格波动的核心驱动原因。
        标题使用 🌅 美股早报（隔夜复盘）。
        
        【输入数据】
        报告日期：{reportDate}
        统计时段：昨夜美股交易时段（北京时间前一日21:30-当日4:00）
        数据源数量：全网财经资讯源（已过滤行政噪音）
        
        异动股票数据：
        {stockData}
        
        【核心分析要求 - 必须严格执行】
        1. **涨跌逻辑解码**（必须）：
           - 作为隔夜复盘专家，结合新闻解释该股票昨夜价格波动的核心驱动原因
           - 格式必须严格按照：【核心原因词】+ 具体逻辑解释
           - 示例："【业绩暴雷】盘后公布的季度营收不及预期，且管理层下调全年指引，引发盘后股价暴跌15%，拖累整个板块情绪。"
           - 示例："【AI订单狂潮】传出获得微软Azure百亿级算力订单，且产能规划超预期，刺激资金彻夜抢筹，股价创历史新高。"
           - 示例："【监管黑天鹅】受FTC反垄断调查升级影响，市场对并购交易落地产生担忧，多空分歧极大，盘后波动剧烈。"
        
        2. **热度等级判定**：
           - 基于frequency判断热度：
             * frequency >= 30: 🔥 爆发
             * 20 <= frequency < 30: 📉 大跌（如果是利空）/ 📈 大涨（如果是利好）
             * 10 <= frequency < 20: ⚖️ 活跃
        
        3. **结果导向**：
           - 早上看的是"昨夜结果"（业绩兑现、利空砸盘、多空分歧），不是晚间的"预期"
           - 分析要切中"昨夜发生了什么"，以及对今日A股相关板块的传导影响
        
        【输出格式要求】
        1. 严格使用企业微信支持的 Markdown 语法
        2. 只输出最终的 Markdown 内容，不要任何解释
        3. 股票数量控制在 1-5 只，按异动频次降序排列
        4. 宁缺毋滥：如果没有达到阈值的优质标的，宁可只推1只甚至不推
        5. 每只股票分析控制在 60-100 字，直击要害
        
        【格式规范】
        - 标题格式：# 🌅 美股早报 | 日期（使用日出图标，一级标题）
        - 开头文案：昨夜美股已收盘。系统回溯了整夜（21:30-04:00）全网资讯发酵轨迹，以下标的是昨夜资金博弈的绝对核心：
        - 股票条目格式（每行前面都要加 > ，形成引用块）：
          > 1. 股票名 (代码) | 🇺🇸 美股
          > 🔥/📈/📉/⚖️ 隔夜热度：<font color="颜色">等级 (监控到 X 次高频异动)</font>
          > 🧠 涨跌逻辑解码：【核心原因词】+ 具体逻辑解释
        - 颜色选择规则：
          * 爆发/极度活跃: color="warning"
          * 高热/高度活跃: color="info"
          * 活跃/中度活跃: color="success"
        - 互动引导（切中投资者"昨夜错过大行情，想找原因"的心理）：
          💡 隔夜行情复盘：
          昨夜美股的大涨大跌让你措手不及？想查查你关注的股票出了什么重磅消息？
          👉 请在群内直接发送：@美股分析专家 分析 [股票代码]
        - 免责声明：<font color="comment">⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于隔夜逻辑梳理，绝不构成任何投资或交易建议。</font>
        
        【请生成报告】
        """;

    public AISummaryServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String summarizeUSStocks(List<USStockRss> stockList) {
        if (stockList == null || stockList.isEmpty()) {
            return "过去24小时内暂无美股异动数据。";
        }

        String stockData = formatUSStockData(stockList);
        String prompt = US_STOCK_SUMMARY_PROMPT.replace("{stockData}", stockData);

        try {
            log.info("开始调用AI总结美股数据，共 {} 只股票", stockList.size());
            String summary = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("AI总结美股数据完成");
            return summary;
        } catch (Exception e) {
            log.error("AI总结美股数据失败: {}", e.getMessage(), e);
            return generateFallbackSummary(stockList, "美股");
        }
    }

    @Override
    public String summarizeAStocks(List<AStockRss> stockList) {
        if (stockList == null || stockList.isEmpty()) {
            return "过去24小时内暂无A股异动数据。";
        }

        String stockData = formatAStockData(stockList);
        String prompt = A_STOCK_SUMMARY_PROMPT.replace("{stockData}", stockData);

        try {
            log.info("开始调用AI总结A股数据，共 {} 只股票", stockList.size());
            String summary = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("AI总结A股数据完成");
            return sanitizeModelOutput(summary);
        } catch (Exception e) {
            log.error("AI总结A股数据失败: {}", e.getMessage(), e);
            return generateFallbackSummaryA(stockList);
        }
    }

    @Override
    public String generateUSMorningReportMarkdown(List<StockAlertDTO<USStockRss>> stockAlertList, String reportDate) {
        if (stockAlertList == null || stockAlertList.isEmpty()) {
            return buildNoDataMarkdown(reportDate, "美股");
        }

        String stockData = formatUSStockAlertData(stockAlertList);
        String prompt = US_MORNING_REPORT_PROMPT
                .replace("{reportDate}", reportDate)
                .replace("{stockData}", stockData);

        try {
            log.info("开始生成美股盘前早报 Markdown，日期: {}，共 {} 只股票", reportDate, stockAlertList.size());
            String markdown = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("美股盘前早报 Markdown 生成完成");
            return markdown;
        } catch (Exception e) {
            log.error("生成美股盘前早报 Markdown 失败: {}", e.getMessage(), e);
            return generateFallbackMarkdown(stockAlertList, reportDate, "美股", "🇺🇸");
        }
    }

    @Override
    public String generateAMorningReportMarkdown(List<StockAlertDTO<AStockRss>> stockAlertList, String reportDate) {
        if (stockAlertList == null || stockAlertList.isEmpty()) {
            return buildNoDataMarkdown(reportDate, "A股");
        }

        String stockData = formatAStockAlertData(stockAlertList);
        String prompt = A_MORNING_REPORT_PROMPT
                .replace("{reportDate}", reportDate)
                .replace("{stockData}", stockData);

        try {
            log.info("开始生成A股盘前早报 Markdown，日期: {}，共 {} 只股票", reportDate, stockAlertList.size());
            String markdown = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("A股盘前早报 Markdown 生成完成");
            return ensureAStockMarkdown(markdown, stockAlertList, reportDate, true);
        } catch (Exception e) {
            log.error("生成A股盘前早报 Markdown 失败: {}", e.getMessage(), e);
            return generateAMorningFallbackMarkdown(stockAlertList, reportDate);
        }
    }

    @Override
    public String generateAEveningReportMarkdown(List<StockAlertDTO<AStockRss>> stockAlertList, String reportDate) {
        if (stockAlertList == null || stockAlertList.isEmpty()) {
            return buildAStockNoDataEveningMarkdown(reportDate);
        }

        String stockData = formatAStockAlertData(stockAlertList);
        String prompt = A_EVENING_REPORT_PROMPT
                .replace("{reportDate}", reportDate)
                .replace("{stockData}", stockData);

        try {
            log.info("开始生成A股盘后复盘 Markdown，日期: {}，共 {} 只股票", reportDate, stockAlertList.size());
            String markdown = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("A股盘后复盘 Markdown 生成完成");
            return ensureAStockMarkdown(markdown, stockAlertList, reportDate, false);
        } catch (Exception e) {
            log.error("生成A股盘后复盘 Markdown 失败: {}", e.getMessage(), e);
            return generateEveningFallbackMarkdown(stockAlertList, reportDate);
        }
    }

    @Override
    public String generateUSEveningReportMarkdown(List<StockAlertDTO<USStockRss>> stockAlertList, String reportDate) {
        if (stockAlertList == null || stockAlertList.isEmpty()) {
            return buildNoDataMarkdown(reportDate, "美股");
        }

        String stockData = formatUSStockAlertData(stockAlertList);
        String prompt = US_EVENING_REPORT_PROMPT
                .replace("{reportDate}", reportDate)
                .replace("{stockData}", stockData);

        try {
            log.info("开始生成美股夜报 Markdown，日期: {}，共 {} 只股票", reportDate, stockAlertList.size());
            String markdown = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("美股夜报 Markdown 生成完成");
            return markdown;
        } catch (Exception e) {
            log.error("生成美股夜报 Markdown 失败: {}", e.getMessage(), e);
            return generateFallbackMarkdown(stockAlertList, reportDate, "美股", "🇺🇸");
        }
    }

    @Override
    public String generateUSOvernightRecapMarkdown(List<StockAlertDTO<USStockRss>> stockAlertList, String reportDate) {
        if (stockAlertList == null || stockAlertList.isEmpty()) {
            return buildUSOvernightNoDataMarkdown(reportDate);
        }

        String stockData = formatUSStockAlertData(stockAlertList);
        String prompt = US_OVERNIGHT_RECAP_PROMPT
                .replace("{reportDate}", reportDate)
                .replace("{stockData}", stockData);

        try {
            log.info("开始生成美股早报（隔夜复盘）Markdown，日期: {}，共 {} 只股票", reportDate, stockAlertList.size());
            String markdown = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("美股早报（隔夜复盘）Markdown 生成完成");
            return markdown;
        } catch (Exception e) {
            log.error("生成美股早报（隔夜复盘）Markdown 失败: {}", e.getMessage(), e);
            return generateUSOvernightFallbackMarkdown(stockAlertList, reportDate);
        }
    }

    /**
     * 格式化美股数据（旧方法兼容）
     */
    private String formatUSStockData(List<USStockRss> stockList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stockList.size(); i++) {
            USStockRss stock = stockList.get(i);
            sb.append(i + 1).append(". ").append(stock.getStockCode()).append("\n");
            sb.append("   标题(英): ").append(stock.getTitle()).append("\n");
            sb.append("   标题(中): ").append(stock.getTitleZh() != null ? stock.getTitleZh() : "N/A").append("\n");
            sb.append("   标签: ").append(stock.getTags() != null ? stock.getTags() : "N/A").append("\n");
            sb.append("   时间: ").append(stock.getPubDateBj() != null ? stock.getPubDateBj().format(DATE_FORMATTER) : "N/A").append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 格式化A股数据（旧方法兼容）
     */
    private String formatAStockData(List<AStockRss> stockList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stockList.size(); i++) {
            AStockRss stock = stockList.get(i);
            sb.append("### EventCard ").append(i + 1).append("\n");
            sb.append("stock_code: ").append(stock.getStockCode()).append("\n");
            sb.append("stock_name: ").append(stock.getStockName() != null ? stock.getStockName() : stock.getStockCode()).append("\n");
            sb.append("signal_score: ").append(stock.getSignalScore() != null ? stock.getSignalScore() : 0).append("\n");
            sb.append("signal_side: ").append(stock.getSignalSide() != null ? stock.getSignalSide() : "中性").append("\n");
            sb.append("event_type: ").append(stock.getEventType() != null ? stock.getEventType() : "常规事项").append("\n");
            sb.append("latest_notice_title: ").append(stock.getTitle() != null ? stock.getTitle() : "N/A").append("\n");
            sb.append("tags: ").append(stock.getTag() != null ? stock.getTag() : "N/A").append("\n");
            sb.append("analysis_hint: ").append(stock.getAnalysisHint() != null ? stock.getAnalysisHint() : "请优先判断是否存在实质性预期差").append("\n");
            sb.append("cluster_highlights:\n").append(indentMultiline(stock.getClusterHighlights() != null ? stock.getClusterHighlights() : "N/A", "  - ")).append("\n");
            sb.append("time: ").append(stock.getPubDate() != null ? stock.getPubDate().format(DATE_FORMATTER) : "N/A").append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 格式化美股异动数据（包含频次和所有相关标题）
     */
    private String formatUSStockAlertData(List<StockAlertDTO<USStockRss>> stockAlertList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stockAlertList.size(); i++) {
            StockAlertDTO<USStockRss> dto = stockAlertList.get(i);
            USStockRss stock = dto.getStock();
            sb.append(i + 1).append(". ").append(stock.getStockCode()).append("\n");
            sb.append("   名称: ").append(stock.getStockCode()).append("\n");
            sb.append("   异动频次: ").append(dto.getFrequency()).append(" 次\n");
            sb.append("   活跃度: ").append(dto.getActivityLevel()).append("\n");
            sb.append("   颜色标签: ").append(dto.getColorTag()).append("\n");
            sb.append("   最新标题(英): ").append(stock.getTitle() != null ? stock.getTitle() : "N/A").append("\n");
            sb.append("   最新标题(中): ").append(stock.getTitleZh() != null ? stock.getTitleZh() : "N/A").append("\n");
            sb.append("   所有相关标题(供提炼催化剂): ").append(stock.getTags() != null ? stock.getTags() : "N/A").append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 格式化A股异动数据（包含频次和所有相关标题）
     */
    private String formatAStockAlertData(List<StockAlertDTO<AStockRss>> stockAlertList) {
        StringBuilder sb = new StringBuilder();
        AStockReportSections sections = splitAStockReportSections(stockAlertList);
        appendAStockEventCards(sb, "## OpportunityCandidates", sections.opportunities());
        appendAStockEventCards(sb, "## RiskCandidates", sections.risks());
        return sb.toString();
    }

    private void appendAStockEventCards(StringBuilder sb,
                                        String sectionTitle,
                                        List<StockAlertDTO<AStockRss>> alerts) {
        sb.append(sectionTitle).append("\n");
        if (alerts.isEmpty()) {
            sb.append("- none\n\n");
            return;
        }
        for (int i = 0; i < alerts.size(); i++) {
            StockAlertDTO<AStockRss> dto = alerts.get(i);
            AStockRss stock = dto.getStock();
            sb.append("### EventCard ").append(i + 1).append("\n");
            sb.append("stock_code: ").append(stock.getStockCode()).append("\n");
            sb.append("stock_name: ").append(stock.getStockName() != null ? stock.getStockName() : stock.getStockCode()).append("\n");
            sb.append("signal_score: ").append(dto.getSignalScore()).append("\n");
            sb.append("signal_level: ").append(dto.getSignalLevel()).append("\n");
            sb.append("signal_side: ").append(dto.getSignalSide()).append("\n");
            sb.append("event_cluster_count: ").append(dto.getEventCount()).append("\n");
            sb.append("support_notice_count: ").append(dto.getFrequency()).append("\n");
            sb.append("event_type: ").append(stock.getEventType() != null ? stock.getEventType() : "N/A").append("\n");
            sb.append("color_tag: ").append(dto.getSignalColorTag()).append("\n");
            sb.append("analysis_hint: ").append(stock.getAnalysisHint() != null ? stock.getAnalysisHint() : "N/A").append("\n");
            sb.append("latest_notice_title: ").append(stock.getTitle() != null ? stock.getTitle() : "N/A").append("\n");
            sb.append("related_titles: ").append(stock.getRelatedTitles() != null ? stock.getRelatedTitles() : "N/A").append("\n");
            sb.append("cluster_highlights:\n").append(indentMultiline(stock.getClusterHighlights() != null ? stock.getClusterHighlights() : "N/A", "  - ")).append("\n");
            sb.append("\n");
        }
    }

    /**
     * 生成美股降级总结（当AI调用失败时使用）
     */
    private String generateFallbackSummary(List<USStockRss> stockList, String marketType) {
        StringBuilder sb = new StringBuilder();
        sb.append("过去24小时内").append(marketType).append("异动TOP5：\n\n");
        for (int i = 0; i < stockList.size(); i++) {
            USStockRss stock = stockList.get(i);
            sb.append(i + 1).append(". **").append(stock.getStockCode()).append("**");
            sb.append(" - ").append(stock.getTitleZh() != null ? stock.getTitleZh() : stock.getTitle());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成A股降级总结（当AI调用失败时使用）
     */
    private String generateFallbackSummaryA(List<AStockRss> stockList) {
        StringBuilder sb = new StringBuilder();
        sb.append("过去24小时内A股核心事件TOP5：\n\n");
        for (int i = 0; i < stockList.size(); i++) {
            AStockRss stock = stockList.get(i);
            sb.append(i + 1).append(". **").append(stock.getStockCode()).append("** (").append(stock.getStockName()).append(")");
            sb.append(" - ").append(stock.getTitle());
            sb.append("【").append(stock.getEventType() != null ? stock.getEventType() : stock.getTag()).append(" / ");
            sb.append(stock.getSignalSide() != null ? stock.getSignalSide() : "中性").append(" / ");
            sb.append(stock.getSignalScore() != null ? stock.getSignalScore() : 0).append("分】");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建无数据时的 Markdown 消息
     */
    private String buildNoDataMarkdown(String reportDate, String market) {
        String botName = market.equals("美股") ? "@美股分析专家" : "@A股分析专家";
        String thresholdDesc = market.equals("A股")
                ? "（当前阈值：事件评分 >= 60 分，且已过滤治理/理财/会务等噪音公告）"
                : "（当前阈值：24小时内同一标的异动 >= 10 次，宁缺毋滥）";
        return "# 🌅 AI 盘前异动雷达 | " + reportDate + "\n\n" +
               "过去 24 小时内，系统共扫描全网财经资讯源（已过滤行政噪音）。\n\n" +
               "<font color=\"warning\">⚠️ 暂无" + market + "异动数据</font>\n" +
               "<font color=\"comment\">" + thresholdDesc + "</font>\n\n" +
               "💡 AI 深度查股：\n" +
               "想看具体股票分析？请在群内直接发送：" + botName + " 分析 [股票代码]\n\n" +
               "<font color=\"comment\">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>";
    }

    private String buildAStockNoDataEveningMarkdown(String reportDate) {
        return "# 🌆 A股盘后情绪解码 | " + reportDate + "\n\n" +
               "今日 A 股已收盘。系统回溯了日内（9:00-15:00）公告事件，并拆分出机会与风险两条主线。\n\n" +
               "<font color=\"warning\">⚠️ 暂无 🇨🇳 A股需要解码的盘后事件</font>\n" +
               "<font color=\"comment\">（当前阈值：事件评分 >= 60 分，且已过滤治理/理财/会务等噪音公告）</font>\n\n" +
               "💡 持仓深度体检：\n" +
               "今天的行情让你看不懂？想查查你手里被套的股票今天有没有出什么暗雷？\n" +
               "👉 请在群内直接发送：@A股分析专家 分析 [你的股票代码]\n\n" +
               "<font color=\"comment\">⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于盘后逻辑梳理，绝不构成任何投资或交易建议。</font>";
    }

    /**
     * 计算激增比例描述（降级方案使用）
     */
    private String getSurgeDescription(int frequency) {
        if (frequency >= 25) {
            return "较昨日激增 400%+";
        } else if (frequency >= 15) {
            return "较昨日激增 200%+";
        } else if (frequency >= 10) {
            return "较昨日显著上升";
        } else {
            return "活跃度一般";
        }
    }

    private String sanitizeModelOutput(String output) {
        if (output == null) {
            return "";
        }
        String cleaned = output.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        return cleaned.trim();
    }

    private String ensureAStockMarkdown(String markdown,
                                        List<StockAlertDTO<AStockRss>> stockAlertList,
                                        String reportDate,
                                        boolean morning) {
        String cleaned = sanitizeModelOutput(markdown);
        if (cleaned.isBlank()
                || !cleaned.startsWith("#")
                || (!cleaned.contains("## 机会榜") && !cleaned.contains("## 风险榜"))) {
            log.warn("A股 Markdown 输出不符合预期，使用降级模板。内容: {}", cleaned);
            return morning
                    ? generateAMorningFallbackMarkdown(stockAlertList, reportDate)
                    : generateEveningFallbackMarkdown(stockAlertList, reportDate);
        }
        return cleaned;
    }

    private AStockReportSections splitAStockReportSections(List<StockAlertDTO<AStockRss>> stockAlertList) {
        List<StockAlertDTO<AStockRss>> opportunities = new ArrayList<>();
        List<StockAlertDTO<AStockRss>> risks = new ArrayList<>();

        if (stockAlertList == null) {
            return new AStockReportSections(opportunities, risks);
        }

        for (StockAlertDTO<AStockRss> dto : stockAlertList) {
            if (dto == null || dto.getStock() == null) {
                continue;
            }
            if (isRiskAlert(dto)) {
                if (risks.size() < A_STOCK_SECTION_LIMIT) {
                    risks.add(dto);
                }
            } else if (opportunities.size() < A_STOCK_SECTION_LIMIT) {
                opportunities.add(dto);
            }

            if (opportunities.size() >= A_STOCK_SECTION_LIMIT && risks.size() >= A_STOCK_SECTION_LIMIT) {
                break;
            }
        }

        return new AStockReportSections(opportunities, risks);
    }

    private boolean isRiskAlert(StockAlertDTO<AStockRss> dto) {
        if (dto == null || dto.getStock() == null) {
            return false;
        }

        AStockRss stock = dto.getStock();
        if ("利空".equals(dto.getSignalSide())) {
            return true;
        }
        if (containsAnyKeyword(stock.getEventType(), A_STOCK_RISK_EVENT_KEYWORDS)) {
            return true;
        }
        if (containsAnyKeyword(stock.getTitle(), A_STOCK_RISK_TITLE_KEYWORDS)
                || containsAnyKeyword(stock.getAnalysisHint(), A_STOCK_RISK_TITLE_KEYWORDS)) {
            return true;
        }

        String stockName = stock.getStockName();
        return stockName != null && stockName.toUpperCase(Locale.ROOT).contains("ST");
    }

    private boolean containsAnyKeyword(String text, List<String> keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        return keywords.stream()
                .map(keyword -> keyword.toUpperCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private String indentMultiline(String text, String prefix) {
        if (text == null || text.isBlank()) {
            return prefix + "N/A";
        }
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> prefix + line)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 生成降级 Markdown（当AI调用失败时使用）
     */
    private <T> String generateFallbackMarkdown(List<StockAlertDTO<T>> stockAlertList, String reportDate, 
                                                  String market, String flag) {
        StringBuilder sb = new StringBuilder();
        String reportTitle = market.equals("美股") ? "# 🌅 AI 盘前异动雷达 | " : "# 🌅 A股盘前异动雷达 | ";
        sb.append(reportTitle).append(reportDate).append("\n\n");
        sb.append("过去 24 小时内，系统共扫描全网财经资讯源（已过滤行政噪音），以下标的爆发密集异动，请注意盘前风险与机会：\n\n");

        for (int i = 0; i < stockAlertList.size(); i++) {
            StockAlertDTO<T> dto = stockAlertList.get(i);
            String stockCode;
            String stockName = null;
            String title = null;
            String tag = null;

            if (dto.getStock() instanceof USStockRss) {
                USStockRss stock = (USStockRss) dto.getStock();
                stockCode = stock.getStockCode();
                stockName = stockCode;
                title = stock.getTitleZh() != null ? stock.getTitleZh() : stock.getTitle();
                tag = stock.getTags();
            } else if (dto.getStock() instanceof AStockRss) {
                AStockRss stock = (AStockRss) dto.getStock();
                stockCode = stock.getStockCode();
                stockName = stock.getStockName();
                title = stock.getTitle();
                tag = stock.getRelatedTitles() != null ? stock.getRelatedTitles() : stock.getTag();
            } else {
                stockCode = "未知";
            }

            String displayName = stockName != null ? stockName : stockCode;
            
            sb.append("> ").append(i + 1).append(". ").append(displayName).append(" (").append(stockCode).append(") | ").append(flag).append(" ").append(market).append("\n");
            if (dto.getStock() instanceof AStockRss) {
                sb.append("> 📈 事件判断：<font color=\"comment\">【AI分析中...】</font>\n");
                sb.append("> 🎯 事件评分：<font color=\"").append(dto.getSignalColorTag()).append("\">")
                        .append(dto.getSignalScore()).append(" 分 (")
                        .append(dto.getSignalLevel()).append("，")
                        .append(dto.getEventCount()).append(" 个事件簇 / ")
                        .append(dto.getFrequency()).append(" 条支撑公告)</font>\n");
            } else {
                String surgeDesc = getSurgeDescription(dto.getFrequency());
                sb.append("> 📈 情绪雷达：<font color=\"comment\">【AI分析中...】</font>\n");
                sb.append("> 📊 异动频次：<font color=\"").append(dto.getColorTag()).append("\">").append(dto.getFrequency()).append(" 次 (").append(dto.getActivityLevel()).append(", ").append(surgeDesc).append(")</font>\n");
            }
            sb.append("> 🧠 核心催化剂：").append(title != null ? title : "暂无详细分析");
            if (tag != null && !tag.isEmpty()) {
                sb.append(" 相关标题：[").append(tag.substring(0, Math.min(50, tag.length()))).append("...]");
            }
            sb.append("\n\n");
        }

        String botName = market.equals("美股") ? "@美股分析专家" : "@A股分析专家";
        sb.append("💡 AI 深度查股：\n");
        sb.append("想看上述股票的具体新闻源？或者查询你的自选股？\n");
        sb.append("👉 请在群内直接发送：").append(botName).append(" 分析 [股票代码]\n\n");
        sb.append("<font color=\"comment\">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>");

        return sb.toString();
    }

    private String generateAMorningFallbackMarkdown(List<StockAlertDTO<AStockRss>> stockAlertList, String reportDate) {
        AStockReportSections sections = splitAStockReportSections(stockAlertList);
        StringBuilder sb = new StringBuilder();
        sb.append("# 🌅 A股盘前异动雷达 | ").append(reportDate).append("\n\n");
        sb.append("过去 24 小时内，系统完成了公告去噪、事件聚类和风险分流，以下标的是盘前最值得关注的机会与风险：\n\n");
        appendAMorningSection(sb, "机会榜", "暂无达到阈值的机会事件", sections.opportunities(), false);
        appendAMorningSection(sb, "风险榜", "暂无高优先级风险事件", sections.risks(), true);
        sb.append("💡 AI 深度查股：\n");
        sb.append("想看上述股票的具体公告源？或者查询你的自选股？\n");
        sb.append("👉 请在群内直接发送：@A股分析专家 分析 [股票代码]\n\n");
        sb.append("<font color=\"comment\">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>");
        return sb.toString();
    }

    private void appendAMorningSection(StringBuilder sb,
                                       String sectionTitle,
                                       String emptyText,
                                       List<StockAlertDTO<AStockRss>> alerts,
                                       boolean riskSection) {
        sb.append("## ").append(sectionTitle).append("\n\n");
        if (alerts.isEmpty()) {
            sb.append("<font color=\"comment\">").append(emptyText).append("</font>\n\n");
            return;
        }
        for (int i = 0; i < alerts.size(); i++) {
            StockAlertDTO<AStockRss> dto = alerts.get(i);
            AStockRss stock = dto.getStock();
            String stockCode = stock.getStockCode();
            String displayName = stock.getStockName() != null ? stock.getStockName() : stockCode;
            String title = stock.getTitle() != null ? stock.getTitle() : "暂无详细分析";
            String tag = stock.getRelatedTitles() != null ? stock.getRelatedTitles() : stock.getTag();

            sb.append("> ").append(i + 1).append(". ").append(displayName).append(" (").append(stockCode).append(") | 🇨🇳 A股\n");
            sb.append("> ").append(riskSection ? "⚠️" : "📈").append(" 事件判断：<font color=\"")
                    .append(dto.getSignalColorTag()).append("\">【")
                    .append(resolveFallbackSignalLabel(dto)).append("】</font>\n");
            sb.append("> 🎯 事件评分：<font color=\"").append(dto.getSignalColorTag()).append("\">")
                    .append(dto.getSignalScore()).append(" 分 (")
                    .append(dto.getSignalLevel()).append("，")
                    .append(dto.getEventCount()).append(" 个事件簇 / ")
                    .append(dto.getFrequency()).append(" 条支撑公告)</font>\n");
            sb.append("> ").append(riskSection ? "🧠 风险焦点：" : "🧠 核心预期差：")
                    .append(title);
            if (tag != null && !tag.isEmpty()) {
                sb.append(" 相关标题：[").append(tag.substring(0, Math.min(50, tag.length()))).append("...]");
            }
            sb.append("\n\n");
        }
    }

    private String resolveFallbackSignalLabel(StockAlertDTO<AStockRss> dto) {
        if ("利空".equals(dto.getSignalSide())) {
            return "利空预警";
        }
        if (dto.getSignalScore() >= 110) {
            return "强烈看多";
        }
        if (dto.getSignalScore() >= 80) {
            return "谨慎看多";
        }
        return "中性观望";
    }

    /**
     * 构建美股早报（隔夜复盘）无数据 Markdown 消息
     */
    private String buildUSOvernightNoDataMarkdown(String reportDate) {
        return "# 🌅 美股早报 | " + reportDate + "\n\n" +
               "昨夜美股已收盘。系统回溯了整夜（21:30-04:00）全网资讯发酵轨迹。\n\n" +
               "<font color=\"warning\">⚠️ 暂无 🇺🇸 美股需要解码的隔夜异动数据</font>\n" +
               "<font color=\"comment\">（当前阈值：24小时内同一标的异动 >= 10 次，宁缺毋滥）</font>\n\n" +
               "💡 隔夜行情复盘：\n" +
               "昨夜美股的大涨大跌让你措手不及？想查查你关注的股票出了什么重磅消息？\n" +
               "👉 请在群内直接发送：@美股分析专家 分析 [股票代码]\n\n" +
               "<font color=\"comment\">⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于隔夜逻辑梳理，绝不构成任何投资或交易建议。</font>";
    }

    /**
     * 生成美股隔夜复盘降级 Markdown（当AI调用失败时使用）
     */
    private String generateUSOvernightFallbackMarkdown(List<StockAlertDTO<USStockRss>> stockAlertList, String reportDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 🌅 美股早报 | ").append(reportDate).append("\n\n");
        sb.append("昨夜美股已收盘。系统回溯了整夜（21:30-04:00）全网资讯发酵轨迹，以下标的是昨夜资金博弈的绝对核心：\n\n");

        for (int i = 0; i < stockAlertList.size(); i++) {
            StockAlertDTO<USStockRss> dto = stockAlertList.get(i);
            USStockRss stock = dto.getStock();
            String stockCode = stock.getStockCode();
            String title = stock.getTitleZh() != null ? stock.getTitleZh() : stock.getTitle();
            String tag = stock.getTags();

            String heatIcon;
            String heatLevel;
            if (dto.getFrequency() >= 30) {
                heatIcon = "🔥";
                heatLevel = "爆发";
            } else if (dto.getFrequency() >= 20) {
                heatIcon = dto.getColorTag().equals("warning") ? "📉" : "📈";
                heatLevel = "高热";
            } else {
                heatIcon = "⚖️";
                heatLevel = "活跃";
            }
            
            sb.append("> ").append(i + 1).append(". ").append(stockCode).append(" | 🇺🇸 美股\n");
            sb.append("> ").append(heatIcon).append(" 隔夜热度：<font color=\"").append(dto.getColorTag()).append("\">").append(heatLevel).append(" (监控到 ").append(dto.getFrequency()).append(" 次高频异动)</font>\n");
            sb.append("> 🧠 涨跌逻辑解码：[AI分析中...] ").append(title != null ? title : "暂无详细分析");
            if (tag != null && !tag.isEmpty()) {
                sb.append(" 相关标题：[").append(tag.substring(0, Math.min(50, tag.length()))).append("...]");
            }
            sb.append("\n\n");
        }

        sb.append("💡 隔夜行情复盘：\n");
        sb.append("昨夜美股的大涨大跌让你措手不及？想查查你关注的股票出了什么重磅消息？\n");
        sb.append("👉 请在群内直接发送：@美股分析专家 分析 [股票代码]\n\n");
        sb.append("<font color=\"comment\">⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于隔夜逻辑梳理，绝不构成任何投资或交易建议。</font>");

        return sb.toString();
    }

    /**
     * 生成A股盘后复盘降级 Markdown（当AI调用失败时使用）
     */
    private String generateEveningFallbackMarkdown(List<StockAlertDTO<AStockRss>> stockAlertList, String reportDate) {
        AStockReportSections sections = splitAStockReportSections(stockAlertList);
        StringBuilder sb = new StringBuilder();
        sb.append("# 🌆 A股盘后情绪解码 | ").append(reportDate).append("\n\n");
        sb.append("今日 A 股已收盘。系统回溯了日内（9:00-15:00）公告事件，并拆分出机会与风险两条主线：\n\n");
        appendAEveningSection(sb, "机会榜", "暂无达到阈值的机会事件", sections.opportunities());
        appendAEveningSection(sb, "风险榜", "暂无高优先级风险事件", sections.risks());

        sb.append("💡 持仓深度体检：\n");
        sb.append("今天的行情让你看不懂？想查查你手里被套的股票今天有没有出什么暗雷？\n");
        sb.append("👉 请在群内直接发送：@A股分析专家 分析 [你的股票代码]\n\n");
        sb.append("<font color=\"comment\">⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于盘后逻辑梳理，绝不构成任何投资或交易建议。</font>");

        return sb.toString();
    }

    private void appendAEveningSection(StringBuilder sb,
                                       String sectionTitle,
                                       String emptyText,
                                       List<StockAlertDTO<AStockRss>> alerts) {
        sb.append("## ").append(sectionTitle).append("\n\n");
        if (alerts.isEmpty()) {
            sb.append("<font color=\"comment\">").append(emptyText).append("</font>\n\n");
            return;
        }

        for (int i = 0; i < alerts.size(); i++) {
            StockAlertDTO<AStockRss> dto = alerts.get(i);
            AStockRss stock = dto.getStock();
            String stockCode = stock.getStockCode();
            String displayName = stock.getStockName() != null ? stock.getStockName() : stockCode;
            String title = stock.getTitle() != null ? stock.getTitle() : "暂无详细分析";
            String tag = stock.getRelatedTitles() != null ? stock.getRelatedTitles() : stock.getTag();
            String heatIcon;
            String heatLevel;
            if (dto.getSignalScore() >= 110) {
                heatIcon = "🔥";
                heatLevel = "主线级";
            } else if (dto.getSignalScore() >= 80) {
                heatIcon = "利空".equals(dto.getSignalSide()) ? "📉" : "📈";
                heatLevel = "高优先级";
            } else {
                heatIcon = "⚖️";
                heatLevel = "边际催化";
            }

            sb.append("> ").append(i + 1).append(". ").append(displayName).append(" (").append(stockCode).append(") | 🇨🇳 A股\n");
            sb.append("> ").append(heatIcon).append(" 当日热度：<font color=\"").append(dto.getSignalColorTag()).append("\">")
                    .append(heatLevel).append(" (事件评分 ").append(dto.getSignalScore()).append(" 分，")
                    .append(dto.getEventCount()).append(" 个事件簇 / ").append(dto.getFrequency()).append(" 条支撑公告)</font>\n");
            sb.append("> 🧠 涨跌逻辑解码：[AI分析中...] ").append(title);
            if (tag != null && !tag.isEmpty()) {
                sb.append(" 相关标题：[").append(tag.substring(0, Math.min(50, tag.length()))).append("...]");
            }
            sb.append("\n\n");
        }
    }

    private record AStockReportSections(List<StockAlertDTO<AStockRss>> opportunities,
                                        List<StockAlertDTO<AStockRss>> risks) {
    }
}
