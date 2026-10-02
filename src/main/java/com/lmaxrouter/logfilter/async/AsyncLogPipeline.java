package com.lmaxrouter.logfilter.async;

import com.lmaxrouter.logfilter.LogFilterMod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
// LogEvent: 仅用于把自定义队列工厂的泛型实参固定为 <LogEvent>(与 Builder.setBlockingQueueFactory 的形参类型精确匹配)
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.async.BlockingQueueFactory;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 异步输出管线安装器 (v2 异步化改造核心)。
 *
 * 职责边界: 本类只做"appender 链的装配与拆除"(调度与生命周期),
 * 不掺任何业务特化逻辑——包装哪些 appender、队列多大、满时策略等全部来自 AsyncPipelineConfig。
 *
 * 工作原理: 把 root logger 现有的全部 appender 摘下, 换成一个 Log4j2 自带的
 * AsyncAppender(单队列单消费者, 事件全序), 原有 appender 仍在 configuration
 * registry 中保持 started 状态, 由 AsyncAppender 按名字引用分发。
 * 打日志线程只剩"过滤裁决 + 入队", 控制台渲染与文件 I/O 全部移到后台消费线程。
 *
 * 线程模型: 所有 install/uninstall 被 LOCK 串行化(配置事件可能在不同线程触发);
 * 状态字段 volatile, 保证任意线程读到一致的安装状态。
 * Log4j2 官方的运行时修改模式即 "改 LoggerConfig -> updateLoggers 原子提交"。
 */
public final class AsyncLogPipeline {

    /** 异步 Appender 在 Log4j 注册表中的固定名字; 重建时覆盖旧条目 */
    private static final String ASYNC_APPENDER_NAME = "LogFilterAsyncAppender";

    /** 当前已安装的 AsyncAppender; null = 未启用异步。volatile: 可能被任意线程读取 */
    private static volatile AsyncAppender installed;
    /** 已安装管线对应的配置签名(幂等判断: 签名未变则不重建, 避免无意义的队列切换) */
    private static volatile String installedSignature;
    /** 已安装管线使用的配置快照(卸载时需要其中的排干超时参数) */
    private static volatile AsyncPipelineConfig installedConfig;
    /** JVM 关闭钩子: 进程退出前排干队列, 等待上限 = 配置的 shutdownTimeoutMs */
    private static volatile Thread shutdownHook;
    /** 串行化所有安装/卸载操作, 保证并发配置事件下的原子性 */
    private static final Object LOCK = new Object();

    private AsyncLogPipeline() {
    }

    /**
     * 应用一份异步管线配置(幂等, 线程安全)。
     * 只应在 ModConfigEvent.Loading / Reloading 事件中调用——此时 Forge 配置值 100% 已加载。
     */
    public static void apply(AsyncPipelineConfig config) {
        synchronized (LOCK) {
            String signature = config.signature();
            boolean isInstalled = installed != null;

            // 幂等短路 1: 配置禁用且当前无管线 -> 什么都不做
            if (!config.isEnabled() && !isInstalled) {
                return;
            }
            // 幂等短路 2: 配置启用且管线已在且签名完全一致 -> 不重建
            if (config.isEnabled() && isInstalled && signature.equals(installedSignature)) {
                return;
            }

            if (!config.isEnabled()) {
                uninstall("配置禁用了异步输出");
                return;
            }

            // 启用状态下参数变化: 先卸载旧管线(含队列排干), 再安装新管线
            if (isInstalled) {
                uninstall("异步管线参数变更, 重建");
            }
            install(config, signature);
        }
    }

    // ==================== 安装 ====================

