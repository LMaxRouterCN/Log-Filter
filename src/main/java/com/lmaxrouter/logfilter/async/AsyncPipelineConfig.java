package com.lmaxrouter.logfilter.async;

import com.lmaxrouter.logfilter.config.LogFilterConfig;

/**
 * 异步输出管线的不可变配置快照 (v2 异步化改造)。
 *
 * 与 FilterConfig 采用同一模式: 在配置事件时刻从配置读取并冻结为只读对象,
 * 运行期零配置读取、天然线程安全。
 * 额外提供配置签名, 用于热重载时的幂等判断:
 * 签名未变则不重建管线, 避免无意义的队列切换与短暂的双份输出窗口。
 *
 * [Stonecutter 迁移] snapshot() 的读取源由 Forge ModConfig 换成 LogFilterConfig(NightConfig),
 * 语义不变: 只应在 Bootstrap 启动链 / ConfigWatcher 文件事件回调中调用,
 * 此刻 TOML 已解析完成, 不存在时序问题。
 */
public final class AsyncPipelineConfig {

    private final boolean enabled;
    private final int queueSize;
    private final boolean blockingWhenFull;
    private final long shutdownTimeoutMs;
    private final boolean includeLocation;
    private final String queueFactoryClass;

    private AsyncPipelineConfig(boolean enabled, int queueSize, boolean blockingWhenFull,
                                long shutdownTimeoutMs, boolean includeLocation,
                                String queueFactoryClass) {
        this.enabled = enabled;
        this.queueSize = queueSize;
        this.blockingWhenFull = blockingWhenFull;
        this.shutdownTimeoutMs = shutdownTimeoutMs;
        this.includeLocation = includeLocation;
        // 规范化: 去除首尾空白, 统一空串语义, 避免用户配置里带空格导致类名解析失败
        this.queueFactoryClass = queueFactoryClass == null ? "" : queueFactoryClass.trim();
    }

    /** 从当前配置文件读取一份快照。 */
    public static AsyncPipelineConfig snapshot() {
        return new AsyncPipelineConfig(
                LogFilterConfig.asyncEnabled(),
                LogFilterConfig.asyncQueueSize(),
                LogFilterConfig.asyncBlockingWhenFull(),
                LogFilterConfig.asyncShutdownTimeoutMs(),
                LogFilterConfig.asyncIncludeLocation(),
                LogFilterConfig.asyncQueueFactoryClass());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public boolean isBlockingWhenFull() {
        return blockingWhenFull;
    }

    public long getShutdownTimeoutMs() {
        return shutdownTimeoutMs;
    }

    public boolean isIncludeLocation() {
        return includeLocation;
    }

    /**
     * 自定义 BlockingQueueFactory 的全限定类名。
     * 空串 = 使用 Log4j2 默认的 ArrayBlockingQueue 实现 (零依赖, 开箱即用)。
     * 非空 = 反射实例化并注入 —— 这是预留的升级钩子:
     * 未来若引入 Disruptor (JarJar 方式), 只需在配置里填入对应工厂类名,
     * 管线外壳与安装逻辑零改动。
     */
    public String getQueueFactoryClass() {
        return queueFactoryClass;
    }

    /**
     * 配置签名: 所有影响管线形态的参数按序拼接。
     * 用于热重载事件的幂等判断 (签名相同 => 参数未变 => 不重建管线)。
     * 包内可见, 不进入对外 API。
     */
    String signature() {
        return enabled + "|" + queueSize + "|" + blockingWhenFull + "|"
                + shutdownTimeoutMs + "|" + includeLocation + "|" + queueFactoryClass;
    }

    @Override
    public String toString() {
        return "AsyncPipelineConfig{" + signature() + "}";
    }
}