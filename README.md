# Log Filter

<img height="300" alt="pack" src="https://github.com/user-attachments/assets/ec3abd11-e1e3-47df-a55b-9392fd4b9bb7" />

一个轻量级日志过滤模组，旨在减少控制台和日志文件中的垃圾信息，让开发者和玩家能更专注于关键错误，并节省硬盘空间。

## 特性

- **正则表达式过滤**：支持强大的正则表达式匹配，精准过滤特定内容的日志。
- **完全匹配过滤**：可以精准过滤掉指定的整条文本信息。
- **按日志记录器过滤**：支持按模组或类名（Logger Name）过滤整个模组的日志。
- **按日志等级过滤**：一键屏蔽 `TRACE` 或 `DEBUG` 等低级日志。
  > 屏蔽`INFO`日志？当然可以！这样你只会看到报错和警告，适用于调试。
- **白名单模式**：设置排除规则，确保重要日志（如严重错误）不会被误删。
- **高效缓存**：内置去重缓存机制，避免重复处理相同的日志。

## 安装

1. 下载模组的 JAR 文件。
2. 将 JAR 文件放入你的 Minecraft 安装目录下的 `mods` 文件夹中。
3. 启动游戏一次。模组会自动生成默认配置文件。
4. 根据需要修改配置文件，然后**重启游戏**使配置生效。

>或手动新建`logfilter-common.toml`文件，按需修改配置文件后启动游戏即可。

## 配置简略说明

配置文件位于：`config/logfilter-common.toml`

