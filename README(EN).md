# Log Filter

A lightweight log filtering mod designed to reduce spam in the console and log files, allowing developers and players to focus on critical errors and save disk space.

## Features

- **Regex Filtering**: Supports powerful regex matching to precisely filter specific log content.
- **Exact Match Filtering**: Can precisely filter out specified entire lines of text.
- **Filter by Logger**: Supports filtering all logs from a mod or class name (Logger Name).
- **Filter by Log Level**: One-click blocking of low-level logs like `TRACE` or `DEBUG`.
  > Block `INFO` logs? Of course! This way you will only see errors and warnings, suitable for debugging.
- **Whitelist Mode**: Set exclusion rules to ensure important logs (like critical errors) are not accidentally deleted.
- **Efficient Caching**: Built-in deduplication cache mechanism to avoid processing the same logs repeatedly.

## Installation

1. Download the mod's JAR file.
2. Put the JAR file into the `mods` folder in your Minecraft installation directory.
3. Launch the game once. The mod will automatically generate a default configuration file.
4. Modify the configuration file as needed and save — the config hot-reloads automatically (no game restart needed). Invalid regexes are skipped with a warning; see the notes below.
> Or manually create a `logfilter-common.toml` file, modify the configuration as needed, and then launch the game.

## Configuration Brief

The configuration file is located at: `config/logfilter-common.toml`
Default configuration:
```toml

[General]
	#Enable or disable log filtering
	#启用或禁用日志过滤
	enableFilter = true
	#Enable debug mode to see which logs are being filtered
	#启用调试模式以查看哪些日志被过滤
	debugMode = false
	#Maximum size of filtered log cache (for duplicate detection)
	#过滤日志缓存的最大容量(用于重复检测)
	#Range: 100 ~ 10000
	maxCacheSize = 1000

[FilterRules]
	#Regex patterns to filter log messages. Matching is PARTIAL (regex find): a rule hits when it matches any part of the message, so wrapping with '.*' is unnecessary.
	#用于过滤日志消息的正则表达式。匹配方式为包含匹配(find): 规则命中消息任意片段即生效, 无需用 '.*' 包裹。
	#REGEX ESCAPING: metacharacters ()[]{}*+?.^$|\ are special; escape them to match literally, e.g. literal parentheses must be written as \( \).
	#正则转义: 元字符 ()[]{}*+?.^$|\ 有特殊含义, 按字面匹配必须转义, 例如字面圆括号必须写成 \( \)。
	#TOML ESCAPING: in double-quoted strings TOML consumes one level of backslashes, so write "some \\(thing\\)"; in single-quoted literal strings write 'some \(thing\)' directly.
	#TOML 转义: 双引号字符串中 TOML 会消耗一层反斜杠, 因此需写 "some \\(thing\\)"; 单引号字面字符串直接写 'some \(thing\)' 即可。
	#An invalid regex (e.g. unbalanced parentheses) is safely skipped with a warning (log WARN + in-game chat message); other rules keep working.
	#非法正则(如括号不闭合)会被安全跳过并告警(日志 WARN + 进入世界时聊天框提示), 其余规则照常生效。
	filterRules = []
	#Exact message strings to filter (case-sensitive)
	#精确匹配并过滤的消息字符串(区分大小写)
	exactMatches = []
	#Logger names to completely filter (e.g., 'net.minecraft.server.MinecraftServer')
	#完全过滤的 Logger 名称(例如 'net.minecraft.server.MinecraftServer')
	loggerNames = []
	#Log levels to filter: TRACE, DEBUG, INFO, WARN, ERROR, FATAL
	#要过滤的日志级别: TRACE, DEBUG, INFO, WARN, ERROR, FATAL
	logLevels = ["TRACE", "DEBUG"]
	#Regex patterns that will EXCLUDE logs from filtering (whitelist)
	#Regex/TOML escaping rules are the same as filterRules (see its comment above).
	#将日志排除在过滤之外的正则表达式(白名单)
	#正则与 TOML 的转义规则与 filterRules 相同(见其上方注释)。
	excludePatterns = []

[async]
	#Enable async log output: wraps all root appenders into a Log4j2 AsyncAppender so console/file I/O runs on a background thread instead of the logging thread
	#启用异步日志输出: 将 root logger 的全部 appender 包装进 Log4j2 AsyncAppender, 控制台/文件 I/O 转移到后台线程执行, 打日志的线程只付入队成本
	enableAsyncLogging = true
	#Capacity of the async queue (number of buffered log events)
	#异步队列容量(缓冲的日志事件数量)
	#Range: 128 ~ 65536
	asyncQueueSize = 4096
	#When queue is full: true = briefly block the logging thread (no log loss), false = drop the log event
	#队列满时: true = 短暂阻塞打日志线程(不丢日志), false = 丢弃该条日志事件
	asyncBlockingWhenFull = true
	#Milliseconds to wait for the queue to drain before discarding remaining events (on pipeline rebuild and JVM shutdown)
	#等待队列排干的毫秒数, 超时后丢弃剩余事件(在管线重建与 JVM 关闭时生效)
	#Range: 0 ~ 600000
	asyncShutdownTimeoutMs = 5000
	#Capture caller location (class/method/line). Costs a stack walk per event; Minecraft's default log pattern does not need it
	#捕获调用方位置(类/方法/行号)。每条日志付出一次栈遍历成本; Minecraft 默认日志格式用不到它, 保持 false 即可
	asyncIncludeLocation = false
	#Fully qualified class name of a org.apache.logging.log4j.core.async.BlockingQueueFactory implementation (empty = default ArrayBlockingQueue). Upgrade hook: a Disruptor-based factory can be injected later via JarJar without code changes
	#BlockingQueueFactory 实现类的全限定名(留空 = 默认 ArrayBlockingQueue)。升级钩子: 后续可通过 JarJar 注入 Disruptor 队列工厂而无需改代码
	asyncQueueFactoryClass = ""


```

