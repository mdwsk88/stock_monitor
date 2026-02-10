# 开发邮件MCP工具 - Markdown转HTML

```
<!-- MarkDown 格式转 HTML 格式工具 -->
<dependency>
    <groupId>com.vladsch.flexmark</groupId>
    <artifactId>flexmark-all</artifactId>
    <version>0.64.8</version>
</dependency>
```

```
/**
 * @Description: markdown 转 html
 * @Author 风间影月
 * @param markdownContent
 * @return String
 */
public static String convertToHtml(String markdownContent) {

    MutableDataSet dataset = new MutableDataSet();
    Parser parser = Parser.builder(dataset).build();
    HtmlRenderer htmlRenderer = HtmlRenderer.builder(dataset).build();

    return htmlRenderer.render(parser.parse(markdownContent));
}
```