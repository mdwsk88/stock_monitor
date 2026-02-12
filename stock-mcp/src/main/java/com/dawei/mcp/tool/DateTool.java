package com.dawei.mcp.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @ClassName DateTool
 * @Author 风间影月
 * @Version 1.0
 * @Description DateTool
 **/
@Component
@Slf4j
public class DateTool {

    @Tool(description = "根据城市所在的时区ID来获得当前时间")
    public String getCurrentTimeByZoneId(String cityName, String zoneId) {
        log.info("========== 调用MCP工具：getCurrentTimeByZoneId() ==========");
        log.info(String.format("| cityName: %s", cityName));
        log.info(String.format("| zoneId: %s", zoneId));

        ZoneId zone = ZoneId.of(zoneId);
        ZonedDateTime zonedDateTime = ZonedDateTime.now(zone);
        String result = String.format("%s 的当前时间是：%s", cityName, zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return result;
    }

    @Tool(description = "获取当前时间")
    public String getCurrentTime() {
        log.info("========== 调用MCP工具：getCurrentTime() ==========");

        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String result = String.format("当前的时间是：%s", currentTime);

        return result;
    }

}
