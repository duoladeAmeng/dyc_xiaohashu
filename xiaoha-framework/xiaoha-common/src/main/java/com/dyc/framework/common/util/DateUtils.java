package com.dyc.framework.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class DateUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter HOUR_MINUTE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private DateUtils() {
    }

    public static long localDateTime2Timestamp(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public static String localDateTime2String(LocalDateTime time) {
        return time.format(DATE_TIME_FORMATTER);
    }

    public static String parse2DateStr(LocalDateTime time) {
        if (Objects.isNull(time)) {
            return null;
        }
        return time.format(DATE_FORMATTER);
    }

    public static String formatRelativeTime(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        long daysDiff = ChronoUnit.DAYS.between(dateTime, now);
        long hoursDiff = ChronoUnit.HOURS.between(dateTime, now);
        long minutesDiff = ChronoUnit.MINUTES.between(dateTime, now);

        if (daysDiff < 1) {
            if (hoursDiff < 1) {
                return minutesDiff < 10 ? "刚刚" : minutesDiff + " 分钟前";
            }
            return hoursDiff + " 小时前";
        }
        if (daysDiff == 1) {
            return "昨天 " + dateTime.format(HOUR_MINUTE_FORMATTER);
        }
        if (daysDiff < 7) {
            return daysDiff + " 天前";
        }
        if (dateTime.getYear() == now.getYear()) {
            return dateTime.format(MONTH_DAY_FORMATTER);
        }
        return dateTime.format(DATE_FORMATTER);
    }

    public static int calculateAge(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