### General (General Settings)

| Option | Default | Description |
| :--- | :--- | :--- |
| `enableFilter` | `true` | Whether to enable the log filtering function. |
| `debugMode` | `false` | Enable debug mode to print filtered logs to the console, convenient for testing rules. |
| `maxCacheSize` | `1000` | The cache size for filtered logs, used for deduplication. |

### FilterRules (Filtering Rules)

#### 1. `filterRules` (Regex)

Use regex to match logs to be filtered.
```toml
filterRules = [
    ".*Exception.*",          # Filter out all logs containing "Exception"
    ".*stack trace.*",        # Filter out stack traces
    ".*Could not pass event.*"# Filter out specific event errors
]
```

> **⚠ Common Pitfall: Regex Metacharacters & Double TOML Escaping**
>
> 1. **Regex layer**: `()[]{}*+?.^$|\` are regex metacharacters; escape them for literal matching. E.g. the parentheses in log `some (thing)` must be written as `\(thing\)` — otherwise they are parsed as a capture group and the rule silently never matches (the regex itself is valid, no error is shown).
> 2. **TOML layer**: inside double-quoted strings TOML consumes one level of backslashes, so double them up: `"some \\(thing\\)"`; **single-quoted literal strings are recommended** (no TOML escaping needed): `'some \(thing\)'`.
> 3. **Match semantics**: `filterRules` uses partial matching (find); a rule hits if it matches any part of the message, so no `.*` wrapping is needed.
> 4. **Invalid regex** (e.g. unbalanced parentheses) is safely skipped: a WARN is written to the log and an in-game chat warning appears when you enter a world; all other rules keep working — no startup crash. Validate rules at regex101.com (Java flavor) first.

#### 2. `exactMatches` (Exact Match)

Logs are filtered only when their content is **exactly identical** (case-sensitive).
```toml
exactMatches = [
    "This message will be completely filtered out"
]
```

#### 3. `loggerNames` (Logger Names)

Filter based on the source of the log (class name or mod package name). Supports hierarchical filtering.
```toml
loggerNames = [
    "net.minecraft.server.MinecraftServer",  # Filter out logs from the main server
    "com.some.noisy.mod"                     # Filter out all logs from a noisy mod
]
```

#### 4. `logLevels` (Log Levels)

Filter based on log level. Available values: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`.
```toml
logLevels = [
    "TRACE",
    "DEBUG"
]
```

