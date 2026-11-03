package com.lmaxrouter.logfilter.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.InMemoryCommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.lmaxrouter.logfilter.Constants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置底层: NightConfig TOML 文件的单例持有者。
 *
 * [Stonecutter 迁移] 替代原 Forge 的 ForgeConfigSpec:
 *  - 键路径与 Forge 版完全同构(General.enableFilter / FilterRules.* / async.*),
 *    旧 Forge 配置文件 logfilter.toml 可直接被本类读取, 用户零迁移成本;
 *  - 读取走 "get + instanceof + 默认兜底", 不依赖 night-config 的 defaultData 语义,
 *    行为 100% 确定;
 *  - 运行期只读不写盘(首建文件时写一次默认值+注释), 配置文件是用户的单一事实源;
 *  - 热重载由 ConfigWatcher(文件系统事件)驱动, 本类只提供原子引用替换。
 */
public final class LogFilterConfig {

    /** 配置文件名: 与 Forge 时代一致(Forge COMMON 命名规则 = modid + "-common.toml"), 旧用户配置零迁移直接复用 */
    public static final String CONFIG_FILE_NAME = "logfilter-common.toml";

    // ==================== 键定义 ====================

    // === General 段: 过滤总开关与行为 ===
    public static final String KEY_ENABLE_FILTER = "General.enableFilter";
    public static final String KEY_DEBUG_MODE = "General.debugMode";
    public static final String KEY_MAX_CACHE_SIZE = "General.maxCacheSize";

    // === FilterRules 段: 过滤规则来源 ===
    public static final String KEY_FILTER_RULES = "FilterRules.filterRules";
    public static final String KEY_EXACT_MATCHES = "FilterRules.exactMatches";
    public static final String KEY_LOGGER_NAMES = "FilterRules.loggerNames";
    public static final String KEY_LOG_LEVELS = "FilterRules.logLevels";
    public static final String KEY_EXCLUDE_PATTERNS = "FilterRules.excludePatterns";

    // === [async] 异步日志输出管线段 ===
    public static final String KEY_ASYNC_ENABLED = "async.enableAsyncLogging";
    public static final String KEY_ASYNC_QUEUE_SIZE = "async.asyncQueueSize";
    public static final String KEY_ASYNC_BLOCKING = "async.asyncBlockingWhenFull";
    public static final String KEY_ASYNC_SHUTDOWN_TIMEOUT = "async.asyncShutdownTimeoutMs";
    public static final String KEY_ASYNC_INCLUDE_LOCATION = "async.asyncIncludeLocation";
    public static final String KEY_ASYNC_QUEUE_FACTORY = "async.asyncQueueFactoryClass";

    // ==================== 默认值 ====================

    private static final boolean DEFAULT_ENABLE_FILTER = true;
    private static final boolean DEFAULT_DEBUG_MODE = false;
    private static final int DEFAULT_MAX_CACHE_SIZE = 1000;
    private static final List<String> DEFAULT_LOG_LEVELS = List.of("TRACE", "DEBUG");
    private static final boolean DEFAULT_ASYNC_ENABLED = true;
    // [2026-09-29 决策记录] 默认值由 false 改为 true(Max 拍板): 冒烟测试全绿后转正为默认行为
    private static final int DEFAULT_ASYNC_QUEUE_SIZE = 4096;
    private static final boolean DEFAULT_ASYNC_BLOCKING = true;
    private static final long DEFAULT_ASYNC_SHUTDOWN_TIMEOUT = 5000L;
    private static final boolean DEFAULT_ASYNC_INCLUDE_LOCATION = false;
    private static final String DEFAULT_ASYNC_QUEUE_FACTORY = "";

    // ==================== 范围钳制边界 (对齐 Forge defineInRange) ====================
    // [2026-11-03 迁移核对] Forge 版三个数值配置带 defineInRange, 越界值被 FML 收敛到边界;
    // 自读 TOML 无 FML 层, 若不钳制则越界值原样放行(行为漂移),
    // 故读取层用同款边界收敛(见下方 clampInt/clampLong)。

    /** maxCacheSize 合法区间 [100, 10000] (Forge: defineInRange(..., 100, 10000)) */
    private static final int MIN_MAX_CACHE_SIZE = 100;
    private static final int MAX_MAX_CACHE_SIZE = 10000;
    /** asyncQueueSize 合法区间 [128, 65536] (Forge: defineInRange(..., 128, 65536)) */
    private static final int MIN_ASYNC_QUEUE_SIZE = 128;
    private static final int MAX_ASYNC_QUEUE_SIZE = 65536;
    /** asyncShutdownTimeoutMs 合法区间 [0, 600000] (Forge: defineInRange(..., 0L, 600000L)) */
    private static final long MIN_ASYNC_SHUTDOWN_TIMEOUT = 0L;
    private static final long MAX_ASYNC_SHUTDOWN_TIMEOUT = 600000L;

