package com.lmaxrouter.logfilter.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class ModConfig {

    public static final ForgeConfigSpec SPEC;

    // === General 段: 过滤总开关与行为 ===
    public static final ForgeConfigSpec.BooleanValue enableFilter;
    public static final ForgeConfigSpec.BooleanValue debugMode;
    public static final ForgeConfigSpec.IntValue maxCacheSize;

    // === FilterRules 段: 过滤规则来源 ===
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> filterRules;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> exactMatches;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> loggerNames;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> logLevels;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> excludePatterns;

    // === [async] 异步日志输出管线 (新增功能): 把 root logger 的 appender 链包进 Log4j2 AsyncAppender ===
    public static final ForgeConfigSpec.BooleanValue enableAsyncLogging;
    public static final ForgeConfigSpec.IntValue asyncQueueSize;
    public static final ForgeConfigSpec.BooleanValue asyncBlockingWhenFull;
    public static final ForgeConfigSpec.LongValue asyncShutdownTimeoutMs;
    public static final ForgeConfigSpec.BooleanValue asyncIncludeLocation;
    public static final ForgeConfigSpec.ConfigValue<String> asyncQueueFactoryClass;

    static {
        final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        // ============ General 段 (注释中英双语: comment() 为 varargs, 每个参数在 toml 中独立成行) ============
        BUILDER.push("General");

        enableFilter = BUILDER
                .comment("Enable or disable log filtering",
                         "启用或禁用日志过滤")
                .define("enableFilter", true);

        debugMode = BUILDER
                .comment("Enable debug mode to see which logs are being filtered",
                         "启用调试模式以查看哪些日志被过滤")
                .define("debugMode", false);

        maxCacheSize = BUILDER
                .comment("Maximum size of filtered log cache (for duplicate detection)",
                         "过滤日志缓存的最大容量(用于重复检测)")
                .defineInRange("maxCacheSize", 1000, 100, 10000);

        BUILDER.pop();

        // ============ FilterRules 段 ============
        BUILDER.push("FilterRules");

        filterRules = BUILDER
                // [2026-10-09] 防坑补充: 真实案例 "(CUDA)" 括号未转义被当作正则捕获组, 规则静默失效, 故增加转义教学注释
                .comment("Regex patterns to filter log messages. Matching is PARTIAL (regex find): a rule hits when it matches any part of the message, so wrapping with '.*' is unnecessary.",
                         "用于过滤日志消息的正则表达式。匹配方式为包含匹配(find): 规则命中消息任意片段即生效, 无需用 '.*' 包裹。",
                         "REGEX ESCAPING: metacharacters ()[]{}*+?.^$|\\ are special; escape them to match literally, e.g. literal parentheses must be written as \\( \\).",
                         "正则转义: 元字符 ()[]{}*+?.^$|\\ 有特殊含义, 按字面匹配必须转义, 例如字面圆括号必须写成 \\( \\)。",
                         "TOML ESCAPING: in double-quoted strings TOML consumes one level of backslashes, so write \"\\\\(CUDA\\\\)\"; in single-quoted literal strings write '\\(CUDA\\)' directly.",
                         "TOML 转义: 双引号字符串中 TOML 会消耗一层反斜杠, 因此需写 \"\\\\(CUDA\\\\)\"; 单引号字面字符串直接写 '\\(CUDA\\)' 即可。",
                         "An invalid regex (e.g. unbalanced parentheses) will crash config loading. Example: 'Terrain Diffusion \\(CUDA\\)' filters both 'uncached region requested' and 'finished generating region' lines.",
                         "非法正则(如括号不闭合)会导致配置加载失败。示例: 'Terrain Diffusion \\(CUDA\\)' 可同时过滤 uncached region requested 与 finished generating region 两种日志。")
                .defineList("filterRules", ArrayList::new,
                        obj -> obj instanceof String && !((String) obj).isEmpty());

        exactMatches = BUILDER
                .comment("Exact message strings to filter (case-sensitive)",
                         "精确匹配并过滤的消息字符串(区分大小写)")
                .defineList("exactMatches", ArrayList::new,
                        obj -> obj instanceof String);

        loggerNames = BUILDER
                .comment("Logger names to completely filter (e.g., 'net.minecraft.server.MinecraftServer')",
                         "完全过滤的 Logger 名称(例如 'net.minecraft.server.MinecraftServer')")
                .defineList("loggerNames", ArrayList::new,
                        obj -> obj instanceof String);

        logLevels = BUILDER
                .comment("Log levels to filter: TRACE, DEBUG, INFO, WARN, ERROR, FATAL",
                         "要过滤的日志级别: TRACE, DEBUG, INFO, WARN, ERROR, FATAL")
                .defineList("logLevels", List.of("TRACE", "DEBUG"),
                        obj -> obj instanceof String);

        excludePatterns = BUILDER
                .comment("Regex patterns that will EXCLUDE logs from filtering (whitelist)",
                         "Regex/TOML escaping rules are the same as filterRules (see its comment above).",
                         "将日志排除在过滤之外的正则表达式(白名单)",
                         "正则与 TOML 的转义规则与 filterRules 相同(见其上方注释)。")
                .defineList("excludePatterns", ArrayList::new,
                        obj -> obj instanceof String);

        BUILDER.pop();

        // === [async] 异步日志输出管线配置段 ===
        // 设计原则: 参数全部外置无硬编码; 队列实现可插拔(预留 Disruptor 升级钩子)
        // [2026-09-29 决策记录] 默认值由 false 改为 true(Max 拍板): 冒烟测试全绿后转正为默认行为
        BUILDER.push("async");

        enableAsyncLogging = BUILDER
                .comment("Enable async log output: wraps all root appenders into a Log4j2 AsyncAppender so console/file I/O runs on a background thread instead of the logging thread",
                         "启用异步日志输出: 将 root logger 的全部 appender 包装进 Log4j2 AsyncAppender, 控制台/文件 I/O 转移到后台线程执行, 打日志的线程只付入队成本")
                .define("enableAsyncLogging", true);

        asyncQueueSize = BUILDER
                .comment("Capacity of the async queue (number of buffered log events)",
                         "异步队列容量(缓冲的日志事件数量)")
                .defineInRange("asyncQueueSize", 4096, 128, 65536);

        asyncBlockingWhenFull = BUILDER
                .comment("When queue is full: true = briefly block the logging thread (no log loss), false = drop the log event",
                         "队列满时: true = 短暂阻塞打日志线程(不丢日志), false = 丢弃该条日志事件")
                .define("asyncBlockingWhenFull", true);

        asyncShutdownTimeoutMs = BUILDER
                .comment("Milliseconds to wait for the queue to drain before discarding remaining events (on pipeline rebuild and JVM shutdown)",
                         "等待队列排干的毫秒数, 超时后丢弃剩余事件(在管线重建与 JVM 关闭时生效)")
                .defineInRange("asyncShutdownTimeoutMs", 5000L, 0L, 600000L);

        asyncIncludeLocation = BUILDER
                .comment("Capture caller location (class/method/line). Costs a stack walk per event; Minecraft's default log pattern does not need it",
                         "捕获调用方位置(类/方法/行号)。每条日志付出一次栈遍历成本; Minecraft 默认日志格式用不到它, 保持 false 即可")
                .define("asyncIncludeLocation", false);

        asyncQueueFactoryClass = BUILDER
                .comment("Fully qualified class name of a org.apache.logging.log4j.core.async.BlockingQueueFactory implementation (empty = default ArrayBlockingQueue). Upgrade hook: a Disruptor-based factory can be injected later via JarJar without code changes",
                         "BlockingQueueFactory 实现类的全限定名(留空 = 默认 ArrayBlockingQueue)。升级钩子: 后续可通过 JarJar 注入 Disruptor 队列工厂而无需改代码")
                .define("asyncQueueFactoryClass", "");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}