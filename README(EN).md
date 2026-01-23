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
4. Modify the configuration file as needed, then **restart the game** for the configuration to take effect.
> Or manually create a `logfilter-common.toml` file, modify the configuration as needed, and then launch the game.

## Configuration Brief

The configuration file is located at: `config/logfilter-common.toml`
Default configuration:
```toml
[General]
	#Enable or disable log filtering
	enableFilter = true
	#Enable debug mode to see which logs are being filtered
	debugMode = false
	#Maximum size of filtered log cache (for duplicate detection)
	#Range: 100 ~ 10000
	maxCacheSize = 1000
[FilterRules]
	#Regex patterns to filter log messages
	filterRules = []
	#Exact message strings to filter (case-sensitive)
	exactMatches = []
	#Logger names to completely filter (e.g., 'net.minecraft.server.MinecraftServer')
	loggerNames = []
	#Log levels to filter: TRACE, DEBUG, INFO, WARN, ERROR, FATAL
	logLevels = ["TRACE", "DEBUG"]
	#Regex patterns that will EXCLUDE logs from filtering (whitelist)
	excludePatterns = []
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

### Configuration Examples

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
    
7.  **Interception and Decision (`FilteringLogFilter.filter` method)**:
    Whenever Minecraft attempts to output a line of log, Log4j calls our `filter(LogEvent event)` method. Here we perform the following judgment logic (priority from high to low):
    *   **Step A: Parse Event**. Convert Log4j's `LogEvent` into our custom defined `LogEntry` object for easier processing.
    *   **Step B: Cache Check**. Calculate the hash value of the log content and see if this log exists in the cache. If yes, return `DENY` immediately without subsequent calculation. This is for performance optimization, preventing CPU overload due to the same error spamming the screen.
    *   **Step C: Whitelist Check (`excludePatterns`)**. If the log matches a whitelist regex, return `NEUTRAL` (neutral/pass). The whitelist has the highest priority and is used to protect important logs.
    *   **Step D: Logger Name Check (`loggerNames`)**. If the log's source class name is in the blacklist, return `DENY`.
    *   **Step E: Log Level Check (`logLevels`)**. If the log level (e.g., `DEBUG`) is in the blacklist, return `DENY`.
    *   **Step F: Exact Match Check (`exactMatches`)**. If the log message exactly matches a configured string, return `DENY`.
    *   **Step G: Regex Rule Check (`filterRules`)**. If the log message matches a configured regex, return `DENY`.
    
8.  **Final Verdict**:
    *   If any of the above interception conditions match, we return `Result.DENY`. Upon receiving `DENY`, Log4j immediately discards the log, and it will not appear in the console or file.
    *   If none match, we return `Result.NEUTRAL`. Log4j considers the filter to have "no opinion" and continues to pass the log to the next processor, eventually displaying it normally.

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
