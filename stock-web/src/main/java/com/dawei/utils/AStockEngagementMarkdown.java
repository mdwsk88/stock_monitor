package com.dawei.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * A 股企业微信推送的追问和反馈入口。
 */
public final class AStockEngagementMarkdown {

    private AStockEngagementMarkdown() {
    }

    public static String appendReportTail(String markdown) {
        if (StringUtils.isBlank(markdown) || markdown.contains("继续追问：")) {
            return markdown;
        }
        return markdown.trim() + "\n\n" + reportTail();
    }

    public static String appendRealtimeTail(String markdown, String targetHint) {
        if (StringUtils.isBlank(markdown) || markdown.contains("继续追问：")) {
            return markdown;
        }
        String target = StringUtils.defaultIfBlank(targetHint, "这条线索");
        return markdown.trim() + "\n\n" + realtimeTail(target);
    }

    public static String reportTail() {
        return "💬 继续追问：\n"
                + "👉 @A股分析专家 为什么 [股票/主题] 上榜？\n"
                + "👉 @A股分析专家 看同主题共振股\n"
                + "👉 @A股分析专家 关注 [主题或股票]\n\n"
                + "<font color=\"comment\">📮 轻反馈：@A股分析专家 有用 / 太吵 / 太晚 / 不相关。你的反馈会用于优化后续推送阈值。</font>";
    }

    public static String realtimeTail(String target) {
        return "💬 继续追问：\n"
                + "👉 @A股分析专家 " + target + " 为什么现在值得看？\n"
                + "👉 @A股分析专家 看同主题共振股\n"
                + "👉 @A股分析专家 关注 " + target + "\n\n"
                + "<font color=\"comment\">📮 轻反馈：@A股分析专家 有用 / 太吵 / 太晚 / 不相关。</font>";
    }
}