#### 5. `excludePatterns` (Whitelist/Exclusion Rules)

**Very Important**. Logs matching rules here will **not** be filtered, even if other rules match them. Used to retain critical errors.
```toml
excludePatterns = [
    ".*CRITICAL ERROR.*",  # Even if it matches Exception, if it is a CRITICAL ERROR, keep it
    ".*IMPORTANT.*"
]
```

## 🔨 Build

If you want to compile this mod yourself:
```bash
./gradlew build
```
The compiled JAR file is located in the `build/libs/` directory.

---

## Deep Dive

### Part 1: How it works at the mod code level

The core idea of this mod is **interception**. It "hijacks" the data before Minecraft's log output reaches the console or files, deciding whether to let it pass or discard it.

#### 1. Log Framework Basics: Log4j 2

Minecraft 1.20.1 uses the **Log4j 2** logging framework.
*   **Log Flow**: Game code generates log events (`LogEvent`) -> Passes to Log4j's `LoggerContext` -> Passes to `Appender` (e.g., console appender, file writer) -> Finally displayed on the screen.
*   **Filter Mechanism**: Log4j allows mounting "filters (`Filter`)" on Loggers or Appenders.
* **Async Output Pipeline**: all appenders on the root logger are wrapped into a Log4j2 `AsyncAppender` (enabled by default, configurable): console/file I/O runs on a background thread, the logging thread only pays the enqueue cost; queue size and overflow policy are configurable.

#### 2. Code Execution Flow

Our mod performs the following operations via `LogFilterManager.java` when the game starts (`FMLCommonSetupEvent`):
1.  **Get Context**:
    ```java
    LoggerContext loggerContext = LoggerContext.getContext(false);
    ```
    We obtained Minecraft's current log environment context.
    
3.  **Get Root Configuration**:
    ```java
    Configuration configuration = loggerContext.getConfiguration();
    LoggerConfig rootLoggerConfig = configuration.getRootLogger();
    ```
    We got the configuration of the "Root Logger". Since almost all logs eventually flow to the Root Logger, mounting a filter here can capture global logs.
    
5.  **Register Custom Filter**:
    ```java
    log4jFilter = new FilteringLogFilter(); // Our custom filter class
    log4jFilter.start();
    rootLoggerConfig.addFilter(log4jFilter);
    ```
    We inserted our custom `FilteringLogFilter` at the very front of the log processing chain.
    
**7. Interception and Decision Making (`FilteringLogFilter.filter` method):**
    Whenever Minecraft attempts to output a log line, Log4j invokes our `filter(LogEvent event)` method. To prevent game stuttering caused by log filtering, we adopt a "fail-fast" design, with the following logic priority:
    *   **Step A: Extract Key Information.** Directly extract key details such as the Logger name and level from the Log4j `LogEvent`.
    *   **Step B: Quick Check (Level & Logger).** Prioritize checking the log level (`logLevels`) and Logger name (`loggerNames`). This is an extremely low-overhead operation. If a match is found, the log is discarded immediately, **completely skipping the expensive message formatting and regex matching that follows**, significantly reducing main thread overhead.
    *   **Step C: Message Formatting.** Only when a log passes the quick check and is not intercepted will `getFormattedMessage()` be called to generate the message text.
    *   **Step D: Cache Check.** Calculate the hash value of the log content to check if it exists in the cache. If it does, return `DENY` immediately to avoid redundant computation.
    *   **Step E: Whitelist Check (`excludePatterns`).** If the log matches a whitelist regex, return `NEUTRAL`. The whitelist has the highest priority and is used to protect important logs.
    *   **Step F: Exact Match Check (`exactMatches`).** If the log message matches a configured string exactly, return `DENY`.
    *   **Step G: Regex Rule Check (`filterRules`).** If the log message matches a configured regular expression, return `DENY`.

    
