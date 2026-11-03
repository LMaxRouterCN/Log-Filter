//? fabric {
package com.lmaxrouter.logfilter.platform.fabric;

import com.lmaxrouter.logfilter.client.ClientConfigWarnings;
import com.lmaxrouter.logfilter.config.ConfigIssues;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/**
 * Fabric 客户端入口 (fabric.mod.json "client" entrypoint)。
 * [Stonecutter 迁移] 替代原 Forge ClientConfigWarnings 的 @EventBusSubscriber(Dist.CLIENT) 接线:
 *  1) 钩子注入: ConfigIssues 展示钩子把 flush 经 Minecraft.execute 投递主线程;
 *  2) 进世界冲刷: 启动期(主 entrypoint 先于本类执行, 聊天 HUD 不存在)留队的问题在 JOIN 消费。
 * 本类只在物理客户端加载, 服务端不可见。
 * [Stonecutter 条件块] 整文件包进 //? fabric {}: neoforge 节点编译时本文件被剔除为空。
 */
public class FabricClientEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // [长期记忆: 003] 客户端钩子: 任意线程的 requestDisplay() -> Minecraft.execute -> flush
        ConfigIssues.setAsyncDisplayHook(
                () -> Minecraft.getInstance().execute(ClientConfigWarnings::flush));

        // [长期记忆: 003] 玩家登录进世界: 聊天 HUD 必然就绪, 冲刷启动期遗留的问题
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ClientConfigWarnings.flush());
    }
}
//?}