package com.dawei.utils;

import com.dawei.entity.AStockMsg;
import com.dawei.entity.AStockPushType;
import com.dawei.entity.AStockRealtimeAlertCard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeComApiTest {

    @Test
    void formatAStockInfoShouldIncludeMergedRealtimeFields() {
        WeComApi weComApi = new WeComApi(mock(RestTemplate.class));
        AStockMsg msg = new AStockMsg();
        msg.setStockCode("600599");
        msg.setStockName("ST熊猫");
        msg.setPubDate("2026-03-16 19:29:49");
        msg.setTitle("ST熊猫:*ST熊猫关于公司股票交易风险提示公告");
        msg.setTag("风险提示性公告 | 终止上市风险提示");
        msg.setEventType("交易风险、退市风险");
        msg.setSignalSide("利空");
        msg.setSignalScore(84);
        msg.setCounts24Hour(2);
        msg.setCounts3Day(3);
        msg.setCounts1Week(5);
        msg.setBatchNoticeCount(2);
        msg.setRelatedTitles("ST熊猫:*ST熊猫关于公司股票可能被终止上市的第四次风险提示公告");

        String content = weComApi.formatAStockInfo(msg);

        assertTrue(content.contains("📎 本轮命中"));
        assertTrue(content.contains("🗂️ 其他标题"));
        assertTrue(content.contains("交易风险、退市风险"));
    }

    @Test
    void formatAStockRealtimeAlertShouldRenderStructuredCard() {
        WeComApi weComApi = new WeComApi(mock(RestTemplate.class));
        AStockRealtimeAlertCard card = new AStockRealtimeAlertCard();
        card.setStockCode("300121");
        card.setStockName("阳谷华泰");
        card.setPushType(AStockPushType.REALTIME_OPPORTUNITY);
        card.setSeverityLabel("核弹级催化");
        card.setSignalScore(93);
        card.setEventType("重大合同");
        card.setTitle("阳谷华泰:关于中标8亿元无人机复材订单的公告");
        card.setConclusion("阳谷华泰(300121) 刚披露高价值利多公告，属于盘中可跟踪的强催化。");
        card.setReasoning("重大合同通常直接抬升订单兑现预期。");
        card.setRiskHint("利好预警不等于直接涨停，仍需看资金承接。");
        card.setMacroThemeName("低空经济");
        card.setMacroTitle("低空飞行基础设施建设提速");
        card.setMacroSignalScore(90);
        card.setResonanceScore(148);
        card.setRelationReason("公告直接命中主线");

        String content = weComApi.formatAStockRealtimeAlert(card);

        assertTrue(content.contains("A股盘中突发预警"));
        assertTrue(content.contains("阳谷华泰(300121)"));
        assertTrue(content.contains("低空经济"));
        assertTrue(content.contains("融合分 148"));
        assertTrue(content.contains("仅供盘中研究与信息跟踪"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendMarkdownMessageAsyncShouldSplitLargeMarkdownIntoMultipleMessages() throws ExecutionException, InterruptedException {
        RestTemplate restTemplate = mock(RestTemplate.class);
        WeComApi weComApi = new WeComApi(restTemplate);
        ReflectionTestUtils.setField(weComApi, "webhookUrlA", "https://example.com/wecom");
        when(restTemplate.postForObject(org.mockito.ArgumentMatchers.eq("https://example.com/wecom"),
                org.mockito.ArgumentMatchers.any(HttpEntity.class), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");

        String longMarkdown = """
                # 🌅 A股盘前异动雷达 | 2026-03-16

                ## 宏观主线
                > 1. 国企改革 | 政策扶持
                > 🧭 方向判断：<font color="warning">【利多】</font>
                > 🎯 主题强度：<font color="warning">115 分 (主线级，关联 3 只映射标的)</font>
                > 🧠 主线解读：新一轮深化国企改革方案明确，重点指向布局优化与并购重组（政策影响），资金将沿“国改+并购”主线寻找潜在标的进行交易。

                ## 共振标的
                > 1. 国投中鲁 (600962) | 国企改革
                > 🔗 共振强度：<font color="warning">180 分 (强共振)</font>
                > 🧠 共振逻辑：公司正处于发行股份购买资产的关键阶段（公告催化），与“国改催生并购机遇”的宏观主线高度契合，极易吸引资金博弈重组进程加速。

                ## 机会榜
                > 1. 国投中鲁 (600962) | 🇨🇳 A股
                > 📈 事件判断：<font color="warning">【强烈看多】</font>
                > 🎯 事件评分：<font color="warning">133 分 (主线级，3 个事件簇 / 3 条支撑公告)</font>
                > 🧠 核心预期差：重大资产重组进入关键执行阶段（业务影响），叠加国改主线，盘前资金将博弈重组成功带来的价值重估。

                ## 风险榜
                > 1. *ST熊猫 (600599) | 🇨🇳 A股
                > ⚠️ 事件判断：<font color="warning">【利空预警】</font>
                > 🎯 事件评分：<font color="warning">145 分 (主线级，3 个事件簇 / 3 条支撑公告)</font>
                > 🧠 风险焦点：同时提示交易风险与终止上市风险提示（风险影响），退市预期急剧升温，资金通常会不计成本抛售以规避最终退市的不确定性。

                💡 AI 深度查股：
                👉 请在群内直接发送：@A股分析专家 分析 [股票代码]

                <font color="comment">⚠️ 免责声明：本数据由程序监听公开资讯并由 AI 自动提炼，仅供逻辑梳理与技术交流。股市有风险，入市需谨慎，绝不构成买卖建议。</font>
                """.repeat(3);

        assertTrue(longMarkdown.getBytes(StandardCharsets.UTF_8).length > 4096);
        assertTrue(weComApi.sendMarkdownMessageAsync(longMarkdown, WeComApi.MarketType.A).get());

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, atLeast(2)).postForObject(org.mockito.ArgumentMatchers.eq("https://example.com/wecom"),
                captor.capture(), org.mockito.ArgumentMatchers.eq(String.class));
        List<HttpEntity<Map<String, Object>>> requests = captor.getAllValues();
        assertTrue(requests.size() > 1);

        for (int i = 0; i < requests.size(); i++) {
            Map<String, Object> requestBody = requests.get(i).getBody();
            Map<String, Object> markdownBody = (Map<String, Object>) requestBody.get("markdown");
            String sentContent = String.valueOf(markdownBody.get("content"));
            assertTrue(sentContent.getBytes(StandardCharsets.UTF_8).length <= 4096);
            assertTrue(sentContent.contains("（" + (i + 1) + "/" + requests.size() + "）"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendMarkdownMessageAsyncShouldShrinkSingleOversizedSectionBeforePosting() throws ExecutionException, InterruptedException {
        RestTemplate restTemplate = mock(RestTemplate.class);
        WeComApi weComApi = new WeComApi(restTemplate);
        ReflectionTestUtils.setField(weComApi, "webhookUrlA", "https://example.com/wecom");
        when(restTemplate.postForObject(org.mockito.ArgumentMatchers.eq("https://example.com/wecom"),
                org.mockito.ArgumentMatchers.any(HttpEntity.class), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");

        String longMarkdown = """
                # 🌆 A股盘后情绪解码 | 2026-03-16

                ## 风险榜
                """ +
                ("""
                > 1. *ST熊猫 (600599) | 🇨🇳 A股
                > 🔥 当日热度：<font color="warning">主线级 (事件评分 145 分，3 个事件簇 / 3 条支撑公告)</font>
                > 🧠 涨跌逻辑解码：【退市压顶】公司日内密集发布股票交易风险提示及终止上市风险公告，退市压力被持续放大，市场情绪恶化，短线资金避险意愿极强。
                """.repeat(18)) +
                """

                <font color="comment">⚠️ 免责声明：本复盘由 AI 基于公开新闻全自动生成，仅用于盘后逻辑梳理，绝不构成任何投资或交易建议。</font>
                """;

        assertTrue(longMarkdown.getBytes(StandardCharsets.UTF_8).length > 4096);
        assertTrue(weComApi.sendMarkdownMessageAsync(longMarkdown, WeComApi.MarketType.A).get());

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(org.mockito.ArgumentMatchers.eq("https://example.com/wecom"),
                captor.capture(), org.mockito.ArgumentMatchers.eq(String.class));
        Map<String, Object> requestBody = captor.getValue().getBody();
        Map<String, Object> markdownBody = (Map<String, Object>) requestBody.get("markdown");
        String sentContent = String.valueOf(markdownBody.get("content"));

        assertTrue(sentContent.getBytes(StandardCharsets.UTF_8).length <= 4096);
        assertFalse(sentContent.contains("免责声明"));
        assertTrue(sentContent.contains("[内容过长，已截断]"));
    }

    @Test
    void sendMarkdownMessageAsyncShouldReturnFalseWhenWeComErrCodeIsNotZero() throws ExecutionException, InterruptedException {
        RestTemplate restTemplate = mock(RestTemplate.class);
        WeComApi weComApi = new WeComApi(restTemplate);
        ReflectionTestUtils.setField(weComApi, "webhookUrlA", "https://example.com/wecom");
        when(restTemplate.postForObject(org.mockito.ArgumentMatchers.eq("https://example.com/wecom"),
                org.mockito.ArgumentMatchers.any(HttpEntity.class), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn("{\"errcode\":40058,\"errmsg\":\"markdown.content exceed max length 4096\"}");

        boolean success = weComApi.sendMarkdownMessageAsync("# test", WeComApi.MarketType.A).get();

        assertFalse(success);
    }
}
