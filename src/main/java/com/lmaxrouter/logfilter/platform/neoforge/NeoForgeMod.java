package com.lmaxrouter.logfilter.platform.neoforge;

import com.lmaxrouter.logfilter.Constants;
import com.lmaxrouter.logfilter.LogFilterBootstrap;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

/**
 * NeoForge 主入口 (neoforge.mods.toml 由 build-logic 生成, modId = Constants.MOD_ID)。
 * [Stonecutter 迁移] 替代原 Forge LogFilterMod 构造器: 全部启动逻辑在
 * LogFilterBootstrap.init, 平台层只剩"提供 config 目录"一件事。
 */
@Mod(Constants.MOD_ID)
public class NeoForgeMod {
    public NeoForgeMod() {
        // FMLPaths 在 mod 构造期已全部就绪; CONFIGDIR = .minecraft/config
        LogFilterBootstrap.init(FMLPaths.CONFIGDIR.get());
    }
}