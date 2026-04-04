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
public class MacroThemeToolEnglish {

    @Resource
    private MacroThemeResearchService macroThemeResearchService;

    @Tool(description = "Use this tool when the user asks which market direction is strongest, what the main macro line is, "
            + "or which sector is currently hottest. Returns recent macro-theme cards with theme name, signalScore, importanceLevel, "
            + "mapped stock count, and representative headlines.")
    public List<MacroThemeCard> getMacroThemeBoard(
            @ToolParam(description = "Lookback hours, default 24, max 72") Integer hours,
            @ToolParam(description = "Minimum theme signalScore, default 80") Integer minSignalScore,
            @ToolParam(description = "Maximum rows to return, default 6") Integer limit) {
        log.info("========== MCP Tool Call: getMacroThemeBoard() [EN] ==========");
        log.info("| hours: {}", hours);
        log.info("| minSignalScore: {}", minSignalScore);
        log.info("| limit: {}", limit);
        return macroThemeResearchService.getMacroThemeBoard(hours, minSignalScore, limit);
    }

    @Tool(description = "Use this tool when the user asks which resonance names stand out today or which representative stocks belong to a theme. "
            + "Returns resonance stock cards built from the theme auto pool plus macro-theme stock mapping.")
    public List<ResonanceStockCard> getThemeResonanceBoard(
            @ToolParam(description = "Optional theme name such as low-altitude economy or computing power; null returns market-wide resonance names") String themeName,
            @ToolParam(description = "Lookback hours, default 24, max 72") Integer hours,
            @ToolParam(description = "Maximum rows to return, default 6") Integer limit) {
        log.info("========== MCP Tool Call: getThemeResonanceBoard() [EN] ==========");
        log.info("| themeName: {}", themeName);
        log.info("| hours: {}", hours);
        log.info("| limit: {}", limit);
        return macroThemeResearchService.getThemeResonanceBoard(themeName, hours, limit);
    }
}
