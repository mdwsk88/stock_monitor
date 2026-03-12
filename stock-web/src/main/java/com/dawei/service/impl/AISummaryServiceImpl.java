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
import java.util.List;
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
        你是一位专业的A股分析师，擅长分析A股公告异动情况。
        
        请根据以下过去24小时内公告异动最频繁的前5只A股数据，生成一份专业的盘前早报总结：
        
        【股票数据】
        {stockData}
        
        【输出要求】
        1. 用简洁专业的语言总结每只股票的公告看点
        2. 分析公告类型和可能的市场影响
        3. 突出显示高频公告股票
        4. 总结控制在300字以内
        5. 语气专业、客观，适合投资参考
        6. 每只股票用一句话概括
        
        请直接输出总结内容，不需要标题。
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
        你是一位国内顶级券商首席分析师，负责将A股公告异动数据转化为具有交易参考价值的专业盘前内参。
        你的分析必须直击交易核心，拒绝"新闻播音员"式的复述，严格过滤掉行政流水账类公告。
        
        【任务描述】
        根据提供的A股异动数据，生成具有投研价值的企业微信 Markdown 报告。
        
        【输入数据】
        报告日期：{reportDate}
        统计时长：过去24小时
        数据源数量：东方财富公告源（已过滤人事/会议/质押等行政噪音）
        
        异动股票数据：
        {stockData}
        
        【核心分析要求 - 必须严格执行】
        1. **多空情绪判定**（必须）：
           - 分析完毕后，必须明确标注情绪：<font color="warning">【强烈看多】</font> / <font color="info">【谨慎看多】</font> / <font color="comment">【中性观望】</font> / <font color="warning">【利空预警】</font>
           - 判定依据：订单/业绩/政策/技术突破/重大合同/并购重组等实质性驱动因素
           - 对于业绩预增、重大合同、技术突破等明确利好，标注【强烈看多】
           - 对于监管问询、诉讼仲裁、业绩暴雷等，标注【利空预警】
        
        2. **强制提炼催化剂**（必须）：
           - 不要复述公告标题！要指出这则公告会影响公司的哪部分业务/财务
           - 格式：【业务/财务影响】+【市场情绪驱动】
           - 示例："中标10亿算力中心订单将直接增厚Q2营收（业务影响），政策利好叠加板块热度引发资金抢筹（情绪驱动）"
        
        3. **异动质量评估**：
           - frequency >= 25: 说明公告重大且市场关注度高，往往是业绩/订单/并购级别
           - frequency 15-24: 有明确实质性利好，值得关注
           - frequency 10-14: 可能是单一事件驱动，需结合情绪判断
        
        【输出格式要求】
        1. 严格使用企业微信支持的 Markdown 语法
        2. 只输出最终的 Markdown 内容，不要任何解释
        3. 股票数量控制在 1-5 只，按异动频次降序排列
        4. 宁缺毋滥：如果没有达到阈值的优质标的，宁可只推1只甚至不推
        5. 每只股票分析控制在 50-80 字，直击要害
        
        【格式规范】
        - 标题格式：🌅 AI 盘前异动雷达 | 日期
        - 开头统计行：扫描时长、数据源数量（已剔除行政噪音）
        - 股票条目格式：
          1. 股票名 (代码) | 🇨🇳 A股
          📈 情绪雷达：<font color="warning">【强烈看多】</font> / <font color="info">【谨慎看多】</font> / <font color="comment">【中性观望】</font> / <font color="warning">【利空预警】</font>
          📊 异动频次：<font color="颜色">次数 次 (等级，较昨日激增 X%)</font>
          🧠 核心催化剂：【业务/财务影响】+【市场情绪驱动】
        - 颜色选择规则：
          * frequency >= 25: color="warning", 等级="极度活跃"
          * 15 <= frequency < 25: color="info", 等级="高度活跃"
          * 10 <= frequency < 15: color="success", 等级="中度活跃"
          * frequency < 10: 不应出现在结果中（已被过滤）
        - 激增比例计算：基于频次本身判断，>=25次写"400%+", 15-24次写"200%+", 10-14次写"显著"
        - 互动引导：固定格式
        - 免责声明：固定格式，使用 <font color="comment">
        
        【示例输出】
        # 🌅 A股盘前异动雷达 | 2026-03-07
        
        过去 24 小时内，系统共扫描全网财经资讯源（已过滤行政噪音），以下标的爆发密集异动，请注意盘前风险与机会：
        
        > 1. 赛力斯 (601127) | 🇨🇳 A股
        > 📈 情绪雷达：<font color="warning">【强烈看多】</font>
        > 📊 异动频次：<font color="warning">28 次 (极度活跃，较昨日激增 400%)</font>
        > 🧠 核心催化剂：最新车型单月交付量创历史新高直接验证产品力（业务影响），智驾系统大版本 OTA 升级叠加华为生态持续催化，资金抢筹意愿强烈。
        
        > 2. 浪潮信息 (000977) | 🇨🇳 A股
        > 📈 情绪雷达：<font color="info">【谨慎看多】</font>
        > 📊 异动频次：<font color="info">19 次 (高度活跃，较昨日激增 200%)</font>
        > 🧠 核心催化剂：国内算力基础设施集中招标大单即将落地（业务影响），硬件服务器板块热度回升，但需关注实际中标份额确认。
        
        💡 AI 深度查股：
        想看上述股票的具体新闻源？或者查询你的自选股？
        👉 请在群内直接发送：@A股分析专家 分析 [股票代码]
        
        <font color="comment">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>
        
        【请生成报告】
        """;

    // A股盘后复盘 Markdown 生成提示词（晚间复盘专属模板）
    private static final String A_EVENING_REPORT_PROMPT = """
        【角色设定】
        你是一位资深的A股盘后复盘专家，擅长解读当日盘面已经发生的事实，帮助投资者理解价格波动背后的核心逻辑。
        你的分析必须聚焦于"解码"今日盘面，而不是预测明日走势。
        
        【任务描述】
        根据提供的A股日内异动数据，生成盘后复盘报告，解释今日价格波动的核心驱动原因。
        
        【输入数据】
        报告日期：{reportDate}
        统计时段：日内（9:00-15:00）
        数据源数量：东方财富公告源（已过滤行政噪音）
        
        异动股票数据：
        {stockData}
        
        【核心分析要求 - 必须严格执行】
        1. **涨跌逻辑解码**（必须）：
           - 作为盘后复盘专家，结合新闻解释该股票今日价格波动的核心驱动原因
           - 格式必须严格按照：【核心原因词】+ 具体逻辑解释
           - 示例："【业绩兑现】盘中公布的季度利润率远超预期，叠加管理层在日内电话会中确认了海外AI服务器追加大单，直接引发午后资金抢筹封板。"
           - 示例："【债务疑云】受外媒关于其非标债务展期谈判遇阻的传闻影响，引发市场对流动性的担忧，导致板块情绪承压。"
           - 示例："【地缘博弈】受美国相关生物安全法案最新进展的扰动，多空双方今日分歧极大，新闻面上澄清公告与外媒小作文交织，呈现宽幅震荡。"
        
        2. **热度等级判定**：
           - 基于frequency判断热度：
             * frequency >= 30: 🔥 爆发
             * 20 <= frequency < 30: 📉 高热（如果是利空）/ 📈 高热（如果是利好）
             * 10 <= frequency < 20: ⚖️ 活跃
        
        3. **结果导向**：
           - 晚间看的是"结果"（业绩兑现、利空砸盘、多空分歧），不是早间的"预期"
           - 分析要切中"今天发生了什么"，而不是"明天可能会怎样"
        
        【输出格式要求】
        1. 严格使用企业微信支持的 Markdown 语法
        2. 只输出最终的 Markdown 内容，不要任何解释
        3. 股票数量控制在 1-5 只，按异动频次降序排列
        4. 宁缺毋滥：如果没有达到阈值的优质标的，宁可只推1只甚至不推
        5. 每只股票分析控制在 60-100 字，直击要害
        
        【格式规范】
        - 标题格式：# 🌆 A股盘后情绪解码 | 日期（使用傍晚图标，一级标题）
        - 开头文案："今日 A 股已收盘。系统回溯了日内（9:00-15:00）全网资讯发酵轨迹，以下标的是今日资金与媒体博弈的绝对核心："
        - 股票条目格式（每行前面都要加 > ，形成引用块）：
          > 1. 股票名 (代码) | 🇨🇳 A股
          > 🔥/📈/📉/⚖️ 当日热度：<font color="颜色">等级 (监控到 X 次高频异动)</font>
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
        
        【示例输出】
        # 🌆 A股盘后情绪解码 | 2026-03-12
        
        今日 A 股已收盘。系统回溯了日内（9:00-15:00）全网资讯发酵轨迹，以下标的是今日资金与媒体博弈的绝对核心：
        
        > 1. 工业富联 (601138) | 🇨🇳 A股
        > 🔥 当日热度：<font color="warning">爆发 (监控到 35 次高频异动)</font>
        > 🧠 涨跌逻辑解码：【业绩兑现】盘中公布的季度利润率远超预期，叠加管理层在日内电话会中确认了海外 AI 服务器追加大单，直接引发午后资金抢筹封板。
        
        > 2. 万科 A (000002) | 🇨🇳 A股
        > 📉 当日热度：<font color="info">高热 (监控到 22 次高频异动)</font>
        > 🧠 涨跌逻辑解码：【债务疑云】受外媒关于其非标债务展期谈判遇阻的传闻影响，引发市场对流动性的担忧，导致板块情绪承压。
        
        > 3. 药明康德 (603259) | 🇨🇳 A股
        > ⚖️ 当日热度：<font color="success">活跃 (监控到 15 次异动)</font>
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
            return summary;
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
            return markdown;
        } catch (Exception e) {
            log.error("生成A股盘前早报 Markdown 失败: {}", e.getMessage(), e);
            return generateFallbackMarkdown(stockAlertList, reportDate, "A股", "🇨🇳");
        }
    }

    @Override
    public String generateAEveningReportMarkdown(List<StockAlertDTO<AStockRss>> stockAlertList, String reportDate) {
        if (stockAlertList == null || stockAlertList.isEmpty()) {
            return buildNoDataMarkdown(reportDate, "A股");
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
            return markdown;
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
            sb.append(i + 1).append(". ").append(stock.getStockCode()).append(" (").append(stock.getStockName()).append(")\n");
            sb.append("   标题: ").append(stock.getTitle()).append("\n");
            sb.append("   类型: ").append(stock.getTag()).append("\n");
            sb.append("   时间: ").append(stock.getPubDate() != null ? stock.getPubDate().format(DATE_FORMATTER) : "N/A").append("\n");
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
        for (int i = 0; i < stockAlertList.size(); i++) {
            StockAlertDTO<AStockRss> dto = stockAlertList.get(i);
            AStockRss stock = dto.getStock();
            sb.append(i + 1).append(". ").append(stock.getStockCode()).append(" (").append(stock.getStockName()).append(")\n");
            sb.append("   名称: ").append(stock.getStockName()).append("\n");
            sb.append("   异动频次: ").append(dto.getFrequency()).append(" 次\n");
            sb.append("   活跃度: ").append(dto.getActivityLevel()).append("\n");
            sb.append("   颜色标签: ").append(dto.getColorTag()).append("\n");
            sb.append("   最新公告标题: ").append(stock.getTitle() != null ? stock.getTitle() : "N/A").append("\n");
            sb.append("   所有相关公告标题(供提炼催化剂): ").append(stock.getTag() != null ? stock.getTag() : "N/A").append("\n");
            sb.append("\n");
        }
        return sb.toString();
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
        sb.append("过去24小时内A股公告异动TOP5：\n\n");
        for (int i = 0; i < stockList.size(); i++) {
            AStockRss stock = stockList.get(i);
            sb.append(i + 1).append(". **").append(stock.getStockCode()).append("** (").append(stock.getStockName()).append(")");
            sb.append(" - ").append(stock.getTitle());
            sb.append("【").append(stock.getTag()).append("】");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建无数据时的 Markdown 消息
     */
    private String buildNoDataMarkdown(String reportDate, String market) {
        String botName = market.equals("美股") ? "@美股分析专家" : "@A股分析专家";
        return "# 🌅 AI 盘前异动雷达 | " + reportDate + "\n\n" +
               "过去 24 小时内，系统共扫描全网财经资讯源（已过滤行政噪音）。\n\n" +
               "<font color=\"warning\">⚠️ 暂无" + market + "异动数据</font>\n" +
               "<font color=\"comment\">（当前阈值：24小时内同一标的异动 >= 10 次，宁缺毋滥）</font>\n\n" +
               "💡 AI 深度查股：\n" +
               "想看具体股票分析？请在群内直接发送：" + botName + " 分析 [股票代码]\n\n" +
               "<font color=\"comment\">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>";
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
                tag = stock.getTag();
            } else {
                stockCode = "未知";
            }

            String displayName = stockName != null ? stockName : stockCode;
            String surgeDesc = getSurgeDescription(dto.getFrequency());
            
            sb.append("> ").append(i + 1).append(". ").append(displayName).append(" (").append(stockCode).append(") | ").append(flag).append(" ").append(market).append("\n");
            sb.append("> 📈 情绪雷达：<font color=\"comment\">【AI分析中...】</font>\n");
            sb.append("> 📊 异动频次：<font color=\"").append(dto.getColorTag()).append("\">").append(dto.getFrequency()).append(" 次 (").append(dto.getActivityLevel()).append(", ").append(surgeDesc).append(")</font>\n");
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
        StringBuilder sb = new StringBuilder();
        sb.append("# 🌆 A股盘后情绪解码 | ").append(reportDate).append("\n\n");
        sb.append("今日 A 股已收盘。系统回溯了日内（9:00-15:00）全网资讯发酵轨迹，以下标的是今日资金与媒体博弈的绝对核心：\n\n");

        for (int i = 0; i < stockAlertList.size(); i++) {
            StockAlertDTO<AStockRss> dto = stockAlertList.get(i);
            AStockRss stock = dto.getStock();
            String stockCode = stock.getStockCode();
            String stockName = stock.getStockName();
            String title = stock.getTitle();
            String tag = stock.getTag();

            String displayName = stockName != null ? stockName : stockCode;
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
            
            sb.append("> ").append(i + 1).append(". ").append(displayName).append(" (").append(stockCode).append(") | 🇨🇳 A股\n");
            sb.append("> ").append(heatIcon).append(" 当日热度：<font color=\"").append(dto.getColorTag()).append("\">").append(heatLevel).append(" (监控到 ").append(dto.getFrequency()).append(" 次高频异动)</font>\n");
            sb.append("> 🧠 涨跌逻辑解码：[AI分析中...] ").append(title != null ? title : "暂无详细分析");
            if (tag != null && !tag.isEmpty()) {
                sb.append(" 相关标题：[").append(tag.substring(0, Math.min(50, tag.length()))).append("...]");
            }
            sb.append("\n\n");
        }

        sb.append("💡 持仓深度体检：\n");
        sb.append("今天的行情让你看不懂？想查查你手里被套的股票今天有没有出什么暗雷？\n");
        sb.append("👉 请在群内直接发送：@A股分析专家 分析 [你的股票代码]\n\n");
        sb.append("<font color=\"comment\">⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于盘后逻辑梳理，绝不构成任何投资或交易建议。</font>");

        return sb.toString();
    }
}
