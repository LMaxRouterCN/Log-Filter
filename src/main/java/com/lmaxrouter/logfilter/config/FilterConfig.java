package com.lmaxrouter.logfilter.config;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
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
        this.enableFilter = com.lmaxrouter.logfilter.config.ModConfig.enableFilter.get();
        this.debugMode = com.lmaxrouter.logfilter.config.ModConfig.debugMode.get();
        this.maxCacheSize = com.lmaxrouter.logfilter.config.ModConfig.maxCacheSize.get();

        // Compile regex patterns
        // [长期记忆: 003] 构造期加固: 原实现 .map(Pattern::compile) 裸奔, 一条非法正则即炸掉整个配置加载(启动崩溃/热重载失败);
        // 改为逐条容错编译, 非法项只跳过该条并经 ConfigIssues 上报(日志 WARN + 聊天框), 其余规则照常生效
        this.filterPatterns = com.lmaxrouter.logfilter.config.ModConfig.filterRules.get().stream()
                .map(raw -> compileRule(raw, "filterRules"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        this.excludePatterns = com.lmaxrouter.logfilter.config.ModConfig.excludePatterns.get().stream()
                .map(raw -> compileRule(raw, "excludePatterns"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        this.exactMatches = List.copyOf(com.lmaxrouter.logfilter.config.ModConfig.exactMatches.get());
        this.loggerNames = List.copyOf(com.lmaxrouter.logfilter.config.ModConfig.loggerNames.get());
        this.logLevels = List.copyOf(com.lmaxrouter.logfilter.config.ModConfig.logLevels.get());
    }

    /**
     * [长期记忆: 003] 单条正则的容错编译: 非法项返回 null(调用方过滤), 并经 ConfigIssues 统一上报。
     * 只取异常描述与出错下标, 保证单行日志; 下标是规则字符串内的位置,
     * TOML 双引号写法因多一层转义会偏移, 对照原文时注意(推荐单引号字面写法, 详见 ModConfig 注释)。
     */
    private static Pattern compileRule(String raw, String configKey) {
        try {
            return Pattern.compile(raw);
        } catch (PatternSyntaxException e) {
            ConfigIssues.report(configKey + " entry \"" + raw + "\": "
                    + e.getDescription() + " (at index " + e.getIndex() + ")");
            return null;
        }
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