//? fabric {
package com.lmaxrouter.logfilter.platform.fabric;

import com.lmaxrouter.logfilter.LogFilterBootstrap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric 主入口 (fabric.mod.json "main" entrypoint, 由 build-logic 生成注册)。
 * [Stonecutter 迁移] 替代原 Forge LogFilterMod 构造器: 全部启动逻辑在
 * LogFilterBootstrap.init, 平台层只剩"提供 config 目录"一件事。
 * [Stonecutter 条件块] 整文件包进 //? fabric {}: neoforge 节点编译时本文件被剔除为空,
 * 避免 fabric-api import 在 NF classpath 上炸编译 (模板 platform/fabric/* 同款写法)。
 */
public class FabricEntrypoint implements ModInitializer {
    @Override
    public void onInitialize() {
        LogFilterBootstrap.init(FabricLoader.getInstance().getConfigDir());
    }
}
//?}