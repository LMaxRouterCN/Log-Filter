package com.lmaxrouter.logfilter;

import com.lmaxrouter.logfilter.async.AsyncLogPipeline;
import com.lmaxrouter.logfilter.async.AsyncPipelineConfig;
import com.lmaxrouter.logfilter.async.ConfigWatcher;
import com.lmaxrouter.logfilter.config.LogFilterConfig;
import com.lmaxrouter.logfilter.filter.LogFilterManager;

import java.nio.file.Path;

/**
 * 跨平台共享引导 (Stonecutter 迁移新增件)。
 *
 * 职责: 把原 Forge 入口类 LogFilterMod 构造器 + ModConfigEvent.Loading +
 * FMLCommonSetupEvent 三段式启动链, 压平为一个平台无关的 init:
 *
 *   1) LogFilterConfig.init   —— 打开/首建 TOML(等价 Forge 配置文件加载)
 *   2) AsyncLogPipeline.apply —— 按快照装配异步管线(等价 ModConfigEvent.Loading 回调)
 *   3) LogFilterManager.initialize —— 挂 root logger 过滤器(等价 FMLCommonSetupEvent 回调)
 *   4) ConfigWatcher.start    —— 起文件监听(等价 Forge FML 文件监听 / 后续 Reloading 事件源)
 *
 * 各平台入口(fabric ModInitializer / NeoForge @Mod 构造器)只做一件事:
 * 传入平台 config 目录, 调用本方法。此后一切变更由 ConfigWatcher 事件链驱动。
 */
public final class LogFilterBootstrap {

    private LogFilterBootstrap() {
    }

    /** 唯一入口: mod 生命周期最早期调用(两加载器入口的等价位置) */
    public static void init(Path configDir) {
        LogFilterConfig.init(configDir);
        AsyncLogPipeline.apply(AsyncPipelineConfig.snapshot());
        LogFilterManager.initialize();
        ConfigWatcher.start(configDir);
        Constants.LOGGER.info("Log Filter Mod loaded!");
    }
}