    /** 当前配置实例; null = 尚未 init。volatile: Watcher 线程写 / 任意线程读 */
    private static volatile CommentedFileConfig config;

    /** 配置文件路径; init 后不再变 */
    private static Path configFile;

    private LogFilterConfig() {
    }

    // ==================== 生命周期 ====================

    /**
     * 初始化配置: 打开(或首建)配置文件。
     * 只应由 LogFilterBootstrap.init 在 mod 入口最早期调用一次。
     */
    public static synchronized void init(Path configDir) {
        configFile = configDir.resolve(CONFIG_FILE_NAME);
        config = open();
        // 首次运行: 文件不存在则写默认值+注释。
        // 写盘发生在 ConfigWatcher 启动之前, 自己的写不会触发监听回环(无自触发死循环)
        if (!Files.exists(configFile)) {
            writeDefaults();
            Constants.LOGGER.info("[config] 未找到配置文件, 已生成默认: {}", configFile);
        }
    }

    /**
     * 热重载: 重新解析文件并原子替换引用。
     * 坏 TOML(编辑器写一半被截获)时抛异常 -> 保持旧配置不动, 等下一次事件(编辑完成会再触发)。
     */
    public static synchronized void reload() {
        if (configFile == null) {
            return; // 未 init, 忽略(防御)
        }
        try {
            CommentedFileConfig fresh = open();
            fresh.load();
            CommentedFileConfig old = config;
            config = fresh;
            if (old != null) {
                old.close();
            }
        } catch (Exception e) {
            Constants.LOGGER.warn("[config] 重载失败, 保持旧配置: {}", e.toString());
        }
    }

    private static CommentedFileConfig open() {
        CommentedFileConfig c = CommentedFileConfig.builder(configFile)
                .preserveInsertionOrder() // 写默认文件时保持声明顺序, 人类可读
                .build();
        c.load();
        return c;
    }

    /** 首建文件: 逐键 put 默认值 + setComment 双语注释, 一次落盘 */
    private static void writeDefaults() {
        CommentedFileConfig c = config;
        if (c == null) {
            return;
        }
        c.set(KEY_ENABLE_FILTER, DEFAULT_ENABLE_FILTER);
        c.set(KEY_DEBUG_MODE, DEFAULT_DEBUG_MODE);
        c.set(KEY_MAX_CACHE_SIZE, DEFAULT_MAX_CACHE_SIZE);
        c.set(KEY_FILTER_RULES, new ArrayList<String>());
        c.set(KEY_EXACT_MATCHES, new ArrayList<String>());
        c.set(KEY_LOGGER_NAMES, new ArrayList<String>());
        c.set(KEY_LOG_LEVELS, new ArrayList<>(DEFAULT_LOG_LEVELS));
        c.set(KEY_EXCLUDE_PATTERNS, new ArrayList<String>());
        c.set(KEY_ASYNC_ENABLED, DEFAULT_ASYNC_ENABLED);
        c.set(KEY_ASYNC_QUEUE_SIZE, DEFAULT_ASYNC_QUEUE_SIZE);
        c.set(KEY_ASYNC_BLOCKING, DEFAULT_ASYNC_BLOCKING);
        c.set(KEY_ASYNC_SHUTDOWN_TIMEOUT, DEFAULT_ASYNC_SHUTDOWN_TIMEOUT);
        c.set(KEY_ASYNC_INCLUDE_LOCATION, DEFAULT_ASYNC_INCLUDE_LOCATION);
        c.set(KEY_ASYNC_QUEUE_FACTORY, DEFAULT_ASYNC_QUEUE_FACTORY);

        setComments(c);
        c.save();
    }

