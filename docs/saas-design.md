# SaaS 化改造设计方案

## 一、数据库设计

### 1. 客户群管理表 (t_customer_group)

```sql
-- 客户群表：管理多个企业微信群/钉钉群的配置
CREATE TABLE t_customer_group (
    id                  BIGINT UNSIGNED     AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    group_name          VARCHAR(100)        NOT NULL COMMENT '群名称（客户标识）',
    group_type          TINYINT             NOT NULL DEFAULT 1 COMMENT '群类型：1-企业微信, 2-钉钉, 3-飞书',
    webhook_url         VARCHAR(500)        NOT NULL COMMENT 'Webhook URL',
    secret              VARCHAR(500)        NULL COMMENT '加签密钥（钉钉/企微）',
    
    -- 市场订阅配置
    subscribe_us        TINYINT             NOT NULL DEFAULT 1 COMMENT '是否订阅美股：0-否, 1-是',
    subscribe_a         TINYINT             NOT NULL DEFAULT 1 COMMENT '是否订阅A股：0-否, 1-是',
    subscribe_hk        TINYINT             NOT NULL DEFAULT 0 COMMENT '是否订阅港股：0-否, 1-是',
    
    -- 订阅内容配置
    subscribe_notices   TINYINT             NOT NULL DEFAULT 1 COMMENT '是否订阅公告：0-否, 1-是',
    subscribe_reports   TINYINT             NOT NULL DEFAULT 1 COMMENT '是否订阅早晚报：0-否, 1-是',
    subscribe_alerts    TINYINT             NOT NULL DEFAULT 1 COMMENT '是否订阅异动提醒：0-否, 1-是',
    
    -- 营业时间和频率配置
    work_start_time     TIME                NULL COMMENT '开始推送时间（如 09:00）',
    work_end_time       TIME                NULL COMMENT '结束推送时间（如 22:00）',
    
    -- 付费与状态
    expire_time         DATETIME            NOT NULL COMMENT '订阅到期时间',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
    max_daily_msgs      INT                 NOT NULL DEFAULT 100 COMMENT '每日最大推送条数限制',
    
    -- 元数据
    remark              VARCHAR(500)        NULL COMMENT '备注',
    create_time         DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX idx_expire_time (expire_time),
    INDEX idx_status (status),
    INDEX idx_group_type (group_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户群管理表';
```

### 2. 消息推送记录表 (t_message_log)

```sql
-- 消息推送记录表：记录每次推送的详情，用于计费和追踪
CREATE TABLE t_message_log (
    id                  BIGINT UNSIGNED     AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    group_id            BIGINT UNSIGNED     NOT NULL COMMENT '客户群ID',
    message_type        TINYINT             NOT NULL COMMENT '消息类型：1-美股异动, 2-A股公告, 3-早评, 4-晚报',
    market_type         TINYINT             NOT NULL COMMENT '市场类型：1-美股, 2-A股, 3-港股',
    
    -- 消息内容摘要
    title               VARCHAR(500)        NULL COMMENT '消息标题',
    content_md5         VARCHAR(32)         NULL COMMENT '消息内容MD5（用于去重）',
    stock_code          VARCHAR(20)         NULL COMMENT '相关股票代码',
    
    -- 推送结果
    push_status         TINYINT             NOT NULL DEFAULT 0 COMMENT '推送状态：0-待发送, 1-成功, 2-失败',
    push_time           DATETIME            NULL COMMENT '实际推送时间',
    error_msg           VARCHAR(500)        NULL COMMENT '错误信息（失败时记录）',
    response_code       VARCHAR(50)         NULL COMMENT 'Webhook响应码',
    
    -- 成本追踪
    ai_tokens_used      INT                 NULL COMMENT 'AI调用消耗Token数（用于成本核算）',
    
    create_time         DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_group_id (group_id),
    INDEX idx_push_time (push_time),
    INDEX idx_message_type (message_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息推送记录表';
```

### 3. 客户订阅股票表 (t_customer_stock)

```sql
-- 客户订阅股票表：记录客户关注的特定股票（可选功能）
CREATE TABLE t_customer_stock (
    id                  BIGINT UNSIGNED     AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    group_id            BIGINT UNSIGNED     NOT NULL COMMENT '客户群ID',
    stock_code          VARCHAR(20)         NOT NULL COMMENT '股票代码',
    stock_name          VARCHAR(100)        NULL COMMENT '股票名称',
    market_type         TINYINT             NOT NULL COMMENT '市场类型：1-美股, 2-A股, 3-港股',
    
    -- 提醒阈值配置
    notice_threshold    INT                 NULL COMMENT '公告提醒阈值：24小时内公告数超过此值提醒',
    price_alert_enabled TINYINT             NOT NULL DEFAULT 0 COMMENT '是否启用价格提醒',
    
    create_time         DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_group_stock (group_id, stock_code),
    INDEX idx_stock_code (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户订阅股票表';
```

