package com.lmaxrouter.logfilter.filter;

import com.lmaxrouter.logfilter.LogFilterMod;
import com.lmaxrouter.logfilter.config.FilterConfig;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;

public class LogFilterManager {
    private static LogFilter logFilter;
    private static FilteringLogFilter log4jFilter;

    /**
     * Initialize the log filter system using Log4j2
     */
    public static void initialize() {
        FilterConfig config = new FilterConfig();
        logFilter = new LogFilter(config);

        LoggerContext loggerContext = LoggerContext.getContext(false);
        Configuration configuration = loggerContext.getConfiguration();

        // Get root logger config
        LoggerConfig rootLoggerConfig = configuration.getRootLogger();

        // Create and register Log4j2 filter
        log4jFilter = new FilteringLogFilter();
        log4jFilter.start();

        // Add filter to root logger
        rootLoggerConfig.addFilter(log4jFilter);
        loggerContext.updateLoggers(configuration);

        LogFilterMod.LOGGER.info("Log Filter initialized with {} filter rules",
                config.getFilterPatterns().size());
    }

    /**
     * Reload configuration and reset filter
     */
    public static void reload() {
        if (logFilter != null) {
            logFilter.clearCache();
        }

        FilterConfig newConfig = new FilterConfig();
        logFilter = new LogFilter(newConfig);

        if (log4jFilter != null) {
            log4jFilter.reset();
        }

        LogFilterMod.LOGGER.info("Log Filter reloaded");
    }

    /**
     * Custom Log4j2 Filter
     */
    public static class FilteringLogFilter extends AbstractFilter {
        @Override
        public Result filter(LogEvent event) {
            try {
                LogEntry entry = new LogEntry(
                        event.getLoggerName(),
                        event.getLevel().name(),
                        event.getMessage().getFormattedMessage(),
                        null,
                        event.getThreadName()
                );

                if (logFilter.shouldFilter(entry)) {
                    // Filter this log (don't display it)
                    return Result.DENY;
                }

                // Allow this log
                return Result.NEUTRAL;
            } catch (Exception e) {
                // On error, allow the log through
                return Result.NEUTRAL;
            }
        }

        public void reset() {
            // Called on reload
        }
    }
}
