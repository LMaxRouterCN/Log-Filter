package com.lmaxrouter.logfilter.filter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 注意：此类目前在 LogFilterManager 中不再用于过滤逻辑，
 * 仅保留用于其他模块可能的日志记录需求或数据展示。
 */
public class LogEntry {
    private final String loggerName;
    private final String level;
    private final String message;
    private final Throwable throwable;
    private final long timestamp; // 修正：使用构造时的时间戳
    private final String threadName;

    public LogEntry(String loggerName, String level, String message, Throwable throwable, String threadName) {
        this.loggerName = loggerName;
        this.level = level;
        this.message = message;
        this.throwable = throwable;
        this.timestamp = System.currentTimeMillis(); // 记录创建时间
        this.threadName = threadName;
    }

    // Getters...

    public String getFormattedMessage() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        // 修正：使用 timestamp 转换，而不是 LocalDateTime.now()，确保时间准确
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        String timestampStr = dateTime.format(formatter);

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestampStr).append("] ");
        sb.append("[").append(threadName).append("] ");
        sb.append("[").append(level).append("] ");
        sb.append("[").append(loggerName).append("] ");
        sb.append(message);
        return sb.toString();
    }

    // ... hashCode, equals, toString 保持不变 ...
}