    /**
     * 首建文件时的双语注释。
     * 全部自原 Forge ModConfig 平移 —— 注释是用户文档, 迁移不许丢。
     */
    private static void setComments(CommentedConfig c) {
        c.setComment(KEY_ENABLE_FILTER, join(
                "Enable or disable log filtering",
                "启用或禁用日志过滤"));
        c.setComment(KEY_DEBUG_MODE, join(
                "Enable debug mode to see which logs are being filtered",
                "启用调试模式以查看哪些日志被过滤"));
        c.setComment(KEY_MAX_CACHE_SIZE, join(
                "Maximum size of filtered log cache (for duplicate detection)",
                "过滤日志缓存的最大容量(用于重复检测)"));
        // [2026-10-09] 防坑补充: 真实案例 "(CUDA)" 括号未转义被当作正则捕获组, 规则静默失效, 故增加转义教学注释
        // (1.1.1: toml 示例已通用化为 some (thing), 避免特殊语境误导)
        c.setComment(KEY_FILTER_RULES, join(
                "Regex patterns to filter log messages. Matching is PARTIAL (regex find): a rule hits when it matches any part of the message, so wrapping with '.*' is unnecessary.",
                "用于过滤日志消息的正则表达式。匹配方式为包含匹配: 规则命中消息任意片段即生效, 无需用 '.*' 包裹。",
                "REGEX ESCAPING: metacharacters ()[]{}*+?.^$|\\ are special; escape them to match literally, e.g. literal parentheses must be written as \\( \\).",
                "正则转义: 元字符 ()[]{}*+?.^$|\\ 有特殊含义, 按字面匹配必须转义, 例如字面圆括号必须写成 \\( \\)。",
                "TOML ESCAPING: in double-quoted strings TOML consumes one level of backslashes, so write \"some \\\\(thing\\\\)\"; in single-quoted literal strings write 'some \\(thing\\)' directly.",
                "TOML 转义: 双引号字符串中 TOML 会消耗一层反斜杠, 因此需写 \"some \\\\(thing\\\\)\"; 单引号字面字符串直接写 'some \\(thing\\)' 即可。",
                "An invalid regex (e.g. unbalanced parentheses) is safely skipped with a warning (log WARN + in-game chat message); other rules keep working.",
                "非法正则(如括号不闭合)会被安全跳过并告警(日志 WARN + 进入世界时聊天框提示), 其余规则照常生效。"));
        c.setComment(KEY_EXACT_MATCHES, join(
                "Exact message strings to filter (case-sensitive)",
                "精确匹配并过滤的消息字符串(区分大小写)"));
        c.setComment(KEY_LOGGER_NAMES, join(
                "Logger names to completely filter (e.g., 'net.minecraft.server.MinecraftServer')",
                "完全过滤的 Logger 名称(例如 'net.minecraft.server.MinecraftServer')"));
        c.setComment(KEY_LOG_LEVELS, join(
                "Log levels to filter: TRACE, DEBUG, INFO, WARN, ERROR, FATAL",
                "要过滤的日志级别: TRACE, DEBUG, INFO, WARN, ERROR, FATAL"));
        c.setComment(KEY_EXCLUDE_PATTERNS, join(
                "Regex patterns that will EXCLUDE logs from filtering (whitelist)",
                "Regex/TOML escaping rules are the same as filterRules (see its comment above).",
                "将日志排除在过滤之外的正则表达式(白名单)",
                "正则与 TOML 的转义规则与 filterRules 相同(见其上方注释)。"));
        // === [async] 异步日志输出管线 (v2 异步化改造): 把 root logger 的 appender 链包进 Log4j2 AsyncAppender ===
        // 设计原则: 参数全部外置无硬编码; 队列实现可插拔(预留 Disruptor 升级钩子)
        c.setComment(KEY_ASYNC_ENABLED, join(
                "Enable async log output: wraps all root appenders into a Log4j2 AsyncAppender so console/file I/O runs on a background thread instead of the logging thread",
                "启用异步日志输出: 将 root logger 的全部 appender 包装进 Log4j2 AsyncAppender, 控制台/文件 I/O 转移到后台线程执行, 打日志的线程只付入队成本"));
        c.setComment(KEY_ASYNC_QUEUE_SIZE, join(
                "Capacity of the async queue (number of buffered log events)",
                "异步队列容量(缓冲的日志事件数量)"));
        c.setComment(KEY_ASYNC_BLOCKING, join(
                "When queue is full: true = briefly block the logging thread (no log loss), false = drop the log event",
                "队列满时: true = 短暂阻塞打日志线程(不丢日志), false = 丢弃该条日志事件"));
        c.setComment(KEY_ASYNC_SHUTDOWN_TIMEOUT, join(
                "Milliseconds to wait for the queue to drain before discarding remaining events (on pipeline rebuild and JVM shutdown)",
                "等待队列排干的毫秒数, 超时后丢弃剩余事件(在管线重建与 JVM 关闭时生效)"));
        c.setComment(KEY_ASYNC_INCLUDE_LOCATION, join(
                "Capture caller location (class/method/line). Costs a stack walk per event; Minecraft's default log pattern does not need it",
                "捕获调用方位置(类/方法/行号)。每条日志付出一次栈遍历成本; Minecraft 默认日志格式用不到它, 保持 false 即可"));
        c.setComment(KEY_ASYNC_QUEUE_FACTORY, join(
                "Fully qualified class name of a org.apache.logging.log4j.core.async.BlockingQueueFactory implementation (empty = default ArrayBlockingQueue). Upgrade hook: a Disruptor-based factory can be injected later via JarJar without code changes",
                "BlockingQueueFactory 实现类的全限定名(留空 = 默认 ArrayBlockingQueue)。升级钩子: 后续可通过 JarJar 注入 Disruptor 队列工厂而无需改代码"));
    }

