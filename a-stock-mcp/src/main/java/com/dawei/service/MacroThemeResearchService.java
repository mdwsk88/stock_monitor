package com.dawei.service;

import com.dawei.dto.MacroThemeCard;
import com.dawei.dto.ResonanceStockCard;

import java.util.List;

public interface MacroThemeResearchService {

    List<MacroThemeCard> getMacroThemeBoard(Integer hours, Integer minSignalScore, Integer limit);

    List<ResonanceStockCard> getThemeResonanceBoard(String themeName, Integer hours, Integer limit);
}
