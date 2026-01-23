package com.example.logfilter.filter;

import com.example.logfilter.config.FilterConfig;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LogFilter {
    private final FilterConfig config;
    private final Set<String> filteredCache;

    public LogFilter(FilterConfig config) {
        this.config = config;
        this.filteredCache = ConcurrentHashMap.newKeySet();
    }

    /**
     * Check if a log entry should be filtered out
     * @return true if the log should be filtered (not displayed), false otherwise
     */
    public boolean shouldFilter(LogEntry entry) {
        if (!config.isEnableFilter()) {
            return false;
        }

        String message = entry.getMessage();
        String loggerName = entry.getLoggerName();
        String level = entry.getLevel();

        // Create cache key for this entry
        String cacheKey = loggerName + ":" + level + ":" + message;

        // Check cache first
        if (filteredCache.contains(cacheKey)) {
            return true;
        }

        // Check exclude patterns (whitelist) first
        for (java.util.regex.Pattern excludePattern : config.getExcludePatterns()) {
            if (excludePattern.matcher(message).find()) {
                return false; // Don't filter whitelisted messages
            }
        }

        // Check logger names
        for (String name : config.getLoggerNames()) {
            if (loggerName.equals(name) || loggerName.startsWith(name + ".")) {
                if (config.isDebugMode()) {
                    System.out.println("[LogFilter] Filtered by logger name: " + loggerName);
                }
                addToCache(cacheKey);
                return true;
            }
        }

        // Check log levels
        for (String configLevel : config.getLogLevels()) {
            if (level.equalsIgnoreCase(configLevel)) {
                if (config.isDebugMode()) {
                    System.out.println("[LogFilter] Filtered by level: " + level);
                }
                addToCache(cacheKey);
                return true;
            }
        }

        // Check exact matches
        for (String exact : config.getExactMatches()) {
            if (message.equals(exact)) {
                if (config.isDebugMode()) {
                    System.out.println("[LogFilter] Filtered exact match: " + exact);
                }
                addToCache(cacheKey);
                return true;
            }
        }

        // Check regex patterns
        for (java.util.regex.Pattern pattern : config.getFilterPatterns()) {
            if (pattern.matcher(message).find()) {
                if (config.isDebugMode()) {
                    System.out.println("[LogFilter] Filtered by pattern: " + pattern.pattern());
                }
                addToCache(cacheKey);
                return true;
            }
        }

        return false;
    }

    private void addToCache(String key) {
        if (filteredCache.size() >= config.getMaxCacheSize()) {
            filteredCache.clear();
        }
        filteredCache.add(key);
    }

    public void clearCache() {
        filteredCache.clear();
    }

    public int getCacheSize() {
        return filteredCache.size();
    }
}
