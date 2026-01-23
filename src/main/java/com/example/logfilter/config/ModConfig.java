package com.example.logfilter.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue enableFilter;
    public static final ForgeConfigSpec.BooleanValue debugMode;
    public static final ForgeConfigSpec.IntValue maxCacheSize;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> filterRules;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> exactMatches;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> loggerNames;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> logLevels;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> excludePatterns;

    static {
        final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        BUILDER.push("General");

        enableFilter = BUILDER
                .comment("Enable or disable log filtering")
                .define("enableFilter", true);

        debugMode = BUILDER
                .comment("Enable debug mode to see which logs are being filtered")
                .define("debugMode", false);

        maxCacheSize = BUILDER
                .comment("Maximum size of filtered log cache (for duplicate detection)")
                .defineInRange("maxCacheSize", 1000, 100, 10000);

        BUILDER.pop();

        BUILDER.push("FilterRules");

        filterRules = BUILDER
                .comment("Regex patterns to filter log messages")
                .defineList("filterRules", ArrayList::new,
                        obj -> obj instanceof String && !((String) obj).isEmpty());

        exactMatches = BUILDER
                .comment("Exact message strings to filter (case-sensitive)")
                .defineList("exactMatches", ArrayList::new,
                        obj -> obj instanceof String);

        loggerNames = BUILDER
                .comment("Logger names to completely filter (e.g., 'net.minecraft.server.MinecraftServer')")
                .defineList("loggerNames", ArrayList::new,
                        obj -> obj instanceof String);

        logLevels = BUILDER
                .comment("Log levels to filter: TRACE, DEBUG, INFO, WARN, ERROR, FATAL")
                .defineList("logLevels", List.of("TRACE", "DEBUG"),
                        obj -> obj instanceof String);

        excludePatterns = BUILDER
                .comment("Regex patterns that will EXCLUDE logs from filtering (whitelist)")
                .defineList("excludePatterns", ArrayList::new,
                        obj -> obj instanceof String);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
