package com.dawei.service.impl;

import com.dawei.entity.AStockRss;
import com.dawei.entity.StockAlertDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A股报告分类器：将事件分成机会与风险两类
 */
@Component
public class AStockReportClassifier {

    private static final List<String> RISK_EVENT_KEYWORDS = List.of(
            "诉讼仲裁", "监管处罚", "业绩承压", "交易风险", "减持套现", "退市风险", "司法处置"
    );
    private static final List<String> RISK_TITLE_KEYWORDS = List.of(
            "退市风险", "立案", "处罚", "诉讼", "仲裁", "司法冻结", "司法拍卖",
            "减持", "异常波动", "终止上市", "风险提示"
    );

    public Sections split(List<StockAlertDTO<AStockRss>> stockAlertList, int sectionLimit) {
        int effectiveLimit = Math.max(1, sectionLimit);
        List<StockAlertDTO<AStockRss>> opportunities = new ArrayList<>();
        List<StockAlertDTO<AStockRss>> risks = new ArrayList<>();

        if (stockAlertList == null) {
            return new Sections(opportunities, risks);
        }

        for (StockAlertDTO<AStockRss> dto : stockAlertList) {
            if (dto == null || dto.getStock() == null) {
                continue;
            }
            if (isRiskAlert(dto)) {
                if (risks.size() < effectiveLimit) {
                    risks.add(dto);
                }
            } else if (opportunities.size() < effectiveLimit) {
                opportunities.add(dto);
            }

            if (opportunities.size() >= effectiveLimit && risks.size() >= effectiveLimit) {
                break;
            }
        }

        return new Sections(opportunities, risks);
    }

    public boolean isRiskAlert(StockAlertDTO<AStockRss> dto) {
        if (dto == null || dto.getStock() == null) {
            return false;
        }

        AStockRss stock = dto.getStock();
        if ("利空".equals(dto.getSignalSide())) {
            return true;
        }
        if (containsAnyKeyword(stock.getEventType(), RISK_EVENT_KEYWORDS)) {
            return true;
        }
        if (containsAnyKeyword(stock.getTitle(), RISK_TITLE_KEYWORDS)
                || containsAnyKeyword(stock.getAnalysisHint(), RISK_TITLE_KEYWORDS)) {
            return true;
        }

        String stockName = stock.getStockName();
        return stockName != null && stockName.toUpperCase(Locale.ROOT).contains("ST");
    }

    private boolean containsAnyKeyword(String text, List<String> keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        return keywords.stream()
                .map(keyword -> keyword.toUpperCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    public record Sections(List<StockAlertDTO<AStockRss>> opportunities,
                           List<StockAlertDTO<AStockRss>> risks) {
    }
}
