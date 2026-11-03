package com.lmaxrouter.logfilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 跨平台常量: 替代原 Forge 入口类 LogFilterMod 中的静态字段。
 * 入口类本身被拆到各 platform 包(platform.fabric / platform.neoforge),
 * 核心逻辑(filter/async/config)只依赖本类, 与加载器完全解耦。
 */
public final class Constants {

    public static final String MOD_ID = "logfilter";

    /**
     * Mod 专用 Logger。
     * Log4j2 root 上的 FilteringLogFilter 以 loggerName.startsWith(MOD_ID) 识别
     * 本 Mod 自身日志并直接放行 —— 这是防递归的关键(见 LogFilterManager)。
     */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Constants() {
    }
}