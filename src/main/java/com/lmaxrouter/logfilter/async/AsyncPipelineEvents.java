package com.lmaxrouter.logfilter.async;

import com.lmaxrouter.logfilter.LogFilterMod;
import com.lmaxrouter.logfilter.config.ModConfig;
import com.lmaxrouter.logfilter.filter.LogFilterManager;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 配置事件接线层 (v2 异步化改造)。
 *
 * 注意: ModConfigEvent 是 MOD bus 事件(FML 在配置文件加载/重载时于 mod 事件总线触发),
 * 因此这里必须是 Bus.MOD; 事件到达时 ForgeConfigSpec 的值 100% 已加载完毕,
 * 配置读取不存在任何时序问题——这就是事件驱动替代轮询的接线点。
 *
 * 本类由 @Mod.EventBusSubscriber 注解自动注册, LogFilterMod 入口类零改动。
 */
@Mod.EventBusSubscriber(modid = LogFilterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AsyncPipelineEvents {

    /** 配置文件首次加载(游戏启动期或该类型配置初始化时) */
    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (!isOurConfig(event)) {
            return;
        }
        // 按快照装配/幂等校验异步输出管线
        AsyncLogPipeline.apply(AsyncPipelineConfig.snapshot());
        // [长期记忆: 001] 复活 LogFilterManager.reload() 死代码: 过滤配置随配置加载事件同步刷新,
        // 从此"改配置文件不生效必须重启游戏"的坑被填掉
        LogFilterManager.reload();
    }

    /** 玩家在游戏中修改并保存配置文件(Forge 会触发热重载) */
    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (!isOurConfig(event)) {
            return;
        }
        // AsyncLogPipeline.apply 幂等: 参数签名未变则什么都不做, 变了才事务性重建管线
        AsyncLogPipeline.apply(AsyncPipelineConfig.snapshot());
        // 过滤配置同理热重载(新增: 之前只有死代码 reload 无人订阅)
        LogFilterManager.reload();
    }

    /** 只响应本模组自己的 config spec, 忽略其他模组/Forge 的配置事件 */
    private static boolean isOurConfig(ModConfigEvent event) {
        return event.getConfig().getSpec() == ModConfig.SPEC;
    }
}