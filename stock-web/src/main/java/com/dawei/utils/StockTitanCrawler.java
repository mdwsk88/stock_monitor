package com.dawei.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class StockTitanCrawler {

    private static final String URL = "https://www.stocktitan.net/news/live.html";

    public static void main(String[] args) throws Exception {

        String targetTitle =
                "SCULLY ROYALTY PROVIDES UPDATE ON ANNUAL GENERAL MEETING";

        List<String> tags = getTags(targetTitle);

        System.out.println(tags);
    }

    public static List<String> getTags(String title) throws Exception {
        Document doc = Jsoup.connect(URL)
                .userAgent("Mozilla/5.0")
                .timeout(10_000)
                .get();
        return extractTagsByTitle(doc, title);
    }

    /**
     * 根据新闻标题提取标签数组
     */
    private static List<String> extractTagsByTitle(Document doc, String title) {
        List<String> result = new ArrayList<>();

        // 1. 找到所有新闻标题节点
        Elements titleElements = doc.select("a.feed-link");

        for (Element titleEl : titleElements) {
            if (title.equals(titleEl.text())) {

                // 2. 向上找到整个 news-row
                Element newsRow = titleEl.closest("div.news-row");

                if (newsRow == null) {
                    continue;
                }

                // 3. 在该 news-row 中找 tags
                Elements tagElements = newsRow.select("div[name=tags] span.badge");

                for (Element tag : tagElements) {
                    result.add(tag.text().trim());
                }
                break;
            }
        }
        return result;
    }
}
