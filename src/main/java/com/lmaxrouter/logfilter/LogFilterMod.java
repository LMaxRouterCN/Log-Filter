package com.lmaxrouter.logfilter;

import com.lmaxrouter.logfilter.config.ModConfig;
import com.lmaxrouter.logfilter.filter.LogFilterManager;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(LogFilterMod.MOD_ID)
public class LogFilterMod {
    public static final String MOD_ID = "logfilter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public LogFilterMod() {
        // Register config - 使用完全限定名避免冲突
        ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                ModConfig.SPEC
        );

        // Register event bus
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);

        LOGGER.info("Log Filter Mod loaded!");
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Initializing Log Filter...");
        LogFilterManager.initialize();
    }
}