8.  **Final Verdict**:
    *   If any of the above interception conditions match, we return `Result.DENY`. Upon receiving `DENY`, Log4j immediately discards the log, and it will not appear in the console or file.
    *   If none match, we return `Result.NEUTRAL`. Log4j considers the filter to have "no opinion" and continues to pass the log to the next processor, eventually displaying it normally.
9. **Config Hot-Reload**: after you edit and save `logfilter-common.toml`, Forge's config file watcher immediately fires the reload event: the async pipeline rebuilds idempotently (skipped if parameters unchanged), filter rules recompile, invalid regexes are skipped with warnings — no game restart needed, all event-driven.

---

### Part 2: Practical Case Analysis and Configuration

Let's look at this log:
```text
[241... 00:32:22.574] [Render thread/WARN] [net.minecraft.client.renderer.ShaderInstance/]: Shader rendertype_entity_translucent_emissive could not find sampler named Sampler2 in the specified shader program.
```

#### 1. Log Entry Dissection

We need to break down this log into several key parts:
| Log Fragment | Meaning | Corresponding Config Item | Note |
| :--- | :--- | :--- | :--- |
| `WARN` | **Log Level** | `logLevels` | Indicates this is a warning, not an error or info. |
| `net.minecraft.client.renderer.ShaderInstance` | **Logger Name** | `loggerNames` | The fully qualified name of the Java class that emitted this log. |
| `Shader ... program.` | **Log Message** | `filterRules`, `exactMatches` | The specific error content. |

#### 2. How to determine which configuration item it belongs to?

*   If you want to **block all messages emitted by a certain class/mod** (e.g., I'm tired of seeing all errors from this Shader class), you should use **`loggerNames`**.
*   If you want to **block all messages of a certain level** (e.g., I don't want to see any WARN level messages), you should use **`logLevels`**.
*   If you want to **precisely block this one sentence** (even if it only appears once), you should use **`exactMatches`**.
*   If you want to **fuzzily block this type of error** (e.g., regardless of whether it can't find Sampler1 or Sampler2, or which Shader it is, just block it if it's "cannot find sampler"), you should use **`filterRules`**.

#### 3. Practical Configuration Solutions

For this specific Shader error, we have four filtering strategies. Please choose according to your needs:

##### Solution A: Precisely filter this error (Recommended - Use `filterRules`)

The core feature of this log is "cannot find sampler". We can write a regular expression that covers all cases where a sampler is not found, without affecting other logs.
**Applicable Scenario**: You consider all warnings about missing shader samplers to be irrelevant and want to block them all.  
**Configuration Code**:
```toml
# Explanation: .* means any character, matching all logs containing "could not find sampler named", regardless of what comes before or after.
	filterRules = [".*could not find sampler named.*"]
```

##### Solution B: Only block this specific long sentence (Use `exactMatches`)

If you only want to block the specific warning about `Sampler2` not being found, but keep other Shader warnings.
**Applicable Scenario**: Extremely precise strike, no collateral damage.  
**Configuration Code**:
```toml
# Must be exactly the same as the text in the log (usually excluding timestamp and thread name, only including the content after the colon)
	exactMatches = ["Shader rendertype_entity_translucent_emissive could not find sampler named Sampler2 in the specified shader program."]
```

##### Solution C: Block all logs from the ShaderInstance class (Use `loggerNames`)

If the `ShaderInstance` class is very noisy and you don't want to see anything it outputs at all.
**Applicable Scenario**: Aggressive filtering. **Warning**: If this class outputs serious error logs later, you won't see them either.  
**Configuration Code**:
```toml
# As long as the log source is net.minecraft.client.renderer.ShaderInstance, block everything.
	loggerNames = ["net.minecraft.client.renderer.ShaderInstance"]
```

##### Solution D: Block all WARN level logs (Use `logLevels`)

**Applicable Scenario**: **Not Recommended**. This will block all warning messages, potentially causing you to miss potential issues that really need attention.  
**Configuration Code**:
```toml
	logLevels = ["WARN"]
```

