package com.example.logfilter.filter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogEntry {
    private final String loggerName;
    private final String level;
    private final String message;
    private final Throwable throwable;
    private final long timestamp;
    private final String threadName;

    public LogEntry(String loggerName, String level, String message,
                    Throwable throwable, String threadName) {
        this.loggerName = loggerName;
        this.level = level;
        this.message = message;
        this.throwable = throwable;
        this.timestamp = System.currentTimeMillis();
        this.threadName = threadName;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getThreadName() {
        return threadName;
    }

    public String getFormattedMessage() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        LocalDateTime dateTime = LocalDateTime.now();
        String timestampStr = dateTime.format(formatter);

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestampStr).append("] ");
        sb.append("[").append(threadName).append("] ");
        sb.append("[").append(level).append("] ");
        sb.append("[").append(loggerName).append("] ");
        sb.append(message);

        return sb.toString();
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LogEntry)) return false;
        LogEntry other = (LogEntry) obj;
        return loggerName.equals(other.loggerName) &&
                level.equals(other.level) &&
                message.equals(other.message);
    }

    @Override
    public int hashCode() {
        int result = loggerName.hashCode();
        result = 31 * result + level.hashCode();
        result = 31 * result + message.hashCode();
        return result;
    }
}