    private static void install(AsyncPipelineConfig config, String signature) {
        // getContext(false): 取当前已被 MC 配置好的 LoggerContext, 不新建
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        LoggerConfig rootLoggerConfig = configuration.getRootLogger();

        // 快照 root 现有 appender(事务回滚依据)
        Map<String, Appender> attachedAppenders = rootLoggerConfig.getAppenders();
        if (attachedAppenders.isEmpty()) {
            LogFilterMod.LOGGER.warn("[async] root logger 没有任何 appender, 无可包装, 跳过安装");
            return;
        }
        // 优先复用 root 已配置的 AppenderRef 列表(自带每个 appender 的 level/filter 语义),
        // 保证包装后分发行为与原同步链完全一致; refs 缺失时才按 appender 名补建(null 级别 = 全放行)
        List<AppenderRef> originalRefs = rootLoggerConfig.getAppenderRefs();
        AppenderRef[] refs = (originalRefs != null && !originalRefs.isEmpty())
                ? originalRefs.toArray(new AppenderRef[0])
                : attachedAppenders.keySet().stream()
                        .map(name -> AppenderRef.createAppenderRef(name, null, null))
                        .toArray(AppenderRef[]::new);

        // 构建 AsyncAppender(逐条 setter 不链式, 便于对照 javap 验证过的 API 逐行注释)
        AsyncAppender.Builder<?> builder = AsyncAppender.newBuilder();
        builder.setName(ASYNC_APPENDER_NAME);
        builder.setAppenderRefs(refs);
        // 队列满策略: 阻塞(不丢日志) 或 丢弃(不阻塞打日志线程)
        builder.setBlocking(config.isBlockingWhenFull());
        builder.setBufferSize(config.getQueueSize());
        builder.setShutdownTimeout(config.getShutdownTimeoutMs());
        // 调用位置栈帧采集默认关闭(每事件一次栈行走, 代价极高; MC 默认 pattern 不需要)
        builder.setIncludeLocation(config.isIncludeLocation());
        // 分发异常不反噬打日志线程
        builder.setIgnoreExceptions(true);
        // AsyncAppender 在 start 时按 ref 名字从该 registry 解析真实 appender
        builder.setConfiguration(configuration);

        // 可插拔队列工厂(升级钩子): 配置非空 = 反射实例化自定义 BlockingQueueFactory
        // (未来 JarJar 注入 Disruptor 后填入工厂类名即可, 外壳零改动); 失败回退默认 ArrayBlockingQueue 不中止安装
        if (!config.getQueueFactoryClass().isEmpty()) {
                        BlockingQueueFactory<LogEvent> factory = createQueueFactory(config.getQueueFactoryClass());
            if (factory != null) {
                builder.setBlockingQueueFactory(factory);
                LogFilterMod.LOGGER.info("[async] 使用自定义队列工厂: {}", config.getQueueFactoryClass());
            }
        }

        AsyncAppender async = builder.build();

        // --- 事务性安装: 任何一步失败都恢复原 appender 链, 不留半成品 ---
        async.start();
        try {
            // 1) 注册进 configuration 的 appender registry(覆盖同名旧条目, 供 Log4j 生命周期管理)
            configuration.addAppender(async);
            // 2) 从 root 摘下原有 appender(仅从 LoggerConfig 摘除, registry 中仍保留且 started,
            //    因此 AsyncAppender 仍能按名字向它们分发, 不丢日志)
            for (String name : attachedAppenders.keySet()) {
                rootLoggerConfig.removeAppender(name);
            }
            // 3) root 挂上异步 appender(null 级别 = 不额外限级, 级别语义已由 refs 保留)
            rootLoggerConfig.addAppender(async, null, null);
            // 4) 原子提交: updateLoggers 让所有 Logger 感知新 appender 链
            context.updateLoggers();
        } catch (Throwable t) {
            // 回滚: 摘下异步, 按快照恢复原有链, 重新提交
            try {
                rootLoggerConfig.removeAppender(ASYNC_APPENDER_NAME);
                for (Map.Entry<String, Appender> entry : attachedAppenders.entrySet()) {
                    rootLoggerConfig.addAppender(entry.getValue(), null, null);
                }
                context.updateLoggers();
            } catch (Throwable rollbackFailure) {
                LogFilterMod.LOGGER.error("[async] 事务回滚也失败了, 日志输出链处于未知状态!", rollbackFailure);
            }
            // 停掉尚未提交的异步 appender(stop 幂等; 已停止的条目留在 registry 中无害,
            // 后续重建会以同名覆盖; Log4j 无公开的 registry 移除 API)
            async.stop(config.getShutdownTimeoutMs(), TimeUnit.MILLISECONDS);
            LogFilterMod.LOGGER.error("[async] 异步管线安装失败, 已回滚到同步输出", t);
            return;
        }

        installed = async;
        installedSignature = signature;
        installedConfig = config;
        registerShutdownHook(config);
        LogFilterMod.LOGGER.info(
                "[async] 异步日志输出已启用: queueSize={}, blockingWhenFull={}, queueFactory={}, 包装了 {} 个 appender: {}",
                config.getQueueSize(), config.isBlockingWhenFull(),
                config.getQueueFactoryClass().isEmpty() ? "(default)" : config.getQueueFactoryClass(),
                attachedAppenders.size(), attachedAppenders.keySet());
    }

