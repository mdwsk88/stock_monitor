package com.dawei.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 企业微信群轻反馈记录。
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("a_wecom_feedback")
public class WeComFeedback {

    private String id;
    private String feedbackType;
    private String targetType;
    private String targetName;
    private String stockCode;
    private String themeName;
    private String pushType;
    private String pushKey;
    private String source;
    private String comment;
    private LocalDateTime createTime;
}
