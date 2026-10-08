package com.lmaxrouter.logfilter.client;

import com.lmaxrouter.logfilter.LogFilterMod;
import com.lmaxrouter.logfilter.config.ConfigIssues;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 客户端专属: 配置问题的聊天框展示端(本类仅在物理客户端被事件总线加载, 服务端不可见)。
 * [长期记忆: 003] 两条事件链汇入同一个 flush, 全程事件驱动零轮询:
 *  1) 世界内热重载: 配置监听线程调 requestDisplay() → 钩子经 Minecraft.execute 把 flush 投递到主线程;
 *  2) 启动期(Loading/初始化时聊天 HUD 不存在): 问题留队, 玩家进入世界触发 LoggingIn → flush。
 * flush 幂等且带玩家在线守卫: HUD 未就绪不清队列, 留给下一次事件, 不丢不重。
 */
@Mod.EventBusSubscriber(modid = LogFilterMod.MOD_ID, value = Dist.CLIENT)
public final class ClientConfigWarnings {

    // 类被注册(仅客户端)时即注入钩子, 此后任何线程的 requestDisplay() 都能安全投递到主线程
    static {
        ConfigIssues.setAsyncDisplayHook(() -> Minecraft.getInstance().execute(ClientConfigWarnings::flush));
    }

    /** 玩家登录进世界: 聊天 HUD 必然就绪, 冲刷启动期遗留的问题 */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        flush();
    }

    /** 主线程专用: 仅当玩家会话存在时冲刷队列进聊天框; 黄字, 汇总行 + 逐条明细 */
    private static void flush() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return; // 主菜单/无会话: 保持入队, 留给 LoggingIn 再冲
        }
        List<String> issues = ConfigIssues.drain();
        if (issues.isEmpty()) {
            return;
        }
        mc.player.displayClientMessage(Component.literal("[LogFilter] " + issues.size()
                + " invalid regex(es) skipped / 非法正则已跳过(详见日志):")
                .withStyle(ChatFormatting.YELLOW), false);
        for (String issue : issues) {
            mc.player.displayClientMessage(Component.literal("  " + issue)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private ClientConfigWarnings() {
    }
}