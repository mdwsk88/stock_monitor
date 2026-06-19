package com.dawei.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 企业微信群级关注项。
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("a_wecom_subscription")
public class WeComSubscription {

    private String id;
    private String subscriptionType;
    private String targetName;
    private String stockCode;
    private Integer enabled;
    private String source;
    private String reason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