    /** 多行注释 join: night-config 把 \n 展开为 TOML 多行 # 注释 */
    private static String join(String... lines) {
        return String.join("\n", lines);
    }

    // ==================== 类型安全读取 (get + instanceof + 默认兜底) ====================

    public static boolean enableFilter() {
        return bool(KEY_ENABLE_FILTER, DEFAULT_ENABLE_FILTER);
    }

    public static boolean debugMode() {
        return bool(KEY_DEBUG_MODE, DEFAULT_DEBUG_MODE);
    }

    public static int maxCacheSize() {
        return clampInt(integer(KEY_MAX_CACHE_SIZE, DEFAULT_MAX_CACHE_SIZE),
                MIN_MAX_CACHE_SIZE, MAX_MAX_CACHE_SIZE);
    }

    public static List<String> filterRules() {
        return stringList(KEY_FILTER_RULES, List.of());
    }

    public static List<String> exactMatches() {
        return stringList(KEY_EXACT_MATCHES, List.of());
    }

    public static List<String> loggerNames() {
        return stringList(KEY_LOGGER_NAMES, List.of());
    }

    public static List<String> logLevels() {
        return stringList(KEY_LOG_LEVELS, DEFAULT_LOG_LEVELS);
    }

    public static List<String> excludePatterns() {
        return stringList(KEY_EXCLUDE_PATTERNS, List.of());
    }

    public static boolean asyncEnabled() {
        return bool(KEY_ASYNC_ENABLED, DEFAULT_ASYNC_ENABLED);
    }

    public static int asyncQueueSize() {
        return clampInt(integer(KEY_ASYNC_QUEUE_SIZE, DEFAULT_ASYNC_QUEUE_SIZE),
                MIN_ASYNC_QUEUE_SIZE, MAX_ASYNC_QUEUE_SIZE);
    }

    public static boolean asyncBlockingWhenFull() {
        return bool(KEY_ASYNC_BLOCKING, DEFAULT_ASYNC_BLOCKING);
    }

    public static long asyncShutdownTimeoutMs() {
        return clampLong(longValue(KEY_ASYNC_SHUTDOWN_TIMEOUT, DEFAULT_ASYNC_SHUTDOWN_TIMEOUT),
                MIN_ASYNC_SHUTDOWN_TIMEOUT, MAX_ASYNC_SHUTDOWN_TIMEOUT);
    }

    public static boolean asyncIncludeLocation() {
        return bool(KEY_ASYNC_INCLUDE_LOCATION, DEFAULT_ASYNC_INCLUDE_LOCATION);
    }

    public static String asyncQueueFactoryClass() {
        return string(KEY_ASYNC_QUEUE_FACTORY, DEFAULT_ASYNC_QUEUE_FACTORY);
    }

    // ==================== 原始读取 helpers ====================

    private static boolean bool(String key, boolean def) {
        Object v = raw(key);
        return v instanceof Boolean b ? b : def;
    }

    private static int integer(String key, int def) {
        Object v = raw(key);
        return v instanceof Integer i ? i : def;
    }

    private static long longValue(String key, long def) {
        // TOML 整数字面量解析为 Integer/Long: 容量小的解析为 Integer, 大的为 Long
        Object v = raw(key);
        if (v instanceof Integer i) {
            return i.longValue();
        }
        return v instanceof Long l ? l : def;
    }

    // [2026-11-03 迁移核对] 范围钳制: 对齐 Forge defineInRange 越界收敛语义(超范围值收敛到边界, 不报错不拒绝)
    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    private static long clampLong(long v, long min, long max) {
        return Math.max(min, Math.min(v, max));
    }

    private static String string(String key, String def) {
        Object v = raw(key);
        return v instanceof String s ? s : def;
    }

    private static List<String> stringList(String key, List<String> def) {
        Object v = raw(key);
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof String s) {
                    out.add(s);
                }
            }
            return out;
        }
        return def;
    }

    /** 弱一致快照读: volatile 引用一次读取; 文件重载瞬间的旧值无害(下一次事件修正) */
    private static Object raw(String key) {
        CommentedFileConfig c = config;
        return c == null ? null : c.get(key);
    }
}