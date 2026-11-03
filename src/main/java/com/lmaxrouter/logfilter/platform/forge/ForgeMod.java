//? forge {
package com.lmaxrouter.logfilter.platform.forge;

import com.lmaxrouter.logfilter.Constants;
import com.lmaxrouter.logfilter.LogFilterBootstrap;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge 主入口 (mods.toml 由 build-logic 生成, modId = Constants.MOD_ID)。
 * [v6 forge 回归] 镜像 NeoForgeMod: 平台层只剩"提供 config 目录"一件事,
 * 启动逻辑全部在 LogFilterBootstrap.init (v1 LogFilterMod 的 ForgeConfigSpec 路线已废弃, v5 起 NightConfig 自读)。
 * [包路径实证] @Mod = net.minecraftforge.fml.common.Mod (1.19.2 模板 + 1.20.1 v1 双实证);
 * FMLPaths = net.minecraftforge.fml.loading.FMLPaths (NeoForge 同构 + 1.16~1.20 一致; 1.18.2/1.19.2 待 CI 判决)。
 * [Stonecutter 条件块] 整文件包进 //? forge {}: fabric / neoforge 节点编译时本文件被剔除为空。
 */
@Mod(Constants.MOD_ID)
public class ForgeMod {
    public ForgeMod() {
        // FMLPaths 在 mod 构造期已全部就绪; CONFIGDIR = .minecraft/config
        LogFilterBootstrap.init(FMLPaths.CONFIGDIR.get());
    }
}
//?}