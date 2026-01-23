package com.example.logfilter.config;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FilterConfig {
    private final List<Pattern> filterPatterns;
    private final List<String> exactMatches;
    private final List<String> loggerNames;
    private final List<String> logLevels;
    private final List<Pattern> excludePatterns;
    private final boolean enableFilter;
    private final boolean debugMode;
    private final int maxCacheSize;

    public FilterConfig() {
        this.enableFilter = com.example.logfilter.config.ModConfig.enableFilter.get();
        this.debugMode = com.example.logfilter.config.ModConfig.debugMode.get();
        this.maxCacheSize = com.example.logfilter.config.ModConfig.maxCacheSize.get();

        // Compile regex patterns
        this.filterPatterns = com.example.logfilter.config.ModConfig.filterRules.get().stream()
                .map(Pattern::compile)
                .collect(Collectors.toList());

        this.excludePatterns = com.example.logfilter.config.ModConfig.excludePatterns.get().stream()
                .map(Pattern::compile)
                .collect(Collectors.toList());

        this.exactMatches = List.copyOf(com.example.logfilter.config.ModConfig.exactMatches.get());
        this.loggerNames = List.copyOf(com.example.logfilter.config.ModConfig.loggerNames.get());
        this.logLevels = List.copyOf(com.example.logfilter.config.ModConfig.logLevels.get());
    }

    public List<Pattern> getFilterPatterns() {
        return filterPatterns;
    }

    public List<String> getExactMatches() {
        return exactMatches;
    }

    public List<String> getLoggerNames() {
        return loggerNames;
    }

    public List<String> getLogLevels() {
        return logLevels;
    }

    public List<Pattern> getExcludePatterns() {
        return excludePatterns;
    }

    public boolean isEnableFilter() {
        return enableFilter;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }
}
