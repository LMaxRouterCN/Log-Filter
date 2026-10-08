package com.lmaxrouter.logfilter.config;

import com.lmaxrouter.logfilter.LogFilterMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 配置问题上报中心: 坏正则等配置问题的唯一汇集点。
 * [长期记忆: 003] 计算与展示完全解耦: report() 只做"log WARN + 入无锁队列"两件事;
 * 展示侧由客户端类通过 setAsyncDisplayHook 注入"投递到主线程"的钩子,
 * 普通代码(服务端/双端)只依赖本类, 不 import 任何客户端类, 服务端零加载风险。
 */
public final class ConfigIssues {

    /** 已上报但尚未在聊天框展示的问题(无锁队列: 配置文件监听线程写, 客户端主线程读) */
    private static final Queue<String> PENDING = new ConcurrentLinkedQueue<>();

    /** 客户端注入的异步展示钩子(内部把 flush 经 Minecraft.execute 投递到主线程); 服务端恒为 null, requestDisplay() 退化为 no-op */
    private static volatile Runnable asyncDisplayHook;

    private ConfigIssues() {
    }

    /**
     * 唯一上报入口: WARN 立即落盘, 聊天框展示延后到有玩家会话时(由展示侧消费)。
     * [长期记忆: 003] 对当前待展示队列去重: 启动期 ModConfigEvent.Loading 与 FMLCommonSetupEvent
     * 会各构造一次 FilterConfig, 同一坏规则避免重复弹行; 冲刷(drain)后可再次上报, 后续 reload 仍能获得反馈。
     */
    public static void report(String issue) {
        LogFilterMod.LOGGER.warn("[LogFilter] Invalid regex in config, skipped: {}", issue);
        if (!PENDING.contains(issue)) {
            PENDING.add(issue);
        }
    }

    /** 客户端专用: 注入展示钩子(见 client.ClientConfigWarnings); 幂等, 后注入覆盖先注入 */
    public static void setAsyncDisplayHook(Runnable hook) {
        asyncDisplayHook = hook;
    }

    /** 配置(重)加载完成后调用: 客户端钩子已就绪则立即请求展示(异步投递主线程), 否则留待 LoggingIn 事件统一冲刷 */
    public static void requestDisplay() {
        Runnable hook = asyncDisplayHook;
        if (hook != null) {
            hook.run();
        }
    }

    /** 展示侧原子取走全部待展示问题; 队列为空则返回空列表, 不阻塞不轮询 */
    public static List<String> drain() {
        List<String> out = new ArrayList<>();
        String s;
        while ((s = PENDING.poll()) != null) {
            out.add(s);
        }
        return out;
    }
}