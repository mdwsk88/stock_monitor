package com.dawei.utils;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A股交易时段与关注窗口判断。
 */
public final class AStockMarketClock {

    private static final LocalTime MORNING_SESSION_START = LocalTime.of(9, 15);
    private static final LocalTime MORNING_SESSION_END = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_SESSION_START = LocalTime.of(12, 55);
    private static final LocalTime AFTERNOON_SESSION_END = LocalTime.of(15, 0);
    private static final LocalTime ATTENTION_WINDOW_START = LocalTime.of(9, 0);
    private static final LocalTime ATTENTION_WINDOW_END = LocalTime.of(18, 30);

    private AStockMarketClock() {
    }

    public static boolean isWeekday(LocalDateTime time) {
        if (time == null) {
            return false;
        }
        DayOfWeek dayOfWeek = time.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    public static boolean isTradingSession(LocalDateTime time) {
        if (!isWeekday(time)) {
            return false;
        }
        LocalTime current = time.toLocalTime();
        boolean morning = !current.isBefore(MORNING_SESSION_START) && !current.isAfter(MORNING_SESSION_END);
        boolean afternoon = !current.isBefore(AFTERNOON_SESSION_START) && !current.isAfter(AFTERNOON_SESSION_END);
        return morning || afternoon;
    }

    public static boolean isMarketAttentionWindow(LocalDateTime time) {
        if (!isWeekday(time)) {
            return false;
        }
        LocalTime current = time.toLocalTime();
        return !current.isBefore(ATTENTION_WINDOW_START) && !current.isAfter(ATTENTION_WINDOW_END);
    }
}