    // ==================== 卸载 ====================

    private static void uninstall(String reason) {
        AsyncAppender async = installed;
        AsyncPipelineConfig config = installedConfig;
        if (async == null) {
            return;
        }
        unregisterShutdownHook();

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        LoggerConfig rootLoggerConfig = configuration.getRootLogger();

        // 1) 先把异步 appender 从 root 摘下 -> 新日志立即走同步路径
        rootLoggerConfig.removeAppender(ASYNC_APPENDER_NAME);
        context.updateLoggers();

        // 2) 停止异步 appender: 内部按 shutdownTimeout 排干队列,
        //    队列残留事件仍会分发到 registry 中 started 状态的原 appender(不丢失)
        async.stop(config != null ? config.getShutdownTimeoutMs() : 5000L, TimeUnit.MILLISECONDS);

        // 3) 按 ref 名字把原 appender 挂回 root
        for (String ref : async.getAppenderRefStrings()) {
            Appender original = configuration.getAppender(ref);
            if (original != null) {
                rootLoggerConfig.addAppender(original, null, null);
            }
        }
        context.updateLoggers();

        installed = null;
        installedSignature = null;
        installedConfig = null;
        LogFilterMod.LOGGER.info("[async] 异步日志输出已停用({}), 已恢复同步输出链", reason);
    }

    // ==================== 可插拔队列工厂(升级钩子) ====================

    @SuppressWarnings("unchecked")
        private static BlockingQueueFactory<LogEvent> createQueueFactory(String className) {
            try {
                Class<?> type = Class.forName(className, true, AsyncLogPipeline.class.getClassLoader());
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                // 泛型擦除后强转安全: 合法实现类运行期创建的就是 Queue<LogEvent>;
                // 若直接返回 BlockingQueueFactory<?>, 无法传入形参类型为 BlockingQueueFactory<LogEvent> 的 setter
                return (BlockingQueueFactory<LogEvent>) constructor.newInstance();
        } catch (Throwable t) {
            LogFilterMod.LOGGER.error("[async] 自定义队列工厂 '{}' 加载失败, 回退为默认 ArrayBlockingQueue", className, t);
            return null;
        }
    }

    // ==================== JVM 关闭钩子 ====================

    private static void registerShutdownHook(AsyncPipelineConfig config) {
        Thread hook = new Thread(() -> {
            AsyncAppender async = installed;
            if (async != null) {
                // JVM 退出前排干队列; stop 幂等, 与 Log4j 自身的生命周期停止不冲突
                async.stop(config.getShutdownTimeoutMs(), TimeUnit.MILLISECONDS);
            }
        }, "logfilter-async-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        shutdownHook = hook;
    }

    private static void unregisterShutdownHook() {
        Thread hook = shutdownHook;
        shutdownHook = null;
        if (hook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // JVM 已在关闭流程中无法移除钩子, 无害(钩子体内的 stop 幂等)
            }
        }
    }
}