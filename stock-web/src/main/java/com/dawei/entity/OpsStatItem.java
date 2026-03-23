package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 运营看板通用统计项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpsStatItem {

    private String label;
    private int count;
}
