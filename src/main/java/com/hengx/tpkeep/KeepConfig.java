package com.hengx.tpkeep;

import net.minecraftforge.common.ForgeConfigSpec;

public final class KeepConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue KEEP_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue KEEP_FLIGHT;
    public static final ForgeConfigSpec.IntValue RETRY_TICKS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("传送保持补丁:传送/跨维度后恢复状态。").push("tpkeep");
        ENABLED = b.comment("总开关。").define("enabled", true);
        KEEP_EFFECTS = b.comment("保留药水效果(原版本应保留,这里是兜底恢复)。").define("keepEffects", true);
        KEEP_FLIGHT = b.comment("保留创造飞行(/fly 给的 mayfly)。").define("keepFlight", true);
        RETRY_TICKS = b.comment("到达后重复恢复的 tick 数(有些模组会在到达后几 tick 才清,多刷几次更稳)。")
            .defineInRange("retryTicks", 20, 0, 200);
        DEBUG_LOG = b.comment("恢复过程的日志(每次 resync 一行:发了几个效果包、是否恢复了飞行)。",
                "默认 false(静默),恢复本身照常执行,只是不写日志。",
                "只有排查问题时才打开:打开后每次传送、每个 retryTicks 内的 tick 都会各写一行。")
            .define("debugLog", false);
        b.pop();
        SPEC = b.build();
    }

    private KeepConfig() {}

    public static boolean enabled() { return ENABLED.get(); }
    public static boolean keepEffects() { return KEEP_EFFECTS.get(); }
    public static boolean keepFlight() { return KEEP_FLIGHT.get(); }
    public static int retryTicks() { return RETRY_TICKS.get(); }

    /** 排查问题时才打开;关闭时调用方连日志字符串都不拼。 */
    public static boolean debugLog() { return DEBUG_LOG.get(); }
}