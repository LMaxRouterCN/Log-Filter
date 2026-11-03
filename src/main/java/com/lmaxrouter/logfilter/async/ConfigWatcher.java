package com.lmaxrouter.logfilter.async;

import com.lmaxrouter.logfilter.Constants;
import com.lmaxrouter.logfilter.config.LogFilterConfig;
import com.lmaxrouter.logfilter.filter.LogFilterManager;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
// [2026-11-03 构建修复] 迁移期缺失的跨包 import: async 包引用 config 包的 ConfigIssues
// (javac: ConfigWatcher.java:113 找不到符号 变量 ConfigIssues, 三个 fabric 节点同报; 其余引用方均有此 import)
import com.lmaxrouter.logfilter.config.ConfigIssues;

/**
 * 配置文件监听器 (v2 热重载, Stonecutter 迁移新增件)。
 *
 * 替代原 Forge 的 ModConfigEvent.Reloading 事件源:
 * Forge 由 FML 的配置文件监听驱动, fabric/NeoForge 平台上无等价免费机制,
 * 故自建 WatchService 守护线程直接监听文件系统事件。
 *
 * 事件链: 文件修改(take 阻塞等待, 纯事件驱动零轮询) ->
 * LogFilterConfig.reload()(重解析 TOML, 坏文件保持旧配置) ->
 * AsyncLogPipeline.apply(幂等签名短路) + LogFilterManager.reload()(过滤规则热生效) ->
 * ConfigIssues.requestDisplay()(客户端弹聊天框)。
 *
 * 防抖语义: Windows 一次保存常连发多条事件, 本类不 sleep 防抖
 * (守护线程内硬编码延时违背零延时原则), 依靠下游幂等性吸收:
 * 批内用 poll() 抽干合并为一次处理, 跨批重复触发只产生冗余 FilterConfig 构造,
 * 数量级为微秒, 无功能影响。
 *
 * 自触发回环: 本 Mod 运行期从不写配置文件(只读), 不存在"自己写盘唤醒自己"的死循环;
 * 首建默认文件的写盘发生在 start() 之前, 也无回环。
 */
public final class ConfigWatcher {

    /** 监听线程; null = 未启动。start 幂等 */
    private static volatile Thread thread;

    private ConfigWatcher() {
    }

    /** 启动监听(幂等); 只应由 LogFilterBootstrap.init 在启动链末尾调用 */
    public static synchronized void start(Path configDir) {
        if (thread != null) {
            return;
        }
        try {
            // ENTRY_MODIFY: 常规编辑保存; ENTRY_CREATE: 覆盖部分编辑器写临时文件+rename 的保存方式
            WatchService service = FileSystems.getDefault().newWatchService();
            configDir.register(service,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            Thread t = new Thread(() -> loop(service), "logfilter-config-watcher");
            t.setDaemon(true); // 不阻止 JVM 退出(与 mod 生命周期无绑定, 退出即终)
            t.start();
            thread = t;
        } catch (IOException e) {
            // 监听失败只损失热重载, 主功能(过滤/异步)不受影响: 降级为 WARN 而非崩溃
            Constants.LOGGER.warn("[config] 目录监听启动失败, 热重载不可用: {}", e.toString());
        }
    }

    /** 守护线程主循环: take() 纯阻塞等待事件, 无轮询 */
    private static void loop(WatchService service) {
        while (true) {
            WatchKey key;
            try {
                key = service.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return; // 服务关闭/线程中断: 退出监听(守护线程, JVM 退出场景)
            }

            boolean hit = false;
            // 批内抽干: 一次唤醒的积压事件合并处理, 只关心本 Mod 的配置文件
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    // 事件队列溢出(监听端处理不过来): 保守视为命中, 触发一次重载校准状态
                    hit = true;
                    continue;
                }
                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                if (pathEvent.context().toString().equals(LogFilterConfig.CONFIG_FILE_NAME)) {
                    hit = true;
                }
            }

            boolean valid = key.reset(); // 重置以继续接收后续事件
            if (valid && hit) {
                try {
                    onConfigChanged();
                } catch (Throwable t) {
                    // 兜底: 任何异常不许炸死监听线程(炸了热重载就永久失效)
                    Constants.LOGGER.error("[config] 热重载处理异常", t);
                }
            } else if (!valid) {
                return; // 目录本身失效(被删): 退出
            }
        }
    }

    /** 文件事件到达: 等价原 Forge ModConfigEvent.Reloading 的回调体 */
    private static void onConfigChanged() {
        LogFilterConfig.reload();
        // AsyncLogPipeline.apply 幂等: 参数签名未变则什么都不做, 变了才事务性重建管线
        AsyncLogPipeline.apply(AsyncPipelineConfig.snapshot());
        // [长期记忆: 001] 复活 LogFilterManager.reload() 死代码(原接线点为 Forge AsyncPipelineEvents.onConfigReloading):
        // 过滤配置随文件事件同步刷新, 从此"改配置文件不生效必须重启游戏"的坑被填掉
        LogFilterManager.reload();
        // 坏正则等问题的客户端反馈(服务端 no-op)
        ConfigIssues.requestDisplay();
    }
}
