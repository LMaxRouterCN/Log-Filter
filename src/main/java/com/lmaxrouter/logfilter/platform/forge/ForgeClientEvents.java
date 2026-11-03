//? forge {
package com.lmaxrouter.logfilter.platform.forge;

import com.lmaxrouter.logfilter.Constants;
import com.lmaxrouter.logfilter.client.ClientConfigWarnings;
import com.lmaxrouter.logfilter.config.ConfigIssues;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 客户端事件接线 (game bus, 仅物理客户端加载, 服务端不可见)。
 * [v6 forge 回归] 镜像 NeoForgeClientEvents, 接线写法复活 v1 ClientConfigWarnings (Forge 1.20.1 实测可跑):
 *  1) 钩子注入: ConfigIssues 展示钩子把 flush 经 Minecraft.execute 投递主线程;
 *  2) 进世界冲刷: 启动期留队的坏规则问题在 LoggingIn 消费。
 * [Forge 事件注解差异] @Mod.EventBusSubscriber 是 @Mod 的嵌套注解 (NeoForge 用独立注解);
 * SubscribeEvent 来自 Forge 自家 eventbus 包 (模板 1.19.2 + v1 1.20.1 双实证, 非 NeoForge 的 bus.api)。
 * [事件类名] 源码基准写 LoggingIn (1.19.2 jar + 1.20.1 v1 双实证);
 * 1.18.2 jar 实证为 LoggedInEvent, 由 build.forge.gradle.kts 的 replacements 按
  * current.parsed &lt; "1.19" 编译期替换 (含本注释与方法名子串, 无害)。
 * [Stonecutter 条件块] 整文件包进 //? forge {}: fabric / neoforge 节点编译时本文件被剔除为空。
 */
@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class ForgeClientEvents {

    // 类被注册(仅客户端)时即注入钩子, 此后任何线程的 requestDisplay() 都能安全投递到主线程
    static {
        // [长期记忆: 003] 客户端钩子: 任意线程的 requestDisplay() -> Minecraft.execute -> flush
        ConfigIssues.setAsyncDisplayHook(
                () -> Minecraft.getInstance().execute(ClientConfigWarnings::flush));
    }

    /** 玩家登录进世界: 聊天 HUD 必然就绪, 冲刷启动期遗留的问题 */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientConfigWarnings.flush();
    }

    private ForgeClientEvents() {
    }
}
//?}