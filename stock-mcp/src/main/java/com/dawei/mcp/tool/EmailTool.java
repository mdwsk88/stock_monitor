package com.dawei.mcp.tool;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * @ClassName DateTool
 * @Author 风间影月
 * @Version 1.0
 * @Description DateTool
 **/
@Component
@Slf4j
public class EmailTool {

    private final JavaMailSender mailSender;
    private final String from;

    @Autowired
    public EmailTool(JavaMailSender mailSender, @Value("${spring.mail.username}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailInfo {
        @ToolParam(description = "收件人地址")
        private String email;
        @ToolParam(description = "发送邮件的标题/主题")
        private String subject;
        @ToolParam(description = "发送邮件的消息/正文内容")
        private String content;
        @ToolParam(description = "邮件的内容是否为HTML还是MarkDown格式，如果是MarkDown格式，则为1；如果是HTML格式，则为2")
        private Integer contentType;
    }

    @Tool(description = "查询老板的邮件/邮箱地址")
    public String getMyEmail() {
        return "924038395@qq.com";
    }

    @Tool(description = "给指定的邮箱发送邮件信息，email 为收件人地址，subject 为邮件标题，content 为邮件的内容")
    public void sendMail(EmailInfo emailInfo) {
        log.info("========== 调用MCP工具：sendMail() ==========");

        Integer contentType = emailInfo.getContentType();

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);

            helper.setFrom(from);
            helper.setTo(emailInfo.getEmail());
            helper.setSubject(emailInfo.getSubject());

            if (contentType == 1) {
                helper.setText(convertToHtml(emailInfo.getContent()), true);
            } else if (contentType == 2) {
                helper.setText(emailInfo.getContent(), true);
            } else {
                helper.setText(emailInfo.getContent());
            }

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("邮件发送失败：{}", e.getMessage());
        }

    }

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


}