默认配置:
```
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


### General (通用设置)

| 选项 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `enableFilter` | `true` | 是否启用日志过滤功能。 |
| `debugMode` | `false` | 启用调试模式，会在控制台打印被过滤掉的日志，方便测试规则。 |
| `maxCacheSize` | `1000` | 过滤日志的缓存大小，用于去重。 |

### FilterRules (过滤规则)

#### 1. `filterRules` (正则表达式)

使用正则表达式匹配要过滤的日志。
```toml
filterRules = [
    ".*Exception.*",          # 过滤掉所有包含 "Exception" 的日志
    ".*stack trace.*",        # 过滤掉堆栈跟踪
    ".*Could not pass event.*"# 过滤掉特定事件报错
]
```

#### 2. `exactMatches` (完全匹配)

只有当日志内容**完全一致**时才会被过滤（区分大小写）。
```toml
exactMatches = [
    "This message will be completely filtered out"
]
```

#### 3. `loggerNames` (记录器名称)

根据日志的来源（类名或模组包名）进行过滤。支持层级过滤。
```toml
loggerNames = [
    "net.minecraft.server.MinecraftServer",  # 过滤掉主服务器的日志
    "com.some.noisy.mod"                     # 过滤掉某个吵闹的模组的所有日志
]
```

#### 4. `logLevels` (日志等级)

根据日志级别进行过滤。可选值：`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`。
```toml
logLevels = [
    "TRACE",
    "DEBUG"
]
```

#### 5. `excludePatterns` (白名单/排除规则)

**非常重要**。此处的规则匹配的日志将**不会**被过滤，即使其他规则匹配到了它。用于保留关键错误。
```toml
excludePatterns = [
    ".*CRITICAL ERROR.*",  # 即使匹配了 Exception，但如果是 CRITICAL ERROR，则保留
    ".*IMPORTANT.*"
]
```

### 配置示例




## 🔨 构建

如果你想要自己编译此模组：
```bash
./gradlew build
```
编译后的 JAR 文件位于 `build/libs/` 目录。

---

## 深入了解


### 第一部分：模组代码层面的工作原理

这个模组的核心思想是**拦截**。它在 Minecraft 的日志输出到达控制台或文件之前，提前“劫持”了这些数据，决定是放行还是丢弃。

#### 1. 日志框架基础：Log4j 2

Minecraft 1.20.1 使用的是 **Log4j 2** 日志框架。
*   **日志流向**: 游戏代码产生日志事件 (`LogEvent`) -> 传递给 Log4j 的 `LoggerContext` -> 传递给 `Appender`（如控制台输出器、文件写入器） -> 最终显示在屏幕上。
*   **Filter 机制**: Log4j 允许在 Logger 或 Appender 上挂载“过滤器 (`Filter`)”。

#### 2. 代码执行流程

我们的模组通过 `LogFilterManager.java` 在游戏启动时 (`FMLCommonSetupEvent`) 执行以下操作：

1.  **获取上下文**:
    ```java
    LoggerContext loggerContext = LoggerContext.getContext(false);
    ```
    我们拿到了 Minecraft 当前的日志环境上下文。
    
3.  **获取根配置**:
    ```java
    Configuration configuration = loggerContext.getConfiguration();
    LoggerConfig rootLoggerConfig = configuration.getRootLogger();
    ```
    我们拿到了“根 Logger”的配置。因为几乎所有日志最终都会流向根 Logger，所以在这里挂载过滤器可以捕获全局日志。
    
5.  **注册自定义过滤器**:
    ```java
    log4jFilter = new FilteringLogFilter(); // 我们自定义的过滤器类
    log4jFilter.start();
    rootLoggerConfig.addFilter(log4jFilter);
    ```
    我们将自定义的 `FilteringLogFilter` 插入到了日志处理链的最前端。
    
7.  **拦截与决策 (`FilteringLogFilter.filter` 方法)**:
    每当 Minecraft 试图输出一行日志时，Log4j 会调用我们的 `filter(LogEvent event)` 方法。在这里我们做了以下判断逻辑（优先级从高到低）：
    *   **步骤 A：解析事件**。将 Log4j 的 `LogEvent` 转换成我们自己定义的 `LogEntry` 对象，方便处理。
    *   **步骤 B：缓存检查**。计算日志内容的哈希值，看缓存里有没有这条日志。如果有，直接返回 `DENY`（拒绝），不进行后续计算。这是为了性能优化，防止同一条报错刷屏导致 CPU 占用过高。
    *   **步骤 C：白名单检查 (`excludePatterns`)**。如果日志匹配了白名单正则，返回 `NEUTRAL`（中立/放行）。白名单优先级最高，用于保护重要日志。
    *   **步骤 D：Logger 名称检查 (`loggerNames`)**。如果日志的来源类名在配置的黑名单中，返回 `DENY`。
    *   **步骤 E：日志等级检查 (`logLevels`)**。如果日志级别（如 `DEBUG`）在配置的黑名单中，返回 `DENY`。
    *   **步骤 F：完全匹配检查 (`exactMatches`)**。如果日志消息与配置的字符串完全一致，返回 `DENY`。
    *   **步骤 G：正则规则检查 (`filterRules`)**。如果日志消息匹配了配置的正则表达式，返回 `DENY`。
    
8.  **最终裁决**:
    *   如果上述任何拦截条件匹配，我们返回 `Result.DENY`。Log4j 收到 `DENY` 后，会立即丢弃该日志，控制台和文件都不会显示。
    *   如果都没匹配，我们返回 `Result.NEUTRAL`。Log4j 认为过滤器“没意见”，继续将日志传给下一个处理器，最终正常显示。
---

### 第二部分：实战案例解析与配置

让我们来看看这条日志：
```text
[241... 00:32:22.574] [Render thread/WARN] [net.minecraft.client.renderer.ShaderInstance/]: Shader rendertype_entity_translucent_emissive could not find sampler named Sampler2 in the specified shader program.
```

#### 1. 日志条目解剖

我们需要把这条日志拆解成几个关键部分：

| 日志片段 | 含义 | 对应配置项 | 备注 |
| :--- | :--- | :--- | :--- |
| `WARN` | **日志等级** | `logLevels` | 表示这是一条警告，不是错误也不是信息。 |
| `net.minecraft.client.renderer.ShaderInstance` | **Logger 名称 (记录器名称)** | `loggerNames` | 发出这条日志的 Java 类的全限定名。 |
| `Shader ... program.` | **日志消息** | `filterRules`, `exactMatches` | 具体的报错内容。 |

#### 2. 如何判断归属哪个配置项？

*   如果你想**屏蔽某个类/模组发出的所有消息**（例如这个 Shader 类的所有报错我都懒得看），你应该用 **`loggerNames`**。
*   如果你想**屏蔽某种级别的所有消息**（例如我不想看任何 WARN 级别的消息），你应该用 **`logLevels`**。
*   如果你想**精准屏蔽这一句话**（哪怕它只出现一次），你应该用 **`exactMatches`**。
*   如果你想**模糊屏蔽这类报错**（例如不管它找不到的是 Sampler1 还是 Sampler2，或者是哪个 Shader，只要是“找不到采样器”就屏蔽），你应该用 **`filterRules`**。

#### 3. 实战配置方案

针对这条具体的 Shader 报错，我们有四种过滤策略，请根据你的需求选择：

##### 方案 A：精准过滤这条报错 (推荐 - 使用 `filterRules`)

这条日志的核心特征是“找不到 sampler”。我们可以写一个正则表达式，涵盖所有找不到 sampler 的情况，而不影响其他日志。

**适用场景**：你认为所有关于找不到 Shader 采样器的警告都是无关紧要的，想全部屏蔽。  
**配置代码**:
```toml
# 解释：.* 表示任意字符，匹配包含 "could not find sampler named" 的所有日志，不管它的前后有什么
	filterRules = [".*could not find sampler named.*"]
```

##### 方案 B：只屏蔽这一句特定的长文本 (使用 `exactMatches`)

如果你只想屏蔽 `Sampler2` 这个特定找不到的警告，但保留其他 Shader 警告。

**适用场景**：极其精准的打击，不误伤友军。  
**配置代码**:
```toml
# 必须和日志里的文字一模一样（通常不包括时间戳和线程名，只包括冒号后面的内容）
	exactMatches = ["Shader rendertype_entity_translucent_emissive could not find sampler named Sampler2 in the specified shader program."]
```

##### 方案 C：屏蔽 ShaderInstance 这个类的所有日志 (使用 `loggerNames`)

如果 `ShaderInstance` 这个类非常吵，你根本不想看它输出的任何东西。

**适用场景**：激进过滤。**警告**：如果这个类后续输出了严重的错误日志，你也看不到了。  
**配置代码**:
```toml
# 只要日志来源是 net.minecraft.client.renderer.ShaderInstance，全部屏蔽
	loggerNames = ["net.minecraft.client.renderer.ShaderInstance"]
```

##### 方案 D：屏蔽所有 WARN 级别的日志 (使用 `logLevels`)

**适用场景**：**不推荐**。这会屏蔽所有的警告信息，可能导致你错过真正需要关注的潜在问题。  
**配置代码**:
```toml
	logLevels = ["WARN"]
```
