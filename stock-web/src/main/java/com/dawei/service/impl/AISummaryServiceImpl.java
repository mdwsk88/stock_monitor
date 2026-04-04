package com.dawei.service.impl;

import com.dawei.entity.AReportFusionContext;
import com.dawei.entity.AReportOpportunityInsight;
import com.dawei.entity.AReportResonanceCard;
import com.dawei.entity.AStockRss;
import com.dawei.entity.MarketSnapshot;
import com.dawei.entity.MarketState;
import com.dawei.entity.MacroThemeEvent;
import com.dawei.entity.StockAlertDTO;
import com.dawei.entity.USStockRss;
import com.dawei.service.AISummaryService;
import com.dawei.utils.PushLanguageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
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
    private final AStockReportClassifier aStockReportClassifier;
    private final PushLanguageService pushLanguageService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int A_STOCK_SECTION_LIMIT = 3;
    private static final Pattern HAN_PATTERN = Pattern.compile("\\p{IsHan}");

    public AISummaryServiceImpl(ChatClient chatClient) {
        this(chatClient, new AStockReportClassifier(), new PushLanguageService());
    }

    public AISummaryServiceImpl(ChatClient chatClient, AStockReportClassifier aStockReportClassifier) {
        this(chatClient, aStockReportClassifier, new PushLanguageService());
    }

    @Autowired
    public AISummaryServiceImpl(ChatClient chatClient,
                                AStockReportClassifier aStockReportClassifier,
                                PushLanguageService pushLanguageService) {
        this.chatClient = chatClient;
        this.aStockReportClassifier = aStockReportClassifier;
        this.pushLanguageService = pushLanguageService;
    }

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

        市场状态快照：
        {marketContext}

        宏观主题数据：
        {macroThemeData}

        共振标的数据：
        {resonanceData}

        股票事件数据：
        {stockData}

        【核心分析要求 - 必须严格执行】
        0. 先读取市场状态快照：
           - `防守态`：不要鼓励追高，偏利多标的除非是绝对核心共振，否则只能降级表达。
           - `进攻态`：优先识别真正能带队的龙头，不要把普通跟风股写成主升核心。
           - `高潮态`：允许保留强龙头，但必须明确提示后排追高与次日兑现风险。
        1. 先判断有没有实质性事件：
           - 只关注业绩超预期、重大合同、中标、并购重组、产品获批、回购增持、立案调查、减持、诉讼处罚等会改变预期的事件。
           - 如果只是治理、会务、理财、募集资金例行动作、补充披露等常规事务，直接忽略，不要强行写成机会。
        2. 必须先分栏输出：
           - `## 宏观主线`：只写真正会影响明日交易主线的政策、行业、流动性和跨境风险。
           - `## 共振标的`：只写“公告事件”和“宏观主题”同时命中的股票，解释为什么它比普通公告更值得高看或回避。
           - `## 机会榜`：只写偏利多或可交易的中性事件。
           - `## 风险榜`：只写 ST/退市风险/诉讼/处罚/立案/减持/异常波动等利空或高风险事件。
           - 如果某一栏没有达到阈值的标的，直接写：`<font color="comment">暂无达到阈值的机会事件</font>`、`暂无高优先级风险事件`、`暂无达到阈值的宏观主线`、`暂无公告与主题共振标的`。
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
        3. 先写 `## 宏观主线`，再写 `## 共振标的`，然后是 `## 机会榜` 和 `## 风险榜`
        4. `宏观主线` 最多写 3 条，`共振标的/机会榜/风险榜` 各最多写 3 只
        5. 宁缺毋滥：如果没有达到阈值的优质标的，宁可只推1只甚至不推
        6. 每只股票分析控制在 50-80 字，直击要害

        【格式规范】
        - 标题格式：🌅 AI 盘前异动雷达 | 日期
        - 开头统计行：扫描时长、数据源（已做去噪、事件聚类和风险分流）
        - 股票条目格式：
          ## 宏观主线
          1. 主题名 | 事件类型
          🧭 方向判断：<font color="warning/info/comment">【利多/中性/利空】</font>
          🎯 主题强度：<font color="颜色">分数 分 (主线级/高优先级/边际催化，关联 N 只映射标的)</font>
          🧠 主线解读：【政策/行业影响】+【资金可能如何沿主题交易】
          ## 共振标的
          1. 股票名 (代码) | 宏观主题
          🔗 共振强度：<font color="颜色">分数 分 (强共振/高共振/弱共振)</font>
          🧠 共振逻辑：【公告催化】+【宏观主题如何放大资金关注度】
          ## 机会榜
          1. 股票名 (代码) | 🇨🇳 A股
          📈 事件判断：<font color="warning">【强烈看多】</font> / <font color="info">【谨慎看多】</font> / <font color="comment">【中性观望】</font> / <font color="warning">【利空预警】</font>
          🏷️ 身位判定：<font color="warning/info/comment">【领军核心/高弹性跟风/观察名单】</font>
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

        ## 宏观主线

        > 1. 算力 | 政策扶持
        > 🧭 方向判断：<font color="info">【利多】</font>
        > 🎯 主题强度：<font color="info">90 分 (高优先级，关联 3 只映射标的)</font>
        > 🧠 主线解读：智算基础设施建设再获强化（政策影响），资金容易沿算力链做扩散与映射交易。

        ## 共振标的

        > 1. 浪潮信息 (000977) | 算力
        > 🔗 共振强度：<font color="warning">136 分 (强共振)</font>
        > 🧠 共振逻辑：服务器订单与算力公告本身已具备交易预期差（公告催化），叠加算力主线升温后，盘前更容易获得资金抢跑定价。

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

        市场状态快照：
        {marketContext}

        宏观主题数据：
        {macroThemeData}

        共振标的数据：
        {resonanceData}

        股票事件数据：
        {stockData}

        【核心分析要求 - 必须严格执行】
        0. 先读取市场状态快照：
           - `防守态`：重点解释风险扩散与防守信号，不要把普通反弹包装成主线。
           - `进攻态`：重点辨识谁是领涨核心，谁只是跟风扩散。
           - `高潮态`：必须提示情绪过热、炸板回落和后排掉队风险。
        1. **涨跌逻辑解码**（必须）：
           - 作为盘后复盘专家，结合事件数据解释该股票今日价格波动的核心驱动原因
           - 格式必须严格按照：【核心原因词】+ 具体逻辑解释
           - 示例："【业绩兑现】盘中公布的季度利润率远超预期，叠加管理层在日内电话会中确认了海外AI服务器追加大单，直接引发午后资金抢筹封板。"
           - 示例："【债务疑云】受外媒关于其非标债务展期谈判遇阻的传闻影响，引发市场对流动性的担忧，导致板块情绪承压。"
           - 示例："【地缘博弈】受美国相关生物安全法案最新进展的扰动，多空双方今日分歧极大，新闻面上澄清公告与外媒小作文交织，呈现宽幅震荡。"
        2. **先拆成双榜**：
           - `## 宏观主线`：解释今天真正影响盘面的政策、行业、流动性和外部风险。
           - `## 共振标的`：解释哪些个股同时被“公告事件”和“宏观主线”共振放大。
           - `## 机会榜`：解释当天偏利多或可交易的中性事件为何被资金关注。
           - `## 风险榜`：解释 ST/退市风险/诉讼/处罚/立案/减持/异常波动等事件为何压制情绪。
           - 如果某一栏为空，直接写：`<font color="comment">暂无达到阈值的机会事件</font>`、`暂无高优先级风险事件`、`暂无达到阈值的宏观主线`、`暂无公告与主题共振标的`。
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
        3. 先写 `## 宏观主线`，再写 `## 共振标的`，然后是 `## 机会榜` 和 `## 风险榜`
        4. `宏观主线` 最多写 3 条，`共振标的/机会榜/风险榜` 各最多写 3 只，按重要性降序排列
        5. 宁缺毋滥：如果没有达到阈值的优质标的，宁可只推1只甚至不推
        6. 每只股票分析控制在 60-100 字，直击要害

        【格式规范】
        - 标题格式：# 🌆 A股盘后情绪解码 | 日期（使用傍晚图标，一级标题）
        - 开头文案："今日 A 股已收盘。系统回溯了日内（9:00-15:00）公告事件，并叠加宏观主题线索，拆分出机会、风险与共振三条主线："
        - 先输出 `## 宏观主线`、`## 共振标的`，再输出 `## 机会榜` 和 `## 风险榜`
        - 股票条目格式（每行前面都要加 > ，形成引用块）：
          > 1. 主题名 | 事件类型
          > 🧭 主线方向：<font color="warning/info/comment">【利多/中性/利空】</font>
          > 🎯 主题强度：<font color="颜色">分数 分 (等级，关联 N 只映射标的)</font>
          > 🧠 盘面影响解码：【核心原因词】+ 主题为什么会对盘面造成影响
          > 1. 股票名 (代码) | 宏观主题
          > 🔗 共振强度：<font color="颜色">分数 分 (强共振/高共振/弱共振)</font>
          > 🧠 共振逻辑解码：【公告影响】+【主题放大后的资金交易方式】
          > 1. 股票名 (代码) | 🇨🇳 A股
          > 🏷️ 身位判定：<font color="warning/info/comment">【领军核心/高弹性跟风/观察名单】</font>
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

        今日 A 股已收盘。系统回溯了日内（9:00-15:00）公告事件，并叠加宏观主题线索，拆分出机会、风险与共振三条主线：

        ## 宏观主线

        > 1. 算力 | 政策扶持
        > 🧭 主线方向：<font color="info">【利多】</font>
        > 🎯 主题强度：<font color="info">90 分 (高优先级，关联 3 只映射标的)</font>
        > 🧠 盘面影响解码：【产业强化】智算基础设施规范落地后，算力链资金更容易围绕服务器、光模块和国产算力平台做集中交易。

        ## 共振标的

        > 1. 工业富联 (601138) | 算力
        > 🔗 共振强度：<font color="warning">138 分 (强共振)</font>
        > 🧠 共振逻辑解码：【订单兑现】服务器订单本就强化了业绩预期，叠加算力主线再升温后，午后资金更容易顺着主题做加速抢筹。

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

    private static final String A_MORNING_REPORT_PROMPT_EN = """
        [Role]
        You are an A-stock event-driven strategist writing a professional WeCom pre-market note.
        Your job is not to repeat notice headlines. Your job is to explain the catalysts that can change trading expectations before the open.

        [Task]
        Use the A-stock event data below to produce a market note in English.

        [Input]
        Report date: {reportDate}
        Window: latest 24 hours
        Source: Eastmoney notice feed (noise filtered, event-clustered, rule-scored)

        Market state snapshot:
        {marketContext}

        Macro theme data:
        {macroThemeData}

        Resonance data:
        {resonanceData}

        Stock event data:
        {stockData}

        [Rules]
        1. Read the market snapshot first.
           - Defensive: do not encourage blind chasing; only keep truly resilient or high-conviction setups.
           - Risk-On: prioritize leaders and catalysts that can pull the theme forward.
           - Overheat: keep strong leaders if necessary, but explicitly warn about crowded laggards and next-day fade risk.
        2. Focus only on events with real trading value: earnings surprise, major contracts, winning bids, M&A, product approval, buybacks, insider increases, investigations, litigation, penalties, delisting risk, or clear macro/liquidity shifts.
        3. Ignore routine governance, meetings, wealth-management products, fundraising housekeeping, and other administrative noise.
        4. Output sections in this exact order:
           - ## Macro Themes
           - ## Resonance Picks
           - ## Opportunity Board
           - ## Risk Board
        5. If a section has no valid candidates, use the exact English empty-state text below:
           - <font color="comment">No macro theme met the threshold</font>
           - <font color="comment">No notice-theme resonance pick met the threshold</font>
           - <font color="comment">No opportunity event met the threshold</font>
           - <font color="comment">No high-priority risk event was detected</font>
        6. The final markdown must be fully in English. Do not leave Chinese narrative, Chinese labels, Chinese disclaimers, or Chinese calls to action in the final output.
        7. Company names and stock codes may remain unchanged, but your analysis, source-title paraphrases, and framing must be English.
        8. Translate the meaning of Chinese source headlines into English when you reference them in the report.
        9. Only output final markdown.

        [Format]
        - Title: # 🌅 A-Stock Pre-Market Alert Radar | {reportDate}
        - Opening paragraph: Over the last 24 hours, the system completed notice de-noising, event clustering, macro-theme aggregation, and risk routing. These are the main themes, resonance picks, opportunities, and risks worth watching before the open:
        - Macro theme item:
          > 1. Theme Name | Event Type
          > 🧭 Direction: <font color="success/info/warning/comment">[Bullish/Bearish/Neutral]</font>
          > 🎯 Theme Strength: <font color="warning/info/success/comment">score points (tier, associated with N mapped stocks)</font>
          > 🧠 Interpretation: [policy/industry impact] + [how capital may trade it]
        - Resonance item:
          > 1. Company (Code) | Macro Theme
          > 🔗 Resonance Score: <font color="warning/info/success/comment">score points (Strong/High/Mild Resonance)</font>
          > 🏷️ Positioning: <font color="warning/info/comment">[Leading Core/High-Beta Follower/Watchlist]</font>
          > 🧠 Resonance Logic: [notice catalyst] + [why the macro theme amplifies it]
        - Opportunity item:
          > 1. Company (Code) | 🇨🇳 Stock
          > 📈 Event View: <font color="warning/info/comment">[Strong Bullish/Cautiously Bullish/Neutral]</font>
          > 🏷️ Positioning: <font color="warning/info/comment">[Leading Core/High-Beta Follower/Watchlist]</font>
          > 🎯 Event Score: <font color="warning/info/success/comment">score points (tier, X event clusters / Y supporting notices)</font>
          > 🧠 Core Setup: [business/financial impact] + [how capital may trade it]
        - Risk item:
          > 1. Company (Code) | 🇨🇳 Stock
          > ⚠️ Event View: <font color="warning/comment">[Bearish Alert/Neutral]</font>
          > 🎯 Event Score: <font color="warning/info/success/comment">score points (tier, X event clusters / Y supporting notices)</font>
          > 🧠 Risk Focus: [where the risk sits] + [how the market is likely to price or avoid it]
        - Use only these score tiers:
          - >=110: Main-Theme Tier
          - 80-109: High Priority
          - 60-79: Marginal Catalyst
        - Use only these position labels:
          - Leading Core
          - High-Beta Follower
          - Watchlist

        [Generate the report]
        """;

    private static final String A_EVENING_REPORT_PROMPT_EN = """
        [Role]
        You are a senior A-stock post-close strategist. Your job is to explain why the tape moved the way it did today, not to predict tomorrow.

        [Task]
        Use the A-stock intraday event data below to produce a post-close markdown report in English.

        [Input]
        Report date: {reportDate}
        Window: intraday 09:00-15:00
        Source: Eastmoney notice feed (noise filtered, event-clustered, rule-scored)

        Market state snapshot:
        {marketContext}

        Macro theme data:
        {macroThemeData}

        Resonance data:
        {resonanceData}

        Stock event data:
        {stockData}

        [Rules]
        1. Read the market snapshot first.
           - Defensive: emphasize risk propagation and defensive signals.
           - Risk-On: identify leaders versus followers.
           - Overheat: explicitly warn about crowding, failed limit-ups, and laggards dropping out.
        2. Explain today's price-action logic. Do not merely restate notice headlines.
        3. Output sections in this exact order:
           - ## Macro Themes
           - ## Resonance Picks
           - ## Opportunity Board
           - ## Risk Board
        4. If a section has no valid candidates, use the exact English empty-state text below:
           - <font color="comment">No macro theme met the threshold</font>
           - <font color="comment">No notice-theme resonance pick met the threshold</font>
           - <font color="comment">No opportunity event met the threshold</font>
           - <font color="comment">No high-priority risk event was detected</font>
        5. The final markdown must be fully in English. Do not leave Chinese narrative, Chinese labels, Chinese disclaimers, or Chinese calls to action in the final output.
        6. Company names and stock codes may remain unchanged, but your analysis, source-title paraphrases, and framing must be English.
        7. Translate the meaning of Chinese source headlines into English when you reference them in the report.
        8. Only output final markdown.

        [Format]
        - Title: # 🌆 A-Stock Post-Close Decode | {reportDate}
        - Opening paragraph: The A-stock market has closed. The system reviewed intraday notices from 09:00 to 15:00 and overlaid macro themes to split the tape into opportunity, risk, and resonance tracks:
        - Macro theme item:
          > 1. Theme Name | Event Type
          > 🧭 Direction: <font color="success/info/warning/comment">[Bullish/Bearish/Neutral]</font>
          > 🎯 Theme Strength: <font color="warning/info/success/comment">score points (tier, associated with N mapped stocks)</font>
          > 🧠 Interpretation: [how the theme affected today's tape]
        - Resonance item:
          > 1. Company (Code) | Macro Theme
          > 🔗 Resonance Score: <font color="warning/info/success/comment">score points (Strong/High/Mild Resonance)</font>
          > 🏷️ Positioning: <font color="warning/info/comment">[Leading Core/High-Beta Follower/Watchlist]</font>
          > 🧠 Resonance Decode: [how notice + macro theme reinforced today's move]
        - Opportunity item:
          > 1. Company (Code) | 🇨🇳 Stock
          > 🏷️ Positioning: <font color="warning/info/comment">[Leading Core/High-Beta Follower/Watchlist]</font>
          > 🔥 Session Heat: <font color="warning/info/success/comment">tier (Event score X points, Y event clusters / Z supporting notices)</font>
          > 🧠 Price Action Decode: [why the name strengthened or held up today]
        - Risk item:
          > 1. Company (Code) | 🇨🇳 Stock
          > 📉 Session Heat: <font color="warning/info/success/comment">tier (Event score X points, Y event clusters / Z supporting notices)</font>
          > 🧠 Risk Focus: [why the market priced the risk the way it did today]
        - Use only these tiers:
          - >=110: Main-Theme Tier
          - 80-109: High Priority
          - 60-79: Marginal Catalyst

        [Generate the report]
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
    @Override
    public String summarizeUSStocks(List<USStockRss> stockList) {
        if (stockList == null || stockList.isEmpty()) {
            return pushLanguageService.text("过去24小时内暂无美股异动数据。", "No unusual US stock activity was detected in the last 24 hours.");
        }

        String stockData = formatUSStockData(stockList);
        String prompt = withLanguageInstruction(US_STOCK_SUMMARY_PROMPT.replace("{stockData}", stockData));

        try {
            log.info("开始调用AI总结美股数据，共 {} 只股票", stockList.size());
            String summary = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            log.info("AI总结美股数据完成");
            return summary;
        } catch (Exception e) {
            log.error("AI总结美股数据失败: {}", e.getMessage(), e);
            return generateFallbackSummary(stockList, pushLanguageService.text("美股", "US stocks"));
        }
    }

    @Override
    public String summarizeAStocks(List<AStockRss> stockList) {
        if (stockList == null || stockList.isEmpty()) {
            return pushLanguageService.text("过去24小时内暂无A股异动数据。", "No unusual A-stock activity was detected in the last 24 hours.");
        }

        String stockData = formatAStockData(stockList);
        String prompt = withLanguageInstruction(A_STOCK_SUMMARY_PROMPT.replace("{stockData}", stockData));

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
        String prompt = withLanguageInstruction(US_MORNING_REPORT_PROMPT
                .replace("{reportDate}", reportDate)
                .replace("{stockData}", stockData));

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
        AStockReportClassifier.Sections sections = aStockReportClassifier.split(stockAlertList, A_STOCK_SECTION_LIMIT);
        AReportFusionContext context = new AReportFusionContext();
        context.setOpportunityAlerts(sections.opportunities());
        context.setRiskAlerts(sections.risks());
        return generateAMorningReportMarkdown(context, reportDate);
    }

    @Override
    public String generateAMorningReportMarkdown(AReportFusionContext reportContext, String reportDate) {
        if (reportContext == null || reportContext.isEmpty()) {
            return buildNoDataMarkdown(reportDate, pushLanguageService.text("A股", "A-stocks"));
        }

        String marketContext = formatMarketContext(reportContext.getMarketSnapshot());
        String stockData = formatAStockAlertData(
                reportContext.getOpportunityAlerts(),
                reportContext.getRiskAlerts(),
                reportContext.getOpportunityInsights()
        );
        String macroThemeData = formatMacroThemeData(reportContext.getMacroThemes());
        String resonanceData = formatResonanceData(reportContext.getResonanceCandidates(), reportContext.getOpportunityInsights());
        String prompt = withLanguageInstruction(resolveAMorningReportPrompt()
                .replace("{reportDate}", reportDate)
                .replace("{marketContext}", marketContext)
                .replace("{macroThemeData}", macroThemeData)
                .replace("{resonanceData}", resonanceData)
                .replace("{stockData}", stockData));

        try {
            log.info("开始生成A股盘前早报 Markdown，日期: {}，机会={}，风险={}，宏观主题={}，共振={}",
                    reportDate,
                    sizeOf(reportContext.getOpportunityAlerts()),
                    sizeOf(reportContext.getRiskAlerts()),
                    sizeOf(reportContext.getMacroThemes()),
                    sizeOf(reportContext.getResonanceCandidates()));
            String markdown = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            if (pushLanguageService.isEnglish() && containsChineseCharacters(markdown)) {
                log.warn("A股盘前早报英文模式仍包含中文，使用更严格的英文模板重试一次");
                markdown = chatClient.prompt(new Prompt(buildStrictEnglishRetryPrompt(prompt, reportDate, true)))
                        .call()
                        .content();
            }
            log.info("A股盘前早报 Markdown 生成完成");
            return ensureAStockMarkdown(markdown, reportContext, reportDate, true);
        } catch (Exception e) {
            log.error("生成A股盘前早报 Markdown 失败: {}", e.getMessage(), e);
            return generateAMorningFallbackMarkdown(reportContext, reportDate);
        }
    }

    @Override
    public String generateAEveningReportMarkdown(List<StockAlertDTO<AStockRss>> stockAlertList, String reportDate) {
        AStockReportClassifier.Sections sections = aStockReportClassifier.split(stockAlertList, A_STOCK_SECTION_LIMIT);
        AReportFusionContext context = new AReportFusionContext();
        context.setOpportunityAlerts(sections.opportunities());
        context.setRiskAlerts(sections.risks());
        return generateAEveningReportMarkdown(context, reportDate);
    }

    @Override
    public String generateAEveningReportMarkdown(AReportFusionContext reportContext, String reportDate) {
        if (reportContext == null || reportContext.isEmpty()) {
            return buildAStockNoDataEveningMarkdown(reportDate);
        }

        String marketContext = formatMarketContext(reportContext.getMarketSnapshot());
        String stockData = formatAStockAlertData(
                reportContext.getOpportunityAlerts(),
                reportContext.getRiskAlerts(),
                reportContext.getOpportunityInsights()
        );
        String macroThemeData = formatMacroThemeData(reportContext.getMacroThemes());
        String resonanceData = formatResonanceData(reportContext.getResonanceCandidates(), reportContext.getOpportunityInsights());
        String prompt = withLanguageInstruction(resolveAEveningReportPrompt()
                .replace("{reportDate}", reportDate)
                .replace("{marketContext}", marketContext)
                .replace("{macroThemeData}", macroThemeData)
                .replace("{resonanceData}", resonanceData)
                .replace("{stockData}", stockData));

        try {
            log.info("开始生成A股盘后复盘 Markdown，日期: {}，机会={}，风险={}，宏观主题={}，共振={}",
                    reportDate,
                    sizeOf(reportContext.getOpportunityAlerts()),
                    sizeOf(reportContext.getRiskAlerts()),
                    sizeOf(reportContext.getMacroThemes()),
                    sizeOf(reportContext.getResonanceCandidates()));
            String markdown = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            if (pushLanguageService.isEnglish() && containsChineseCharacters(markdown)) {
                log.warn("A股盘后复盘英文模式仍包含中文，使用更严格的英文模板重试一次");
                markdown = chatClient.prompt(new Prompt(buildStrictEnglishRetryPrompt(prompt, reportDate, false)))
                        .call()
                        .content();
            }
            log.info("A股盘后复盘 Markdown 生成完成");
            return ensureAStockMarkdown(markdown, reportContext, reportDate, false);
        } catch (Exception e) {
            log.error("生成A股盘后复盘 Markdown 失败: {}", e.getMessage(), e);
            return generateEveningFallbackMarkdown(reportContext, reportDate);
        }
    }

    @Override
    public String generateUSEveningReportMarkdown(List<StockAlertDTO<USStockRss>> stockAlertList, String reportDate) {
        if (stockAlertList == null || stockAlertList.isEmpty()) {
            return buildNoDataMarkdown(reportDate, pushLanguageService.text("美股", "US stocks"));
        }

        String stockData = formatUSStockAlertData(stockAlertList);
        String prompt = withLanguageInstruction(US_EVENING_REPORT_PROMPT
                .replace("{reportDate}", reportDate)
                .replace("{stockData}", stockData));

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
        String prompt = withLanguageInstruction(US_OVERNIGHT_RECAP_PROMPT
                .replace("{reportDate}", reportDate)
                .replace("{stockData}", stockData));

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
            sb.append("   ").append(pushLanguageService.text("标题(英)", "title_en")).append(": ").append(stock.getTitle()).append("\n");
            sb.append("   ").append(pushLanguageService.text("标题(中)", "title_zh")).append(": ").append(stock.getTitleZh() != null ? stock.getTitleZh() : "N/A").append("\n");
            sb.append("   ").append(pushLanguageService.text("标签", "tags")).append(": ").append(stock.getTags() != null ? stock.getTags() : "N/A").append("\n");
            sb.append("   ").append(pushLanguageService.text("时间", "time")).append(": ").append(stock.getPubDateBj() != null ? stock.getPubDateBj().format(DATE_FORMATTER) : "N/A").append("\n");
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
            sb.append("signal_side: ").append(pushLanguageService.signalSideLabel(stock.getSignalSide() != null ? stock.getSignalSide() : pushLanguageService.text("中性", "Neutral"))).append("\n");
            sb.append("event_type: ").append(pushLanguageService.eventType(stock.getEventType() != null ? stock.getEventType() : pushLanguageService.text("常规事项", "Routine"))).append("\n");
            sb.append("latest_notice_title: ").append(stock.getTitle() != null ? stock.getTitle() : "N/A").append("\n");
            sb.append("tags: ").append(stock.getTag() != null ? stock.getTag() : "N/A").append("\n");
            sb.append("analysis_hint: ").append(stock.getAnalysisHint() != null ? stock.getAnalysisHint() : pushLanguageService.text("请优先判断是否存在实质性预期差", "Please judge whether the notice creates a real expectation gap first")).append("\n");
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
            sb.append("   ").append(pushLanguageService.text("名称", "name")).append(": ").append(stock.getStockCode()).append("\n");
            sb.append("   ").append(pushLanguageService.text("异动频次", "mention_frequency")).append(": ").append(dto.getFrequency()).append(pushLanguageService.text(" 次", "")).append("\n");
            sb.append("   ").append(pushLanguageService.text("活跃度", "activity_level")).append(": ").append(pushLanguageService.activityLevel(dto.getFrequency())).append("\n");
            sb.append("   ").append(pushLanguageService.text("颜色标签", "color_tag")).append(": ").append(dto.getColorTag()).append("\n");
            sb.append("   ").append(pushLanguageService.text("最新标题(英)", "latest_title_en")).append(": ").append(stock.getTitle() != null ? stock.getTitle() : "N/A").append("\n");
            sb.append("   ").append(pushLanguageService.text("最新标题(中)", "latest_title_zh")).append(": ").append(stock.getTitleZh() != null ? stock.getTitleZh() : "N/A").append("\n");
            sb.append("   ").append(pushLanguageService.text("所有相关标题(供提炼催化剂)", "related_titles_for_catalyst")).append(": ").append(stock.getTags() != null ? stock.getTags() : "N/A").append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 格式化A股异动数据（包含频次和所有相关标题）
     */
    private String formatAStockAlertData(List<StockAlertDTO<AStockRss>> opportunities,
                                         List<StockAlertDTO<AStockRss>> risks,
                                         List<AReportOpportunityInsight> opportunityInsights) {
        StringBuilder sb = new StringBuilder();
        Map<String, AReportOpportunityInsight> insightByCode = indexInsights(opportunityInsights);
        appendAStockEventCards(sb, "## OpportunityCandidates", opportunities, insightByCode, true);
        appendAStockEventCards(sb, "## RiskCandidates", risks, insightByCode, false);
        return sb.toString();
    }

    private void appendAStockEventCards(StringBuilder sb,
                                        String sectionTitle,
                                        List<StockAlertDTO<AStockRss>> alerts,
                                        Map<String, AReportOpportunityInsight> insightByCode,
                                        boolean opportunitySection) {
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
            sb.append("signal_level: ").append(pushLanguageService.signalLevel(dto.getSignalScore())).append("\n");
            sb.append("signal_side: ").append(pushLanguageService.signalSideLabel(dto.getSignalSide())).append("\n");
            sb.append("event_cluster_count: ").append(dto.getEventCount()).append("\n");
            sb.append("support_notice_count: ").append(dto.getFrequency()).append("\n");
            sb.append("event_type: ").append(pushLanguageService.eventType(stock.getEventType() != null ? stock.getEventType() : "N/A")).append("\n");
            sb.append("color_tag: ").append(dto.getSignalColorTag()).append("\n");
            sb.append("analysis_hint: ").append(stock.getAnalysisHint() != null ? stock.getAnalysisHint() : "N/A").append("\n");
            if (opportunitySection) {
                AReportOpportunityInsight insight = insightByCode.get(stock.getStockCode());
                if (insight != null) {
                    sb.append("position_label: ").append(pushLanguageService.positionLabel(insight.getPositionLabel())).append("\n");
                    sb.append("position_reason: ").append(pushLanguageService.translatePositionReason(insight.getPositionReason())).append("\n");
                    sb.append("trade_hint: ").append(pushLanguageService.translateTradeHint(insight.getTradeHint())).append("\n");
                    sb.append("conviction_score: ").append(insight.getConvictionScore()).append("\n");
                    sb.append("resonance_supported: ").append(insight.isResonanceSupported()).append("\n");
                }
            }
            sb.append("latest_notice_title: ").append(stock.getTitle() != null ? stock.getTitle() : "N/A").append("\n");
            sb.append("related_titles: ").append(stock.getRelatedTitles() != null ? stock.getRelatedTitles() : "N/A").append("\n");
            sb.append("cluster_highlights:\n").append(indentMultiline(stock.getClusterHighlights() != null ? stock.getClusterHighlights() : "N/A", "  - ")).append("\n");
            sb.append("\n");
        }
    }

    private Map<String, AReportOpportunityInsight> indexInsights(List<AReportOpportunityInsight> opportunityInsights) {
        if (opportunityInsights == null || opportunityInsights.isEmpty()) {
            return Map.of();
        }
        return opportunityInsights.stream()
                .filter(Objects::nonNull)
                .filter(insight -> isNotBlank(insight.getStockCode()))
                .collect(Collectors.toMap(
                        AReportOpportunityInsight::getStockCode,
                        insight -> insight,
                        (left, right) -> left.getConvictionScore() >= right.getConvictionScore() ? left : right,
                        LinkedHashMap::new
                ));
    }

    private String formatMarketContext(MarketSnapshot snapshot) {
        StringBuilder sb = new StringBuilder("## MarketContext\n");
        if (snapshot == null) {
            sb.append("market_state: ").append(pushLanguageService.marketStateLabel(MarketState.NEUTRAL)).append("\n")
                    .append("market_interpretation: ").append(resolveMarketInterpretation(null)).append("\n\n");
            return sb.toString();
        }
        sb.append("market_state: ")
                .append(snapshot.getMarketState() != null
                        ? pushLanguageService.marketStateLabel(snapshot.getMarketState())
                        : pushLanguageService.marketStateLabel(MarketState.NEUTRAL))
                .append("\n");
        sb.append("captured_at: ")
                .append(snapshot.getCapturedAt() != null ? snapshot.getCapturedAt().format(DATE_FORMATTER) : "N/A")
                .append("\n");
        sb.append("index_change: ").append(pushLanguageService.text("上证 ", "SSE "))
                .append(formatPct(snapshot.getShChangePct()))
                .append(" | ").append(pushLanguageService.text("深成指 ", "SZSE "))
                .append(formatPct(snapshot.getSzChangePct()))
                .append(" | ").append(pushLanguageService.text("创业板 ", "ChiNext "))
                .append(formatPct(snapshot.getCybChangePct()))
                .append("\n");
        sb.append("breadth: ").append(pushLanguageService.text("上涨 ", "Advancers ")).append(Math.max(0, snapshot.getUpCount()))
                .append(" | ").append(pushLanguageService.text("下跌 ", "Decliners ")).append(Math.max(0, snapshot.getDownCount()))
                .append(" | ").append(pushLanguageService.text("平盘 ", "Unchanged ")).append(Math.max(0, snapshot.getFlatCount()))
                .append("\n");
        sb.append("limit_status: ").append(pushLanguageService.text("涨停 ", "Limit-Up ")).append(Math.max(0, snapshot.getLimitUpCount()))
                .append(" | ").append(pushLanguageService.text("跌停 ", "Limit-Down ")).append(Math.max(0, snapshot.getLimitDownCount()))
                .append("\n");
        sb.append("market_interpretation: ").append(resolveMarketInterpretation(snapshot)).append("\n\n");
        return sb.toString();
    }

    private String formatMacroThemeData(List<MacroThemeEvent> macroThemes) {
        StringBuilder sb = new StringBuilder();
        sb.append("## MacroThemeCandidates\n");
        if (macroThemes == null || macroThemes.isEmpty()) {
            sb.append("- none\n\n");
            return sb.toString();
        }
        for (int i = 0; i < macroThemes.size(); i++) {
            MacroThemeEvent theme = macroThemes.get(i);
            sb.append("### ThemeCard ").append(i + 1).append("\n");
            sb.append("theme_name: ").append(theme.getThemeName()).append("\n");
            sb.append("event_type: ").append(pushLanguageService.eventType(theme.getEventType())).append("\n");
            sb.append("signal_side: ").append(pushLanguageService.signalSideLabel(theme.getSignalSide())).append("\n");
            sb.append("signal_score: ").append(theme.getSignalScore()).append("\n");
            sb.append("importance_level: ").append(theme.getImportanceLevel() != null ? theme.getImportanceLevel() : 0).append("\n");
            sb.append("mapped_stock_count: ").append(theme.getMappedStockCount() != null ? theme.getMappedStockCount() : 0).append("\n");
            sb.append("mapped_stocks: ").append(theme.getMappedStocks() != null ? theme.getMappedStocks() : "N/A").append("\n");
            sb.append("source_name: ").append(theme.getSourceName() != null ? theme.getSourceName() : "N/A").append("\n");
            sb.append("title: ").append(theme.getTitle() != null ? theme.getTitle() : "N/A").append("\n");
            sb.append("summary: ").append(theme.getSummary() != null ? theme.getSummary() : "N/A").append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatResonanceData(List<AReportResonanceCard> resonanceCards,
                                       List<AReportOpportunityInsight> opportunityInsights) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ResonanceCandidates\n");
        if (resonanceCards == null || resonanceCards.isEmpty()) {
            sb.append("- none\n\n");
            return sb.toString();
        }
        Map<String, AReportOpportunityInsight> insightByCode = indexInsights(opportunityInsights);
        for (int i = 0; i < resonanceCards.size(); i++) {
            AReportResonanceCard card = resonanceCards.get(i);
            sb.append("### ResonanceCard ").append(i + 1).append("\n");
            sb.append("stock_code: ").append(card.getStockCode()).append("\n");
            sb.append("stock_name: ").append(card.getStockName()).append("\n");
            sb.append("signal_side: ").append(pushLanguageService.signalSideLabel(card.getSignalSide())).append("\n");
            sb.append("fusion_score: ").append(card.getFusionScore()).append("\n");
            sb.append("fusion_level: ").append(pushLanguageService.fusionLevel(safeInt(card.getFusionScore()))).append("\n");
            sb.append("notice_signal_score: ").append(card.getNoticeSignalScore()).append("\n");
            sb.append("macro_signal_score: ").append(card.getMacroSignalScore()).append("\n");
            sb.append("event_cluster_count: ").append(card.getEventClusterCount()).append("\n");
            sb.append("support_notice_count: ").append(card.getSupportNoticeCount()).append("\n");
            sb.append("macro_theme_name: ").append(card.getMacroThemeName() != null ? card.getMacroThemeName() : "N/A").append("\n");
            sb.append("macro_event_type: ").append(pushLanguageService.eventType(card.getMacroEventType() != null ? card.getMacroEventType() : "N/A")).append("\n");
            sb.append("notice_event_type: ").append(pushLanguageService.eventType(card.getNoticeEventType() != null ? card.getNoticeEventType() : "N/A")).append("\n");
            AReportOpportunityInsight insight = insightByCode.get(card.getStockCode());
            if (insight != null) {
                sb.append("position_label: ").append(pushLanguageService.positionLabel(insight.getPositionLabel())).append("\n");
                sb.append("position_reason: ").append(pushLanguageService.translatePositionReason(insight.getPositionReason())).append("\n");
            }
            sb.append("relation_reason: ").append(card.getRelationReason() != null ? pushLanguageService.translateRelationReason(card.getRelationReason()) : "N/A").append("\n");
            sb.append("notice_title: ").append(card.getNoticeTitle() != null ? card.getNoticeTitle() : "N/A").append("\n");
            sb.append("macro_title: ").append(card.getMacroTitle() != null ? card.getMacroTitle() : "N/A").append("\n");
            sb.append("macro_summary: ").append(card.getMacroSummary() != null ? card.getMacroSummary() : "N/A").append("\n");
            sb.append("notice_analysis_hint: ").append(card.getNoticeAnalysisHint() != null ? card.getNoticeAnalysisHint() : "N/A").append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成美股降级总结（当AI调用失败时使用）
     */
    private String generateFallbackSummary(List<USStockRss> stockList, String marketType) {
        StringBuilder sb = new StringBuilder();
        sb.append(pushLanguageService.text("过去24小时内", "Top moves in the last 24 hours for "))
                .append(marketType)
                .append(pushLanguageService.text("异动TOP5：\n\n", ":\n\n"));
        for (int i = 0; i < stockList.size(); i++) {
            USStockRss stock = stockList.get(i);
            sb.append(i + 1).append(". **").append(stock.getStockCode()).append("**");
            sb.append(" - ").append(pushLanguageService.isEnglish()
                    ? defaultText(stock.getTitle(), stock.getTitleZh())
                    : defaultText(stock.getTitleZh(), stock.getTitle()));
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成A股降级总结（当AI调用失败时使用）
     */
    private String generateFallbackSummaryA(List<AStockRss> stockList) {
        StringBuilder sb = new StringBuilder();
        sb.append(pushLanguageService.text("过去24小时内A股核心事件TOP5：\n\n", "Top A-stock event cards in the last 24 hours:\n\n"));
        for (int i = 0; i < stockList.size(); i++) {
            AStockRss stock = stockList.get(i);
            sb.append(i + 1).append(". **").append(stock.getStockCode()).append("** (").append(stock.getStockName()).append(")");
            sb.append(" - ").append(stock.getTitle());
            sb.append("【").append(pushLanguageService.eventType(stock.getEventType() != null ? stock.getEventType() : stock.getTag())).append(" / ");
            sb.append(pushLanguageService.signalSideLabel(stock.getSignalSide() != null ? stock.getSignalSide() : pushLanguageService.text("中性", "Neutral"))).append(" / ");
            sb.append(stock.getSignalScore() != null ? stock.getSignalScore() : 0).append(pushLanguageService.text("分", "")).append("】");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建无数据时的 Markdown 消息
     */
    private String buildNoDataMarkdown(String reportDate, String market) {
        boolean usMarket = "美股".equals(market) || "US stocks".equals(market);
        String botName = "@" + (usMarket ? pushLanguageService.botNameForUS() : pushLanguageService.botNameForA());
        String title = usMarket
                ? pushLanguageService.text("# 🌅 AI 盘前异动雷达 | ", "# 🌅 AI Pre-Market Alert Radar | ")
                : pushLanguageService.text("# 🌅 AI 盘前异动雷达 | ", "# 🌅 A-Stock Pre-Market Alert Radar | ");
        String thresholdDesc = usMarket
                ? pushLanguageService.text("（当前阈值：24小时内同一标的异动 >= 10 次，宁缺毋滥）",
                "(Current threshold: at least 10 mentions for the same ticker in 24h; quality over quantity.)")
                : pushLanguageService.text("（当前阈值：事件评分 >= 60 分，且已过滤治理/理财/会务等噪音公告）",
                "(Current threshold: event score >= 60, with governance/wealth-management/meeting noise already filtered out.)");
        return title + reportDate + "\n\n"
                + pushLanguageService.text("过去 24 小时内，系统共扫描全网财经资讯源（已过滤行政噪音）。\n\n",
                "In the last 24 hours, the system scanned public financial information sources after filtering administrative noise.\n\n")
                + "<font color=\"warning\">"
                + pushLanguageService.text("⚠️ 暂无" + market + "异动数据",
                "⚠️ No notable " + market + " alerts were detected")
                + "</font>\n"
                + "<font color=\"comment\">" + thresholdDesc + "</font>\n\n"
                + pushLanguageService.text("💡 AI 深度查股：\n想看具体股票分析？请在群内直接发送：", "💡 AI Deep Dive:\nTo analyze a specific stock, send: ")
                + botName
                + pushLanguageService.text(" 分析 [股票代码]\n\n", " analyze [ticker]\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。",
                "⚠️ Disclaimer: This report is generated from public information and AI summarization for research discussion only. It is not investment advice.")
                + "</font>";
    }

    private String buildAStockNoDataEveningMarkdown(String reportDate) {
        return pushLanguageService.text("# 🌆 A股盘后情绪解码 | ", "# 🌆 A-Stock Post-Close Decode | ") + reportDate + "\n\n"
                + pushLanguageService.text("今日 A 股已收盘。系统回溯了日内（9:00-15:00）公告事件，并拆分出机会与风险两条主线。\n\n",
                "The A-stock market has closed. The system reviewed intraday notices from 09:00 to 15:00 and separated them into opportunity and risk tracks.\n\n")
                + "<font color=\"warning\">"
                + pushLanguageService.text("⚠️ 暂无 🇨🇳 A股需要解码的盘后事件", "⚠️ No post-close A-stock events met the decoding threshold")
                + "</font>\n"
                + "<font color=\"comment\">"
                + pushLanguageService.text("（当前阈值：事件评分 >= 60 分，且已过滤治理/理财/会务等噪音公告）",
                "(Current threshold: event score >= 60, with governance/wealth-management/meeting noise already filtered out.)")
                + "</font>\n\n"
                + pushLanguageService.text("💡 持仓深度体检：\n今天的行情让你看不懂？想查查你手里被套的股票今天有没有出什么暗雷？\n👉 请在群内直接发送：@",
                "💡 Position Check:\nIf today's tape was confusing, ask whether a hidden risk hit one of your holdings:\n👉 Send @")
                + pushLanguageService.botNameForA()
                + pushLanguageService.text(" 分析 [你的股票代码]\n\n", " analyze [your ticker]\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于盘后逻辑梳理，绝不构成任何投资或交易建议。",
                "⚠️ Disclaimer: This recap is generated from public news and AI analysis for post-close research only. It is not investment advice.")
                + "</font>";
    }

    /**
     * 计算激增比例描述（降级方案使用）
     */
    private String getSurgeDescription(int frequency) {
        return pushLanguageService.surgeDescription(frequency);
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

    private String resolveAMorningReportPrompt() {
        return pushLanguageService.isEnglish() ? A_MORNING_REPORT_PROMPT_EN : A_MORNING_REPORT_PROMPT;
    }

    private String resolveAEveningReportPrompt() {
        return pushLanguageService.isEnglish() ? A_EVENING_REPORT_PROMPT_EN : A_EVENING_REPORT_PROMPT;
    }

    private String buildStrictEnglishRetryPrompt(String originalPrompt, String reportDate, boolean morning) {
        String title = morning
                ? "# 🌅 A-Stock Pre-Market Alert Radar | " + reportDate
                : "# 🌆 A-Stock Post-Close Decode | " + reportDate;
        return """
                [Retry Requirement]
                The previous response was invalid because it still contained Chinese.
                Regenerate the entire markdown report in English only.
                Do not use Chinese in headings, labels, explanations, source-title paraphrases, empty states, calls to action, or disclaimers.
                Company names and stock codes may remain unchanged.
                The title must be exactly: %s

                %s
                """.formatted(title, originalPrompt);
    }

    private boolean containsChineseCharacters(String text) {
        return text != null && HAN_PATTERN.matcher(text).find();
    }

    private String ensureAStockMarkdown(String markdown,
                                        AReportFusionContext reportContext,
                                        String reportDate,
                                        boolean morning) {
        String cleaned = sanitizeModelOutput(markdown);
        boolean hasExpectedSections = pushLanguageService.isEnglish()
                ? cleaned.contains("## Macro Themes")
                && cleaned.contains("## Resonance Picks")
                && cleaned.contains("## Opportunity Board")
                && cleaned.contains("## Risk Board")
                : cleaned.contains("## 宏观主线")
                && cleaned.contains("## 共振标的")
                && cleaned.contains("## 机会榜")
                && cleaned.contains("## 风险榜");
        if (cleaned.isBlank()
                || !cleaned.startsWith("#")
                || !hasExpectedSections) {
            log.warn("A股 Markdown 输出不符合预期，使用降级模板。内容: {}", cleaned);
            String fallback = morning
                    ? generateAMorningFallbackMarkdown(reportContext, reportDate)
                    : generateEveningFallbackMarkdown(reportContext, reportDate);
            return injectAStockScoreNote(fallback, morning);
        }
        return injectAStockScoreNote(normalizeAStockMarkdown(cleaned, reportDate, morning), morning);
    }

    private String normalizeAStockMarkdown(String markdown, String reportDate, boolean morning) {
        if (!pushLanguageService.isEnglish() || markdown == null || markdown.isBlank()) {
            return markdown;
        }
        String normalized = markdown.replace("\r\n", "\n");
        normalized = normalizeAStockLead(normalized, reportDate, morning);
        return normalizeAStockEnglishLabels(normalized);
    }

    private String normalizeAStockLead(String markdown, String reportDate, boolean morning) {
        String[] lines = markdown.split("\n", -1);
        int firstSectionIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("## ")) {
                firstSectionIndex = i;
                break;
            }
        }
        if (firstSectionIndex < 0) {
            return markdown;
        }

        List<String> rebuilt = new ArrayList<>();
        rebuilt.add(morning
                ? "# 🌅 A-Stock Pre-Market Alert Radar | " + reportDate
                : "# 🌆 A-Stock Post-Close Decode | " + reportDate);
        rebuilt.add("");
        rebuilt.add(morning
                ? "Over the last 24 hours, the system completed notice de-noising, event clustering, macro-theme aggregation, and risk routing. These are the main themes, resonance picks, opportunities, and risks worth watching before the open:"
                : "The A-stock market has closed. The system reviewed intraday notices from 09:00 to 15:00 and overlaid macro themes to split the tape into opportunity, risk, and resonance tracks:");
        rebuilt.add("");
        for (int i = firstSectionIndex; i < lines.length; i++) {
            rebuilt.add(lines[i]);
        }
        return String.join("\n", rebuilt).replaceAll("\n{3,}", "\n\n").trim();
    }

    private String normalizeAStockEnglishLabels(String markdown) {
        String normalized = markdown;
        normalized = normalized.replace("## 宏观主线", "## Macro Themes");
        normalized = normalized.replace("## 共振标的", "## Resonance Picks");
        normalized = normalized.replace("## 机会榜", "## Opportunity Board");
        normalized = normalized.replace("## 风险榜", "## Risk Board");
        normalized = normalized.replace("🇨🇳 A-Shares", "🇨🇳 Stock");
        normalized = normalized.replace("🇨🇳 A股", "🇨🇳 Stock");
        normalized = normalized.replace("A股分析专家", "Stock expert");
        normalized = normalized.replace("A-Share Analyst", "Stock expert");
        normalized = normalized.replace("A-Share", "A-Stock");
        normalized = normalized.replace("A-share", "A-stock");
        normalized = normalized.replace("A-shares", "A-stocks");
        normalized = normalized.replace("> 🧭 主线方向：", "> 🧭 Direction: ");
        normalized = normalized.replace("> 🧭 方向判断：", "> 🧭 Direction: ");
        normalized = normalized.replace("> 🎯 主题强度：", "> 🎯 Theme Strength: ");
        normalized = normalized.replace("> 🧠 主线解读：", "> 🧠 Interpretation: ");
        normalized = normalized.replace("> 🧠 盘面影响解码：", "> 🧠 Interpretation: ");
        normalized = normalized.replace("> 🧠 盘面影响：", "> 🧠 Interpretation: ");
        normalized = normalized.replace("> 🔗 共振强度：", "> 🔗 Resonance Score: ");
        normalized = normalized.replace("> 🧠 共振逻辑：", "> 🧠 Resonance Logic: ");
        normalized = normalized.replace("> 🧠 共振逻辑解码：", "> 🧠 Resonance Decode: ");
        normalized = normalized.replace("> 🏷️ 身位判定：", "> 🏷️ Positioning: ");
        normalized = normalized.replace("> 📈 事件判断：", "> 📈 Event View: ");
        normalized = normalized.replace("> ⚠️ 事件判断：", "> ⚠️ Event View: ");
        normalized = normalized.replace("> 🎯 事件评分：", "> 🎯 Event Score: ");
        normalized = normalized.replace("> 🧠 核心预期差：", "> 🧠 Core Setup: ");
        normalized = normalized.replace("> 🧠 风险焦点：", "> 🧠 Risk Focus: ");
        normalized = normalized.replace("> 🧠 涨跌逻辑解码：", "> 🧠 Price Action Decode: ");
        normalized = normalized.replace("💡 AI 深度查股：", "💡 AI Deep Dive:");
        normalized = normalized.replace("💡 持仓深度体检：", "💡 Position Check:");
        normalized = normalized.replace("想看上述股票的具体公告源？或者查询你的自选股？", "Want the underlying notice sources or a custom watchlist check?");
        normalized = normalized.replace("今天的行情让你看不懂？想查查你手里被套的股票今天有没有出什么暗雷？", "If today's tape was confusing, ask whether a hidden risk hit one of your holdings.");
        normalized = normalized.replace("👉 请在群内直接发送：@", "👉 Send @");
        normalized = normalized.replace(" 分析 [股票代码]", " analyze [ticker]");
        normalized = normalized.replace(" 分析 [你的股票代码]", " analyze [your ticker]");
        normalized = normalized.replace("[AI分析中...]", "[AI reviewing...]");
        normalized = normalized.replace("【AI分析中...】", "【AI reviewing...】");
        normalized = normalized.replace("【利多】", "【Bullish】");
        normalized = normalized.replace("【利空】", "【Bearish】");
        normalized = normalized.replace("【中性】", "【Neutral】");
        normalized = normalized.replace("[利多]", "[Bullish]");
        normalized = normalized.replace("[利空]", "[Bearish]");
        normalized = normalized.replace("[中性]", "[Neutral]");
        normalized = normalized.replace("强烈看多", "Strong Bullish");
        normalized = normalized.replace("谨慎看多", "Cautiously Bullish");
        normalized = normalized.replace("中性观望", "Neutral");
        normalized = normalized.replace("利空预警", "Bearish Alert");
        normalized = normalized.replace("主线级", "Main-Theme Tier");
        normalized = normalized.replace("高优先级", "High Priority");
        normalized = normalized.replace("边际催化", "Marginal Catalyst");
        normalized = normalized.replace("强共振", "Strong Resonance");
        normalized = normalized.replace("高共振", "High Resonance");
        normalized = normalized.replace("弱共振", "Mild Resonance");
        normalized = normalized.replace("领军核心", "Leading Core");
        normalized = normalized.replace("高弹性跟风", "High-Beta Follower");
        normalized = normalized.replace("观察名单", "Watchlist");
        normalized = normalized.replace("暂无达到阈值的宏观主线", "No macro theme met the threshold");
        normalized = normalized.replace("暂无公告与主题共振标的", "No notice-theme resonance pick met the threshold");
        normalized = normalized.replace("暂无达到阈值的机会事件", "No opportunity event met the threshold");
        normalized = normalized.replace("暂无高优先级风险事件", "No high-priority risk event was detected");
        normalized = normalized.replace("，", ", ");
        normalized = normalized.replaceAll("(\\d+) 分(?=\\s*[,(])", "$1 points");
        normalized = normalized.replace("(事件评分 ", "(Event score ");
        normalized = normalized.replaceAll("关联\\s*(\\d+)\\s*只映射标的", "associated with $1 mapped stocks");
        normalized = normalized.replaceAll("(\\d+) 个事件簇 / (\\d+) 条支撑公告", "$1 event clusters / $2 supporting notices");
        normalized = normalized.replaceAll("监控到\\s*(\\d+)\\s*次高频异动", "$1 high-frequency mentions monitored");
        normalized = normalized.replaceAll("(?m)^> ([🔥📈📉⚖️]) 当日热度：", "> $1 Session Heat: ");
        normalized = normalized.replace("⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。",
                "⚠️ Disclaimer: This report is generated from public information and AI summarization for research discussion only. It is not investment advice.");
        normalized = normalized.replace("⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于盘后逻辑梳理，绝不构成任何投资或交易建议。",
                "⚠️ Disclaimer: This recap is generated from public news and AI analysis for post-close research only. It is not investment advice.");
        return normalized;
    }

    private String injectAStockScoreNote(String markdown, boolean morning) {
        if (markdown == null || markdown.isBlank()
                || markdown.contains("口径说明：")
                || markdown.contains("Methodology:")) {
            return markdown;
        }

        String note = morning ? aMorningScoreNote() : aEveningScoreNote();
        int sectionIndex = markdown.indexOf(pushLanguageService.isEnglish() ? "## Macro Themes" : "## 宏观主线");
        if (sectionIndex < 0) {
            return markdown + "\n\n" + note;
        }

        String prefix = markdown.substring(0, sectionIndex).trim();
        String suffix = markdown.substring(sectionIndex);
        return prefix + "\n\n" + note + "\n\n" + suffix;
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
        boolean usMarket = market.equals("美股") || market.equals("US stocks");
        String reportTitle = usMarket
                ? pushLanguageService.text("# 🌅 AI 盘前异动雷达 | ", "# 🌅 AI Pre-Market Alert Radar | ")
                : pushLanguageService.text("# 🌅 A股盘前异动雷达 | ", "# 🌅 A-Stock Pre-Market Alert Radar | ");
        sb.append(reportTitle).append(reportDate).append("\n\n");
        sb.append(pushLanguageService.text(
                "过去 24 小时内，系统共扫描全网财经资讯源（已过滤行政噪音），以下标的爆发密集异动，请注意盘前风险与机会：\n\n",
                "In the last 24 hours, the system scanned public financial sources after filtering administrative noise. The following names showed dense activity worth checking before the open:\n\n"));

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
                stockCode = pushLanguageService.text("未知", "Unknown");
            }

            String displayName = stockName != null ? stockName : stockCode;
            
            sb.append("> ").append(i + 1).append(". ").append(displayName).append(" (").append(stockCode).append(") | ").append(flag).append(" ").append(market).append("\n");
            if (dto.getStock() instanceof AStockRss) {
                sb.append("> ").append(pushLanguageService.text("📈 事件判断", "📈 Event View")).append("：<font color=\"comment\">【")
                        .append(pushLanguageService.text("AI分析中...", "AI reviewing...")).append("】</font>\n");
                sb.append("> ").append(pushLanguageService.text("🎯 事件评分", "🎯 Event Score")).append("：<font color=\"").append(dto.getSignalColorTag()).append("\">")
                        .append(dto.getSignalScore()).append(pushLanguageService.text(" 分 (", " ("))
                        .append(pushLanguageService.signalLevel(dto.getSignalScore())).append(pushLanguageService.text("，", ", "))
                        .append(dto.getEventCount()).append(pushLanguageService.text(" 个事件簇 / ", " event clusters / "))
                        .append(dto.getFrequency()).append(pushLanguageService.text(" 条支撑公告)", " supporting notices)"))
                        .append("</font>\n");
            } else {
                String surgeDesc = getSurgeDescription(dto.getFrequency());
                sb.append("> ").append(pushLanguageService.text("📈 情绪雷达", "📈 Sentiment Radar")).append("：<font color=\"comment\">【")
                        .append(pushLanguageService.text("AI分析中...", "AI reviewing...")).append("】</font>\n");
                sb.append("> ").append(pushLanguageService.text("📊 异动频次", "📊 Mention Frequency")).append("：<font color=\"").append(dto.getColorTag()).append("\">")
                        .append(dto.getFrequency()).append(pushLanguageService.text(" 次 (", " ("))
                        .append(pushLanguageService.activityLevel(dto.getFrequency())).append(", ").append(surgeDesc).append(")</font>\n");
            }
            sb.append("> ").append(pushLanguageService.text("🧠 核心催化剂", "🧠 Core Catalyst")).append("：")
                    .append(title != null ? title : pushLanguageService.text("暂无详细分析", "No detailed analysis yet"));
            if (tag != null && !tag.isEmpty()) {
                sb.append(pushLanguageService.text(" 相关标题：[", " Related titles: ["))
                        .append(tag.substring(0, Math.min(50, tag.length()))).append("...]");
            }
            sb.append("\n\n");
        }

        String botName = "@" + (usMarket ? pushLanguageService.botNameForUS() : pushLanguageService.botNameForA());
        sb.append(pushLanguageService.text("💡 AI 深度查股：\n想看上述股票的具体新闻源？或者查询你的自选股？\n👉 请在群内直接发送：",
                "💡 AI Deep Dive:\nWant the underlying news sources or a custom watchlist check?\n👉 Send: "))
                .append(botName)
                .append(pushLanguageService.text(" 分析 [股票代码]\n\n", " analyze [ticker]\n\n"));
        sb.append("<font color=\"comment\">")
                .append(pushLanguageService.text("⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。",
                        "⚠️ Disclaimer: This report is generated from public information and AI summarization for research discussion only. It is not investment advice."))
                .append("</font>");

        return sb.toString();
    }

    private String generateAMorningFallbackMarkdown(AReportFusionContext reportContext, String reportDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(pushLanguageService.text("# 🌅 A股盘前异动雷达 | ", "# 🌅 A-Stock Pre-Market Alert Radar | "))
                .append(reportDate).append("\n\n");
        sb.append(pushLanguageService.text(
                "过去 24 小时内，系统完成了公告去噪、事件聚类、宏观主题聚合与风险分流，以下标的是盘前最值得关注的主线、共振与风险：\n\n",
                "Over the last 24 hours, the system completed notice de-noising, event clustering, macro-theme aggregation, and risk routing. These are the main themes, resonance picks, opportunities, and risks worth watching before the open:\n\n"));
        appendMarketSnapshotBanner(sb, reportContext.getMarketSnapshot());
        appendMacroThemeSection(sb, reportContext.getMacroThemes());
        appendResonanceSection(sb, reportContext.getResonanceCandidates(), reportContext.getOpportunityInsights(), true);
        appendAMorningSection(sb, pushLanguageService.text("机会榜", "Opportunity Board"),
                pushLanguageService.text("暂无达到阈值的机会事件", "No opportunity event met the threshold"),
                reportContext.getOpportunityAlerts(), reportContext.getOpportunityInsights(), false);
        appendAMorningSection(sb, pushLanguageService.text("风险榜", "Risk Board"),
                pushLanguageService.text("暂无高优先级风险事件", "No high-priority risk event was detected"),
                reportContext.getRiskAlerts(), reportContext.getOpportunityInsights(), true);
        sb.append(pushLanguageService.text("💡 AI 深度查股：\n想看上述股票的具体公告源？或者查询你的自选股？\n👉 请在群内直接发送：@",
                "💡 AI Deep Dive:\nWant the underlying notice sources or a custom watchlist check?\n👉 Send @"))
                .append(pushLanguageService.botNameForA())
                .append(pushLanguageService.text(" 分析 [股票代码]\n\n", " analyze [ticker]\n\n"));
        sb.append("<font color=\"comment\">")
                .append(pushLanguageService.text("⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。",
                        "⚠️ Disclaimer: This report is generated from public information and AI summarization for research discussion only. It is not investment advice."))
                .append("</font>");
        return sb.toString();
    }

    private void appendAMorningSection(StringBuilder sb,
                                       String sectionTitle,
                                       String emptyText,
                                       List<StockAlertDTO<AStockRss>> alerts,
                                       List<AReportOpportunityInsight> opportunityInsights,
                                       boolean riskSection) {
        sb.append("## ").append(sectionTitle).append("\n\n");
        if (alerts.isEmpty()) {
            sb.append("<font color=\"comment\">").append(emptyText).append("</font>\n\n");
            return;
        }
        Map<String, AReportOpportunityInsight> insightByCode = indexInsights(opportunityInsights);
        for (int i = 0; i < alerts.size(); i++) {
            StockAlertDTO<AStockRss> dto = alerts.get(i);
            AStockRss stock = dto.getStock();
            String stockCode = stock.getStockCode();
            String displayName = stock.getStockName() != null ? stock.getStockName() : stockCode;
            String title = stock.getTitle() != null ? stock.getTitle() : "暂无详细分析";
            String tag = stock.getRelatedTitles() != null ? stock.getRelatedTitles() : stock.getTag();

            sb.append("> ").append(i + 1).append(". ").append(displayName).append(" (").append(stockCode).append(") | ")
                    .append(pushLanguageService.text("🇨🇳 A股", "🇨🇳 Stock")).append("\n");
            sb.append("> ").append(riskSection ? "⚠️" : "📈").append(" ")
                    .append(pushLanguageService.text("事件判断", "Event View")).append("：<font color=\"")
                    .append(dto.getSignalColorTag()).append("\">【")
                    .append(resolveFallbackSignalLabel(dto)).append("】</font>\n");
            if (!riskSection) {
                appendPositionLine(sb, insightByCode.get(stockCode));
            }
            sb.append("> ").append(pushLanguageService.text("🎯 事件评分", "🎯 Event Score")).append("：<font color=\"").append(dto.getSignalColorTag()).append("\">")
                    .append(dto.getSignalScore()).append(pushLanguageService.text(" 分 (", " ("))
                    .append(pushLanguageService.signalLevel(dto.getSignalScore())).append(pushLanguageService.text("，", ", "))
                    .append(dto.getEventCount()).append(pushLanguageService.text(" 个事件簇 / ", " event clusters / "))
                    .append(dto.getFrequency()).append(pushLanguageService.text(" 条支撑公告)", " supporting notices)")).append("</font>\n");
            sb.append("> ").append(riskSection
                    ? pushLanguageService.text("🧠 风险焦点：", "🧠 Risk Focus: ")
                    : pushLanguageService.text("🧠 核心预期差：", "🧠 Core Setup: "))
                    .append(title);
            if (tag != null && !tag.isEmpty()) {
                sb.append(pushLanguageService.text(" 相关标题：[", " Related titles: ["))
                        .append(tag.substring(0, Math.min(50, tag.length()))).append("...]");
            }
            sb.append("\n\n");
        }
    }

    private void appendMacroThemeSection(StringBuilder sb, List<MacroThemeEvent> macroThemes) {
        sb.append("## ").append(pushLanguageService.text("宏观主线", "Macro Themes")).append("\n\n");
        if (macroThemes == null || macroThemes.isEmpty()) {
            sb.append("<font color=\"comment\">")
                    .append(pushLanguageService.text("暂无达到阈值的宏观主线", "No macro theme met the threshold"))
                    .append("</font>\n\n");
            return;
        }
        for (int i = 0; i < Math.min(A_STOCK_SECTION_LIMIT, macroThemes.size()); i++) {
            MacroThemeEvent theme = macroThemes.get(i);
            String sideLabel = resolveMacroSideLabel(theme.getSignalSide());
            String colorTag = resolveMacroColorTag(theme.getSignalSide(), safeInt(theme.getSignalScore()));
            String level = resolveMacroSignalLevel(safeInt(theme.getSignalScore()));
            sb.append("> ").append(i + 1).append(". ")
                    .append(theme.getThemeName()).append(" | ")
                    .append(defaultText(pushLanguageService.eventType(theme.getEventType()), pushLanguageService.text("主题事件", "Theme Event"))).append("\n");
            sb.append("> ").append(pushLanguageService.text("🧭 主线方向", "🧭 Direction")).append("：<font color=\"").append(colorTag).append("\">【")
                    .append(sideLabel).append("】</font>\n");
            sb.append("> ").append(pushLanguageService.text("🎯 主题强度", "🎯 Theme Strength")).append("：<font color=\"").append(colorTag).append("\">")
                    .append(safeInt(theme.getSignalScore())).append(pushLanguageService.text(" 分 (", " ("))
                    .append(level).append(pushLanguageService.text("，关联 ", ", "))
                    .append(theme.getMappedStockCount() != null ? theme.getMappedStockCount() : 0)
                    .append(pushLanguageService.text(" 只映射标的)", " mapped stocks)")).append("</font>\n");
            sb.append("> ").append(pushLanguageService.text("🧠 主线解读：", "🧠 Interpretation: ")).append(defaultText(theme.getTitle(), pushLanguageService.text("暂无主题标题", "No theme title")));
            if (isNotBlank(theme.getMappedStocks())) {
                sb.append(pushLanguageService.text(" 关联标的[", " Mapped names ["))
                        .append(truncate(theme.getMappedStocks(), 60)).append("]");
            }
            sb.append("\n\n");
        }
    }

    private void appendResonanceSection(StringBuilder sb,
                                        List<AReportResonanceCard> resonanceCards,
                                        List<AReportOpportunityInsight> opportunityInsights,
                                        boolean morning) {
        sb.append("## ").append(pushLanguageService.text("共振标的", "Resonance Picks")).append("\n\n");
        if (resonanceCards == null || resonanceCards.isEmpty()) {
            sb.append("<font color=\"comment\">")
                    .append(pushLanguageService.text("暂无公告与主题共振标的", "No notice-theme resonance pick met the threshold"))
                    .append("</font>\n\n");
            return;
        }
        Map<String, AReportOpportunityInsight> insightByCode = indexInsights(opportunityInsights);
        for (int i = 0; i < Math.min(A_STOCK_SECTION_LIMIT, resonanceCards.size()); i++) {
            AReportResonanceCard card = resonanceCards.get(i);
            String stockCode = defaultText(card.getStockCode(), pushLanguageService.text("未知", "Unknown"));
            String stockName = defaultText(card.getStockName(), stockCode);
            sb.append("> ").append(i + 1).append(". ").append(stockName).append(" (").append(stockCode).append(") | ")
                    .append(defaultText(card.getMacroThemeName(), pushLanguageService.text("宏观主题", "Macro Theme"))).append("\n");
            sb.append("> ").append(pushLanguageService.text("🔗 共振强度", "🔗 Resonance Score")).append("：<font color=\"").append(card.getColorTag()).append("\">")
                    .append(card.getFusionScore()).append(pushLanguageService.text(" 分 (", " ("))
                    .append(pushLanguageService.fusionLevel(card.getFusionScore())).append(")</font>\n");
            appendPositionLine(sb, insightByCode.get(stockCode));
            sb.append("> ").append(morning
                    ? pushLanguageService.text("🧠 共振逻辑：", "🧠 Resonance Logic: ")
                    : pushLanguageService.text("🧠 共振逻辑解码：", "🧠 Resonance Decode: "))
                    .append(defaultText(card.getNoticeTitle(), pushLanguageService.text("暂无公告标题", "No notice title")));
            if (isNotBlank(card.getMacroTitle())) {
                sb.append(" + ").append(truncate(card.getMacroTitle(), 60));
            }
            sb.append("\n\n");
        }
    }

    private String resolveFallbackSignalLabel(StockAlertDTO<AStockRss> dto) {
        if ("利空".equals(dto.getSignalSide())) {
            return pushLanguageService.text("利空预警", "Bearish Alert");
        }
        if (dto.getSignalScore() >= 110) {
            return pushLanguageService.text("强烈看多", "Strong Bullish");
        }
        if (dto.getSignalScore() >= 80) {
            return pushLanguageService.text("谨慎看多", "Cautiously Bullish");
        }
        return pushLanguageService.text("中性观望", "Neutral");
    }

    /**
     * 构建美股早报（隔夜复盘）无数据 Markdown 消息
     */
    private String buildUSOvernightNoDataMarkdown(String reportDate) {
        return pushLanguageService.text("# 🌅 美股早报 | ", "# 🌅 US Overnight Recap | ") + reportDate + "\n\n"
                + pushLanguageService.text("昨夜美股已收盘。系统回溯了整夜（21:30-04:00）全网资讯发酵轨迹。\n\n",
                "The US market has closed. The system replayed the full overnight information flow from 21:30 to 04:00 Beijing time.\n\n")
                + "<font color=\"warning\">"
                + pushLanguageService.text("⚠️ 暂无 🇺🇸 美股需要解码的隔夜异动数据", "⚠️ No overnight US stock activity met the recap threshold")
                + "</font>\n"
                + "<font color=\"comment\">"
                + pushLanguageService.text("（当前阈值：24小时内同一标的异动 >= 10 次，宁缺毋滥）",
                "(Current threshold: at least 10 mentions for the same ticker in 24h; quality over quantity.)")
                + "</font>\n\n"
                + pushLanguageService.text("💡 隔夜行情复盘：\n昨夜美股的大涨大跌让你措手不及？想查查你关注的股票出了什么重磅消息？\n👉 请在群内直接发送：@",
                "💡 Overnight Replay:\nIf last night's move caught you off guard, ask what actually hit your watchlist:\n👉 Send @")
                + pushLanguageService.botNameForUS()
                + pushLanguageService.text(" 分析 [股票代码]\n\n", " analyze [ticker]\n\n")
                + "<font color=\"comment\">"
                + pushLanguageService.text("⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于隔夜逻辑梳理，绝不构成任何投资或交易建议。",
                "⚠️ Disclaimer: This recap is generated from public news and AI analysis for overnight research only. It is not investment advice.")
                + "</font>";
    }

    /**
     * 生成美股隔夜复盘降级 Markdown（当AI调用失败时使用）
     */
    private String generateUSOvernightFallbackMarkdown(List<StockAlertDTO<USStockRss>> stockAlertList, String reportDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(pushLanguageService.text("# 🌅 美股早报 | ", "# 🌅 US Overnight Recap | "))
                .append(reportDate).append("\n\n");
        sb.append(pushLanguageService.text(
                "昨夜美股已收盘。系统回溯了整夜（21:30-04:00）全网资讯发酵轨迹，以下标的是昨夜资金博弈的绝对核心：\n\n",
                "The US market has closed. The system replayed the overnight information flow from 21:30 to 04:00 Beijing time. These names drew the strongest capital attention:\n\n"));

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
                heatLevel = pushLanguageService.text("爆发", "Explosive");
            } else if (dto.getFrequency() >= 20) {
                heatIcon = dto.getColorTag().equals("warning") ? "📉" : "📈";
                heatLevel = pushLanguageService.text("高热", "High Heat");
            } else {
                heatIcon = "⚖️";
                heatLevel = pushLanguageService.text("活跃", "Active");
            }
            
            sb.append("> ").append(i + 1).append(". ").append(stockCode).append(" | ").append(pushLanguageService.text("🇺🇸 美股", "🇺🇸 US Stocks")).append("\n");
            sb.append("> ").append(heatIcon).append(" ").append(pushLanguageService.text("隔夜热度", "Overnight Heat")).append("：<font color=\"").append(dto.getColorTag()).append("\">")
                    .append(heatLevel).append(pushLanguageService.text(" (监控到 ", " ("))
                    .append(dto.getFrequency()).append(pushLanguageService.text(" 次高频异动)", " high-frequency mentions monitored)")).append("</font>\n");
            sb.append("> ").append(pushLanguageService.text("🧠 涨跌逻辑解码：", "🧠 Price Action Decode: "))
                    .append(pushLanguageService.text("[AI分析中...] ", "[AI reviewing...] "))
                    .append(title != null ? title : pushLanguageService.text("暂无详细分析", "No detailed analysis yet"));
            if (tag != null && !tag.isEmpty()) {
                sb.append(pushLanguageService.text(" 相关标题：[", " Related titles: ["))
                        .append(tag.substring(0, Math.min(50, tag.length()))).append("...]");
            }
            sb.append("\n\n");
        }

        sb.append(pushLanguageService.text("💡 隔夜行情复盘：\n昨夜美股的大涨大跌让你措手不及？想查查你关注的股票出了什么重磅消息？\n👉 请在群内直接发送：@",
                "💡 Overnight Replay:\nIf last night's move caught you off guard, ask what actually hit your watchlist:\n👉 Send @"))
                .append(pushLanguageService.botNameForUS())
                .append(pushLanguageService.text(" 分析 [股票代码]\n\n", " analyze [ticker]\n\n"));
        sb.append("<font color=\"comment\">")
                .append(pushLanguageService.text("⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于隔夜逻辑梳理，绝不构成任何投资或交易建议。",
                        "⚠️ Disclaimer: This recap is generated from public news and AI analysis for overnight research only. It is not investment advice."))
                .append("</font>");

        return sb.toString();
    }

    /**
     * 生成A股盘后复盘降级 Markdown（当AI调用失败时使用）
     */
    private String generateEveningFallbackMarkdown(AReportFusionContext reportContext, String reportDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(pushLanguageService.text("# 🌆 A股盘后情绪解码 | ", "# 🌆 A-Stock Post-Close Decode | "))
                .append(reportDate).append("\n\n");
        sb.append(pushLanguageService.text(
                "今日 A 股已收盘。系统回溯了日内（9:00-15:00）公告事件，并叠加宏观主题线索，拆分出机会、风险与共振三条主线：\n\n",
                "The A-stock market has closed. The system reviewed intraday notices from 09:00 to 15:00 and overlaid macro themes to split the tape into opportunity, risk, and resonance tracks:\n\n"));
        appendMarketSnapshotBanner(sb, reportContext.getMarketSnapshot());
        appendMacroThemeSection(sb, reportContext.getMacroThemes());
        appendResonanceSection(sb, reportContext.getResonanceCandidates(), reportContext.getOpportunityInsights(), false);
        appendAEveningSection(sb, pushLanguageService.text("机会榜", "Opportunity Board"),
                pushLanguageService.text("暂无达到阈值的机会事件", "No opportunity event met the threshold"),
                reportContext.getOpportunityAlerts(), reportContext.getOpportunityInsights());
        appendAEveningSection(sb, pushLanguageService.text("风险榜", "Risk Board"),
                pushLanguageService.text("暂无高优先级风险事件", "No high-priority risk event was detected"),
                reportContext.getRiskAlerts(), reportContext.getOpportunityInsights());

        sb.append(pushLanguageService.text("💡 持仓深度体检：\n今天的行情让你看不懂？想查查你手里被套的股票今天有没有出什么暗雷？\n👉 请在群内直接发送：@",
                "💡 Position Check:\nIf today's tape was confusing, ask whether a hidden risk hit one of your holdings:\n👉 Send @"))
                .append(pushLanguageService.botNameForA())
                .append(pushLanguageService.text(" 分析 [你的股票代码]\n\n", " analyze [your ticker]\n\n"));
        sb.append("<font color=\"comment\">")
                .append(pushLanguageService.text("⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于盘后逻辑梳理，绝不构成任何投资或交易建议。",
                        "⚠️ Disclaimer: This recap is generated from public news and AI analysis for post-close research only. It is not investment advice."))
                .append("</font>");

        return sb.toString();
    }

    private void appendAEveningSection(StringBuilder sb,
                                       String sectionTitle,
                                       String emptyText,
                                       List<StockAlertDTO<AStockRss>> alerts,
                                       List<AReportOpportunityInsight> opportunityInsights) {
        sb.append("## ").append(sectionTitle).append("\n\n");
        if (alerts.isEmpty()) {
            sb.append("<font color=\"comment\">").append(emptyText).append("</font>\n\n");
            return;
        }
        Map<String, AReportOpportunityInsight> insightByCode = indexInsights(opportunityInsights);

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
                heatLevel = pushLanguageService.text("主线级", "Main-Theme Tier");
            } else if (dto.getSignalScore() >= 80) {
                heatIcon = "利空".equals(dto.getSignalSide()) ? "📉" : "📈";
                heatLevel = pushLanguageService.text("高优先级", "High Priority");
            } else {
                heatIcon = "⚖️";
                heatLevel = pushLanguageService.text("边际催化", "Marginal Catalyst");
            }

            sb.append("> ").append(i + 1).append(". ").append(displayName).append(" (").append(stockCode).append(") | ")
                    .append(pushLanguageService.text("🇨🇳 A股", "🇨🇳 Stock")).append("\n");
            appendPositionLine(sb, insightByCode.get(stockCode));
            sb.append("> ").append(heatIcon).append(" ").append(pushLanguageService.text("当日热度", "Session Heat")).append("：<font color=\"").append(dto.getSignalColorTag()).append("\">")
                    .append(heatLevel).append(pushLanguageService.text(" (事件评分 ", " (Event score "))
                    .append(dto.getSignalScore()).append(pushLanguageService.text(" 分，", ", "))
                    .append(dto.getEventCount()).append(pushLanguageService.text(" 个事件簇 / ", " event clusters / "))
                    .append(dto.getFrequency()).append(pushLanguageService.text(" 条支撑公告)", " supporting notices)")).append("</font>\n");
            sb.append("> ").append(pushLanguageService.text("🧠 涨跌逻辑解码：", "🧠 Price Action Decode: "))
                    .append(pushLanguageService.text("[AI分析中...] ", "[AI reviewing...] ")).append(title);
            if (tag != null && !tag.isEmpty()) {
                sb.append(pushLanguageService.text(" 相关标题：[", " Related titles: ["))
                        .append(tag.substring(0, Math.min(50, tag.length()))).append("...]");
            }
            sb.append("\n\n");
        }
    }

    private String resolveMacroSignalLevel(int signalScore) {
        if (signalScore < 60 && pushLanguageService.isEnglish()) {
            return "Watch";
        }
        return signalScore < 60 ? "观察" : pushLanguageService.signalLevel(signalScore);
    }

    private String resolveMacroColorTag(String signalSide, int signalScore) {
        if ("利空".equals(signalSide)) {
            return "warning";
        }
        if (signalScore >= 110) {
            return "warning";
        }
        if (signalScore >= 80) {
            return "info";
        }
        if (signalScore >= 60) {
            return "success";
        }
        return "comment";
    }

    private String resolveMacroSideLabel(String signalSide) {
        return pushLanguageService.signalSideLabel(signalSide);
    }

    private void appendPositionLine(StringBuilder sb, AReportOpportunityInsight insight) {
        if (sb == null || insight == null) {
            return;
        }
        sb.append("> ").append(pushLanguageService.text("🏷️ 身位判定", "🏷️ Positioning")).append("：<font color=\"").append(insight.getColorTag()).append("\">【")
                .append(defaultText(pushLanguageService.positionLabel(insight.getPositionLabel()), pushLanguageService.text("观察名单", "Watchlist"))).append("】</font>");
        if (isNotBlank(insight.getPositionReason())) {
            sb.append(" ").append(truncate(pushLanguageService.translatePositionReason(insight.getPositionReason()), 64));
        }
        sb.append("\n");
    }

    private void appendMarketSnapshotBanner(StringBuilder sb, MarketSnapshot snapshot) {
        if (sb == null || snapshot == null) {
            return;
        }
        sb.append("<font color=\"comment\">").append(pushLanguageService.text("当前市场状态：", "Current market regime: "))
                .append(snapshot.getMarketState() != null ? pushLanguageService.marketStateLabel(snapshot.getMarketState()) : pushLanguageService.marketStateLabel(MarketState.NEUTRAL))
                .append(" | ").append(pushLanguageService.text("上证 ", "SSE ")).append(formatPct(snapshot.getShChangePct()))
                .append(" / ").append(pushLanguageService.text("深成指 ", "SZSE ")).append(formatPct(snapshot.getSzChangePct()))
                .append(" / ").append(pushLanguageService.text("创业板 ", "ChiNext ")).append(formatPct(snapshot.getCybChangePct()))
                .append(" | ").append(pushLanguageService.text("上涨 ", "Advancers ")).append(Math.max(0, snapshot.getUpCount()))
                .append(" / ").append(pushLanguageService.text("下跌 ", "Decliners ")).append(Math.max(0, snapshot.getDownCount()))
                .append(" | ").append(pushLanguageService.text("涨停 ", "Limit-Up ")).append(Math.max(0, snapshot.getLimitUpCount()))
                .append(" / ").append(pushLanguageService.text("跌停 ", "Limit-Down ")).append(Math.max(0, snapshot.getLimitDownCount()))
                .append(" | ").append(resolveMarketInterpretation(snapshot))
                .append("</font>\n\n");
    }

    private String resolveMarketInterpretation(MarketSnapshot snapshot) {
        if (snapshot == null) {
            return pushLanguageService.text("暂无市场快照，按中性环境处理", "No market snapshot is available, so treat the environment as neutral.");
        }
        MarketState marketState = snapshot.getMarketState() != null ? snapshot.getMarketState() : MarketState.NEUTRAL;
        return switch (marketState) {
            case DEFENSIVE -> pushLanguageService.text("盘面偏防守，优先识别风险扩散与逆势硬逻辑", "The tape is defensive. Prioritize risk propagation and truly resilient setups.");
            case RISK_ON -> pushLanguageService.text("盘面进入进攻态，优先辨识带队龙头与主线扩散", "The tape is risk-on. Focus on leaders that can pull the theme forward.");
            case OVERHEAT -> pushLanguageService.text("盘面情绪过热，龙头仍强但后排追高容错下降", "Sentiment is overheated. Leaders may still hold up, but chasing laggards is far less forgiving.");
            default -> pushLanguageService.text("盘面仍在中性区间，宁缺毋滥地筛选高确定性催化", "The tape remains neutral. Stay selective and only keep high-conviction catalysts.");
        };
    }

    private String withLanguageInstruction(String basePrompt) {
        return pushLanguageService.aiOutputInstruction() + "\n\n" + basePrompt;
    }

    private String aMorningScoreNote() {
        return pushLanguageService.text(
                "<font color=\"comment\">口径说明：机会榜/风险榜中的“事件评分”按最近24小时窗口内的股票聚合分计算，不是单条公告原始分；若与 MCP 的近30天或其他滚动窗口查询对比，分数可能不同。</font>",
                "<font color=\"comment\">Methodology: The event score shown in the Opportunity Board and Risk Board is an aggregated stock score over the latest 24-hour window, not the raw score of a single notice. It may differ from MCP queries that use a 30-day or other rolling window.</font>"
        );
    }

    private String aEveningScoreNote() {
        return pushLanguageService.text(
                "<font color=\"comment\">口径说明：下方“当日热度/事件评分”按今日 09:00-15:00 交易时段内的股票聚合分计算，不是单条公告原始分；若与 MCP 的近30天或最近24小时滚动窗口查询对比，分数可能不同。</font>",
                "<font color=\"comment\">Methodology: The session heat and event score below are aggregated stock scores over today's 09:00-15:00 trading window, not the raw score of a single notice. They may differ from MCP queries using the latest 24 hours or a 30-day rolling window.</font>"
        );
    }

    private String formatPct(double value) {
        return String.format(Locale.ROOT, "%+.2f%%", value);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...";
    }

    private int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
