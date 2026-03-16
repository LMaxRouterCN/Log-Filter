package com.lmaxrouter.logfilter.filter;

import com.lmaxrouter.logfilter.LogFilterMod;
import com.lmaxrouter.logfilter.config.FilterConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.util.Objects;

public class LogFilterManager {
    private static volatile LogFilter logFilter;
    private static FilteringLogFilter log4jFilter;

    public static void initialize() {
        FilterConfig config = new FilterConfig();
        logFilter = new LogFilter(config);

        LoggerContext loggerContext = LoggerContext.getContext(false);
        Configuration configuration = loggerContext.getConfiguration();
        LoggerConfig rootLoggerConfig = configuration.getRootLogger();

        log4jFilter = new FilteringLogFilter();
        log4jFilter.start();

        rootLoggerConfig.addFilter(log4jFilter);
        loggerContext.updateLoggers(configuration);

        LogFilterMod.LOGGER.info("Log Filter initialized");
    }

    public static void reload() {
        FilterConfig newConfig = new FilterConfig();
        logFilter = new LogFilter(newConfig);
        LogFilterMod.LOGGER.info("Log Filter reloaded");
    }

    public static class FilteringLogFilter extends AbstractFilter {
        @Override
        public Result filter(LogEvent event) {
            // === 关键安全措施：防止递归 ===
            // 如果是本 Mod 自身产生的日志（包括调试日志），直接放行。
            // 否则开启调试模式后，调试日志会被过滤器捕获，导致无限递归打印日志。
            if (event.getLoggerName().startsWith(LogFilterMod.MOD_ID)) {
                return Result.NEUTRAL;
            }

            LogFilter currentFilter = logFilter;
            if (currentFilter == null) return Result.NEUTRAL;

            try {
                // 优化：快速检查级别和Logger名称
                if (currentFilter.shouldFilterByLevel(event.getLevel())) {
                    currentFilter.logDebug(event, "Level Match: " + event.getLevel());
                    return Result.DENY;
                }

                if (currentFilter.shouldFilterByLogger(event.getLoggerName())) {
                    currentFilter.logDebug(event, "Logger Name Match");
                    return Result.DENY;
                }

                // 消息内容检查
                String message = event.getMessage().getFormattedMessage();
                int cacheKey = Objects.hash(event.getLoggerName(), event.getLevel(), message);

                if (currentFilter.isInCache(cacheKey)) {
                    // 缓存命中通常不需要打印调试日志，因为量可能非常大
                    return Result.DENY;
                }

                if (currentFilter.isWhitelisted(message)) {
                    return Result.NEUTRAL;
                }

                if (currentFilter.shouldFilterContent(message, cacheKey)) {
                    // 内容匹配的调试日志将在 shouldFilterContent 内部打印
                    return Result.DENY;
                }

                return Result.NEUTRAL;
            } catch (Exception e) {
                return Result.NEUTRAL;
            }
        }
    }
}
