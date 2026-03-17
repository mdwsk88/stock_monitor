package com.dawei.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("a_theme_auto_pool")
public class ThemeAutoPoolCandidate {

    private String id;
    private String themeName;
    private String stockCode;
    private String stockName;
    private Integer candidateScore;
    private Integer hitCount;
    private Integer enabled;
    private String reason;
    private LocalDateTime latestPubDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
