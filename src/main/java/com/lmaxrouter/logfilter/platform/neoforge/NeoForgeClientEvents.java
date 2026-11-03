//? neoforge {
package com.lmaxrouter.logfilter.platform.neoforge;

import com.lmaxrouter.logfilter.Constants;
import com.lmaxrouter.logfilter.client.ClientConfigWarnings;
import com.lmaxrouter.logfilter.config.ConfigIssues;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.SubscribeEvent;

/**
 * NeoForge 客户端事件接线 (game bus, 仅物理客户端加载, 服务端不可见)。
 * [Stonecutter 迁移] 替代原 Forge ClientConfigWarnings 的 @EventBusSubscriber(Dist.CLIENT) 接线:
 *  1) 钩子注入: ConfigIssues 展示钩子把 flush 经 Minecraft.execute 投递主线程;
 *  2) 进世界冲刷: 启动期留队的坏规则问题在 LoggingIn 消费。
 * [Stonecutter 条件块] 整文件包进 //? neoforge {}: fabric 节点编译时本文件被剔除为空。
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeClientEvents {

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

    private NeoForgeClientEvents() {
    }
}
//?}