## 二、服务层改造示例

### 1. 多群推送服务接口

```java
package com.dawei.service;

import com.dawei.entity.CustomerGroup;
import com.dawei.entity.MessageLog;
import com.dawei.enums.MarketType;
import com.dawei.enums.MessageType;

import java.util.List;

/**
 * 多群推送服务接口
 */
public interface MultiGroupPushService {
    
    /**
     * 获取所有有效（未过期、已启用）的客户群列表
     */
    List<CustomerGroup> getActiveGroups();
    
    /**
     * 根据市场类型获取订阅的客户群
     */
    List<CustomerGroup> getGroupsByMarket(MarketType marketType);
    
    /**
     * 推送消息到指定客户群
     * 
     * @param group 客户群
     * @param messageType 消息类型
     * @param content 消息内容（Markdown格式）
     * @return 推送记录
     */
    MessageLog pushToGroup(CustomerGroup group, MessageType messageType, String content);
    
    /**
     * 广播消息到所有订阅指定市场的客户群
     * 
     * @param marketType 市场类型
     * @param messageType 消息类型
     * @param content 消息内容
     * @return 推送成功数量
     */
    int broadcastToMarket(MarketType marketType, MessageType messageType, String content);
    
    /**
     * 检查客户群当日推送配额
     */
    boolean checkDailyQuota(Long groupId);
    
    /**
     * 记录推送日志
     */
    void logMessage(MessageLog log);
}
```

### 2. 推送服务实现示例

```java
package com.dawei.service.impl;

import com.dawei.entity.CustomerGroup;
import com.dawei.entity.MessageLog;
import com.dawei.enums.MarketType;
import com.dawei.enums.MessageType;
import com.dawei.mapper.CustomerGroupMapper;
import com.dawei.mapper.MessageLogMapper;
import com.dawei.service.MultiGroupPushService;
import com.dawei.utils.WeComApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class MultiGroupPushServiceImpl implements MultiGroupPushService {

    @Resource
    private CustomerGroupMapper customerGroupMapper;
    
    @Resource
    private MessageLogMapper messageLogMapper;
    
    @Resource
    private WeComApi weComApi;

    @Override
    public List<CustomerGroup> getActiveGroups() {
        return customerGroupMapper.selectActiveGroups(LocalDateTime.now());
    }

    @Override
    public List<CustomerGroup> getGroupsByMarket(MarketType marketType) {
        return customerGroupMapper.selectByMarketType(marketType, LocalDateTime.now());
    }

    @Override
    public MessageLog pushToGroup(CustomerGroup group, MessageType messageType, String content) {
        // 检查配额
        if (!checkDailyQuota(group.getId())) {
            log.warn("群 [{}] 当日推送配额已用完", group.getGroupName());
            return null;
        }
        
        // 检查推送时间窗口
        if (!isInWorkTime(group)) {
            log.info("群 [{}] 当前不在推送时间窗口内", group.getGroupName());
            return null;
        }
        
        MessageLog messageLog = new MessageLog();
        messageLog.setGroupId(group.getId());
        messageLog.setMessageType(messageType.getCode());
        messageLog.setMarketType(getMarketType(messageType).getCode());
        messageLog.setContentMd5(MD5.md5(content));
        
        try {
            // 执行推送
            boolean success = weComApi.sendMarkdownMessage(content, group.getWebhookUrl());
            
            messageLog.setPushStatus(success ? 1 : 2);
            messageLog.setPushTime(LocalDateTime.now());
            
            if (!success) {
                messageLog.setErrorMsg("Webhook调用失败");
            }
            
        } catch (Exception e) {
            log.error("推送消息到群 [{}] 失败: {}", group.getGroupName(), e.getMessage());
            messageLog.setPushStatus(2);
            messageLog.setErrorMsg(e.getMessage());
        }
        
        // 记录日志
        messageLogMapper.insert(messageLog);
        
        return messageLog;
    }

    @Override
    public int broadcastToMarket(MarketType marketType, MessageType messageType, String content) {
        List<CustomerGroup> groups = getGroupsByMarket(marketType);
        int successCount = 0;
        
        for (CustomerGroup group : groups) {
            MessageLog log = pushToGroup(group, messageType, content);
            if (log != null && log.getPushStatus() == 1) {
                successCount++;
            }
        }
        
        log.info("广播消息到 [{}] 市场，目标群 [{}] 个，成功 [{}] 个", 
                marketType.getDesc(), groups.size(), successCount);
        
        return successCount;
    }

    @Override
    public boolean checkDailyQuota(Long groupId) {
        LocalDate today = LocalDate.now();
        int todayCount = messageLogMapper.countByGroupAndDate(groupId, today);
        
        CustomerGroup group = customerGroupMapper.selectById(groupId);
        return todayCount < group.getMaxDailyMsgs();
    }

    @Override
    public void logMessage(MessageLog log) {
        messageLogMapper.insert(log);
    }
    
    private boolean isInWorkTime(CustomerGroup group) {
        if (group.getWorkStartTime() == null || group.getWorkEndTime() == null) {
            return true; // 未配置时间限制则全天可推送
        }
        
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int startHour = group.getWorkStartTime().getHour();
        int endHour = group.getWorkEndTime().getHour();
        
        return currentHour >= startHour && currentHour <= endHour;
    }
    
    private MarketType getMarketType(MessageType messageType) {
        return switch (messageType) {
            case US_STOCK_UNUSUAL -> MarketType.US;
            case A_STOCK_NOTICE -> MarketType.A;
            case HK_STOCK_NOTICE -> MarketType.HK;
            default -> MarketType.US;
        };
    }
}
```

