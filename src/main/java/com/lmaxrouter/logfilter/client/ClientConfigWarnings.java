package com.lmaxrouter.logfilter.client;

import com.lmaxrouter.logfilter.config.ConfigIssues;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 客户端专属: 配置问题的聊天框展示端。
 * [长期记忆: 003] 两条事件链汇入同一个 flush, 全程事件驱动零轮询:
 *  1) 世界内热重载: 配置监听线程调 requestDisplay() -> 钩子经 Minecraft.execute 把 flush 投递到主线程;
 *  2) 启动期(聊天 HUD 不存在): 问题留队, 玩家进入世界触发进世界事件 -> flush。
 * flush 幂等且带玩家在线守卫: HUD 未就绪不清队列, 留给下一次事件, 不丢不重。
 *
 * [Stonecutter 迁移] 自 Forge @EventBusSubscriber(Dist.CLIENT) 版重写为纯逻辑类:
 * 事件接线(钩子注入 + 进世界冲刷)移至各平台入口(platform.fabric.FabricClientEntrypoint /
 * platform.neoforge.NeoForgeClientEvents), 本类只保留 flush 本体。
 * 服务端永不加载本类(无人引用它; 其 import 的 Minecraft 类在 dedicated server 不存在)。
 */
public final class ClientConfigWarnings {

    /** 主线程专用: 仅当玩家会话存在时冲刷队列进聊天框; 黄字, 汇总行 + 逐条明细 */
    public static void flush() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return; // 主菜单/无会话: 保持入队, 留给进世界事件再冲
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