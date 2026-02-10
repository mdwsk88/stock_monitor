package com.itzixi.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class GMTDateConverter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static String getNewYorkTime(Date externalDate) {
        return convertGmtToNewYork(externalDate).format(FORMATTER);
    }

    public static LocalDateTime convertGmtToNewYork(Date externalDate) {

        // 1. 将 Date 转为 Instant（UTC 时间点）
        Instant instant = externalDate.toInstant();

        // 2. 解释为 GMT 时间（其实 instant 就是 UTC/GMT）
        ZonedDateTime gmtTime = instant.atZone(ZoneId.of("GMT"));

        // 3. 转换为纽约时间（自动处理夏令时）
        ZonedDateTime newYorkTime = gmtTime.withZoneSameInstant(ZoneId.of("America/New_York"));

        return newYorkTime.toLocalDateTime();
    }

    public static String getBeijingTime(Date externalDate) {
        return convertGmtToBeijing(externalDate).format(FORMATTER);
    }
    public static LocalDateTime convertGmtToBeijing(Date externalDate) {

        // 1. 将 Date 转为 Instant（UTC 时间点）
        Instant instant = externalDate.toInstant();

        // 2. 解释为 GMT 时间（其实 instant 就是 UTC/GMT）
        ZonedDateTime gmtTime = instant.atZone(ZoneId.of("GMT"));

        // 3. 转换为纽约时间（自动处理夏令时）
        ZonedDateTime beijingTime = gmtTime.withZoneSameInstant(ZoneId.of("Asia/Shanghai"));

        return beijingTime.toLocalDateTime();
    }

    public static String getGMTTime(Date externalDate) {
        return convertGmt(externalDate).format(FORMATTER);
    }

    public static LocalDateTime convertGmt(Date externalDate) {

        // 1. 将 Date 转为 Instant（UTC 时间点）
        Instant instant = externalDate.toInstant();

        // 2. 解释为 GMT 时间（其实 instant 就是 UTC/GMT）
        ZonedDateTime gmtTime = instant.atZone(ZoneId.of("GMT"));

        return gmtTime.toLocalDateTime();
    }

    public static LocalDateTime minus24Hour(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        return dateTime.minusHours(24);
    }

    public static LocalDateTime minus3Day(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        return dateTime.minusDays(3);
    }

    public static LocalDateTime minus1Week(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        return dateTime.minusWeeks(1);
    }

    public static LocalDateTime plus1Minute(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        return dateTime.plusMinutes(1);
    }


    public static void main(String[] args) {

        Date gmtDate = new Date();

        System.out.println("GMT 时间: " + getGMTTime(gmtDate));
        System.out.println("北京时间: " + getBeijingTime(gmtDate));
        System.out.println("纽约时间: " + getNewYorkTime(gmtDate));

    }
}