## 三、WeComApi 改造示例

```java
package com.dawei.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业微信Webhook工具类 - SaaS版本（支持多群）
 */
@Component
@Slf4j
public class WeComApi {

    @Resource
    private RestTemplate restTemplate;

    /**
     * 发送Markdown消息到指定Webhook
     * 
     * @param content Markdown内容
     * @param webhookUrl 企业微信Webhook URL
     * @return 是否发送成功
     */
    public boolean sendMarkdownMessage(String content, String webhookUrl) {
        try {
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("content", content);

            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    webhookUrl, entity, String.class);

            boolean success = response.getStatusCode().is2xxSuccessful();
            if (!success) {
                log.error("企微推送失败，响应: {}", response.getBody());
            }
            return success;

        } catch (Exception e) {
            log.error("企微推送异常: {}", e.getMessage());
            return false;
        }
    }
    
    // ... 其他格式化方法保持不变
}
```

## 四、改造后的定时任务示例

```java
@Component
@EnableScheduling
@Slf4j
public class StockScheduler {

    @Resource
    private RssService rssService;
    
    @Resource
    private MultiGroupPushService pushService;

    /**
     * A股高频抓取并广播到所有订阅A股的群
     */
    @Scheduled(cron = "0 0/5 9-22 * * MON-FRI")
    public void getAStockInfoHighFreq() {
        log.info("【A股高频】开始抓取并广播公告... {}", LocalDateTime.now());
        
        try {
            // 抓取数据
            List<AStockMsg> messages = rssService.fetchAStockNotices();
            
            if (messages.isEmpty()) {
                log.info("【A股高频】没有新公告，跳过广播");
                return;
            }
            
            // 格式化消息
            String content = formatAStockMessages(messages);
            
            // 广播到所有订阅A股的群
            int successCount = pushService.broadcastToMarket(
                MarketType.A, MessageType.A_STOCK_NOTICE, content);
            
            log.info("【A股高频】广播完成，成功推送 [{}] 个群", successCount);
            
        } catch (Exception e) {
            log.error("【A股高频】任务执行失败", e);
        }
    }
}
```

## 五、实施步骤建议

### Phase 1: 基础改造（1-2周）
1. 创建数据库表 `t_customer_group` 和 `t_message_log`
2. 创建 `MultiGroupPushService` 接口和实现
3. 改造 `WeComApi` 支持动态 Webhook URL
4. 将现有的单群配置迁移到新表

### Phase 2: 功能完善（1-2周）
1. 实现定时任务的多群广播
2. 添加配额检查和推送时间窗口控制
3. 开发管理后台（增删改查客户群）

### Phase 3: 商业化功能（2-4周）
1. 接入支付系统（记录订阅到期时间）
2. 开发用户自服务平台
3. 添加数据统计和报表功能
