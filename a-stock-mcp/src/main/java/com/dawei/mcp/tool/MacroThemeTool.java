package com.dawei.mcp.tool;

import com.dawei.dto.MacroThemeCard;
import com.dawei.dto.ResonanceStockCard;
import com.dawei.service.MacroThemeResearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class MacroThemeTool {

    @Resource
    private MacroThemeResearchService macroThemeResearchService;

    @Tool(description = "当用户询问今天什么方向强、宏观主线是什么、当前市场风口是什么时使用。"
            + "返回最近一段时间的核心宏观主题卡片，包含主题名、signalScore、importanceLevel、映射股票数量和代表标题。")
    public List<MacroThemeCard> getMacroThemeBoard(
            @ToolParam(description = "回看小时数，默认 24，最大 72") Integer hours,
            @ToolParam(description = "最小主题 signalScore，默认 80") Integer minSignalScore,
            @ToolParam(description = "返回数量，默认 6") Integer limit) {
        log.info("========== 调用MCP工具：getMacroThemeBoard() ==========");
        log.info("| hours: {}", hours);
        log.info("| minSignalScore: {}", minSignalScore);
        log.info("| limit: {}", limit);
        return macroThemeResearchService.getMacroThemeBoard(hours, minSignalScore, limit);
    }

    @Tool(description = "当用户询问今天买什么、今天有什么共振票、某个主题有哪些代表股时使用。"
            + "返回主题自动候选池与宏观主题映射后的共振股票卡片，适合直接给出关注方向。")
    public List<ResonanceStockCard> getThemeResonanceBoard(
            @ToolParam(description = "可选的主题名，例如 低空经济、算力；为空时返回全市场共振票") String themeName,
            @ToolParam(description = "回看小时数，默认 24，最大 72") Integer hours,
            @ToolParam(description = "返回数量，默认 6") Integer limit) {
        log.info("========== 调用MCP工具：getThemeResonanceBoard() ==========");
        log.info("| themeName: {}", themeName);
        log.info("| hours: {}", hours);
        log.info("| limit: {}", limit);
        return macroThemeResearchService.getThemeResonanceBoard(themeName, hours, limit);
    }
}
