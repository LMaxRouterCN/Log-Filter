package com.lmaxrouter.logfilter.filter;

import com.lmaxrouter.logfilter.LogFilterMod;
import com.lmaxrouter.logfilter.config.FilterConfig;
import org.apache.logging.log4j.Level;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LogFilter {
    private final FilterConfig config;
    private final Set<Integer> filteredCache;

    public LogFilter(FilterConfig config) {
        this.config = config;
        this.filteredCache = ConcurrentHashMap.newKeySet();
    }

    // --- 调试输出方法 ---
    private void debug(String msg) {
        if (config.isDebugMode()) {
            // 使用 Mod 的专用 Logger，而不是 System.out
            LogFilterMod.LOGGER.info("[DEBUG] {}", msg);
        }
    }

    public boolean shouldFilterByLevel(Level level) {
        if (!config.isEnableFilter()) return false;
        for (String configLevel : config.getLogLevels()) {
            if (level.name().equalsIgnoreCase(configLevel)) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldFilterByLogger(String loggerName) {
        if (!config.isEnableFilter()) return false;
        for (String name : config.getLoggerNames()) {
            if (loggerName.equals(name) || loggerName.startsWith(name + ".")) {
                return true;
            }
        }
        return false;
    }

    // 用于 Manager 调用的静态日志方法
    public void logDebug(org.apache.logging.log4j.core.LogEvent event, String reason) {
        if (config.isDebugMode()) {
            LogFilterMod.LOGGER.info("[DEBUG] Filtered '{}': Reason={}",
                    event.getLoggerName(), reason);
        }
    }

    public boolean isWhitelisted(String message) {
        for (java.util.regex.Pattern excludePattern : config.getExcludePatterns()) {
            if (excludePattern.matcher(message).find()) {
                return true;
            }
        }
        return false;
    }

    public boolean isInCache(int cacheKey) {
        return filteredCache.contains(cacheKey);
    }

    public boolean shouldFilterContent(String message, int cacheKey) {
        if (!config.isEnableFilter()) return false;

        for (String exact : config.getExactMatches()) {
            if (message.equals(exact)) {
                addToCache(cacheKey);
                if (config.isDebugMode()) {
                    LogFilterMod.LOGGER.info("[DEBUG] Filtered by Exact Match: {}", exact);
                }
                return true;
            }
        }

        for (java.util.regex.Pattern pattern : config.getFilterPatterns()) {
            if (pattern.matcher(message).find()) {
                addToCache(cacheKey);
                if (config.isDebugMode()) {
                    LogFilterMod.LOGGER.info("[DEBUG] Filtered by Regex: {}", pattern.pattern());
                }
                return true;
            }
        }

        return false;
    }

    private void addToCache(int key) {
        if (filteredCache.size() >= config.getMaxCacheSize()) {
            filteredCache.clear();
        }
        filteredCache.add(key);
    }
}
