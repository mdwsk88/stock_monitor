package com.dawei.mcp.tool;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @ClassName WebFetchTool
 * @Author dawei
 * @Version 1.0
 * @Description 网页内容获取工具，支持将网页HTML内容转换为Markdown格式返回
 **/
@Component
@Slf4j
public class WebFetchTool {

    @Resource
    private RestTemplate restTemplate;

    private final FlexmarkHtmlConverter htmlConverter;

    public WebFetchTool() {
        this.htmlConverter = FlexmarkHtmlConverter.builder().build();
    }

    /**
     * 获取网页内容并转换为Markdown格式
     *
     * @param url 网页URL地址
     * @return 网页内容的Markdown格式文本
     */
    @Tool(description = "获取指定URL网页的内容，并以Markdown格式返回。支持提取文章正文、标题、段落等内容。")
    public String fetchWebContentAsMarkdown(
            @ToolParam(description = "需要获取内容的网页URL地址，例如：https://example.com/article") String url) {
        log.info("========== 调用MCP工具：fetchWebContentAsMarkdown() ==========");
        log.info("| URL: {}", url);

        try {
            // 验证URL格式
            if (!isValidUrl(url)) {
                return "错误：无效的URL格式，请提供有效的网页地址（如：https://example.com）";
            }

            // 设置请求头，模拟浏览器访问
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 发送HTTP请求获取网页内容
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            String htmlContent = response.getBody();
            if (htmlContent == null || htmlContent.isEmpty()) {
                return "错误：无法获取网页内容，响应为空";
            }

            // 使用jsoup解析HTML，提取主要内容
            String extractedHtml = extractMainContent(htmlContent, url);

            // 将HTML转换为Markdown
            String markdownContent = htmlConverter.convert(extractedHtml);

            // 清理Markdown内容
            markdownContent = cleanMarkdown(markdownContent);

            log.info("| 成功获取网页内容，Markdown长度: {} 字符", markdownContent.length());

            // 如果内容太长，进行截断
            if (markdownContent.length() > 18000) {
                markdownContent = markdownContent.substring(0, 15000) + 
                        "\n\n[内容已截断，原始内容过长...]";
            }

            return markdownContent;

        } catch (Exception e) {
            log.error("| 获取网页内容失败: {}", e.getMessage());
            return String.format("错误：获取网页内容失败 - %s", e.getMessage());
        }
    }

    /**
     * 验证URL格式是否有效
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return uri.getScheme() != null && 
                   (uri.getScheme().equals("http") || uri.getScheme().equals("https"));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * 从HTML中提取主要内容，去除广告、导航等无关元素
     */
    private String extractMainContent(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);

        // 移除脚本、样式、导航、广告等无关元素
        doc.select("script, style, nav, header, footer, aside, .advertisement, .ads, " +
                   ".sidebar, .menu, .navigation, #header, #footer, #sidebar, #nav, " +
                   ".social-share, .comments, .comment-section").remove();

        // 尝试查找文章主体内容
        Element mainContent = null;
        
        // 常见的内容容器选择器（按优先级排序）
        String[] contentSelectors = {
            "article",
            "main",
            "[role='main']",
            ".content",
            ".post-content",
            ".article-content",
            ".entry-content",
            "#content",
            "#main-content",
            ".main",
            ".post",
            ".article"
        };

        for (String selector : contentSelectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                mainContent = elements.first();
                break;
            }
        }

        // 如果没找到特定容器，使用body
        if (mainContent == null) {
            mainContent = doc.body();
        }

        if (mainContent != null) {
            // 进一步清理内容中的无关元素
            mainContent.select("iframe, embed, object, .share, .related-posts, .tags, " +
                              ".author-box, .pagination").remove();
            
            return mainContent.html();
        }

        return doc.html();
    }

    /**
     * 清理Markdown内容，移除多余的空行和格式问题
     */
    private String cleanMarkdown(String markdown) {
        if (markdown == null) {
            return "";
        }
        
        // 移除多余的空行
        markdown = markdown.replaceAll("\n{3,}", "\n\n");
        
        // 移除行首行尾的空白字符
        markdown = markdown.trim();
        
        // 移除HTML实体编码的一些常见问题
        markdown = markdown.replace("&nbsp;", " ");
        markdown = markdown.replace("&quot;", "\"");
        markdown = markdown.replace("&lt;", "<");
        markdown = markdown.replace("&gt;", ">");
        markdown = markdown.replace("&amp;", "&");
        
        return markdown;
    }
}
