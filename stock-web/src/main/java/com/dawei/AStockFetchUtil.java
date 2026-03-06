package com.dawei;


import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @ClassName AStockFetchUtil
 * @Author dawei
 * @Version 1.0
 * @Description AStockFetchUtil
 **/
public class AStockFetchUtil {

    // 原地址：https://data.eastmoney.com/notices/hsa/7.html

    public static void main(String[] args) throws Exception {

        String url = "https://np-anotice-stock.eastmoney.com/api/security/ann" +
                "?sr=-1&page_size=50&page_index=1&ann_type=SHA,CYB,SZA,BJA,INV&client_source=web&f_node=3,5,6&s_node=0";

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);

        // 模拟浏览器（很重要）
        httpGet.setHeader("User-Agent", "Mozilla/5.0");
        httpGet.setHeader("Accept", "application/json");

        String json = httpClient.execute(httpGet,
                response -> EntityUtils.toString(response.getEntity(), "UTF-8"));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        JsonNode list = root.path("data").path("list");

        for (JsonNode node : list) {
            String stockCode = node.get("codes").get(0).get("stock_code").asText();
            String stockName = node.get("codes").get(0).get("short_name").asText();
            String title = node.get("title").asText();
            String tag = node.get("columns").get(0).get("column_name").asText();
            String displayTime = node.get("display_time").asText();

            System.out.println(
            "A-Stock-News {" +
                    "stockCode='" + stockCode + '\'' +
                    ", stockName='" + stockName + '\'' +
                    ", title='" + title + '\'' +
                    ", tag='" + tag + '\'' +
                    ", displayTime='" + displayTime + '\'' +
                    '}');
        }
    }

}