package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 宏观实时推送回扫结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacroRealtimePushScanResult {

    private Integer scannedCount = 0;
    private Integer pushedCount = 0;
    private Integer skippedCount = 0;
    private List<String> pushedTitles = new ArrayList<>();
}
