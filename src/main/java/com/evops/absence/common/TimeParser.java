package com.evops.absence.common;

import com.evops.common.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 时间解析：兼容 yyyy-MM-dd'T'HH:mm:ss、yyyy-MM-dd HH:mm(:ss)、yyyy-MM-dd。
 */
public final class TimeParser {

    private TimeParser() {
    }

    public static LocalDateTime parse(String text, LocalDateTime fallback) {
        if (text == null || text.trim().isEmpty()) {
            return fallback;
        }
        String value = text.trim();
        List<DateTimeFormatter> formatters = new ArrayList<>();
        formatters.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (Exception e) {
            throw new BusinessException("TIME_FORMAT_INVALID",
                    "时间格式不正确（应为 yyyy-MM-dd HH:mm(:ss) 或 yyyy-MM-dd）：" + text);
        }
    }
}
