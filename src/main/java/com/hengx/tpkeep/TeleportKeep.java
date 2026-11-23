package com.hengx.tpkeep;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/** 传送保持补丁:跨维度传送后恢复药水效果与创造飞行(/fly)。 */
@Mod(TeleportKeep.MODID)
public class TeleportKeep {

    public static final String MODID = "tpkeep";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public TeleportKeep() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, KeepConfig.SPEC, "tpkeep-common.toml");
        LOGGER.info("传送保持补丁 已加载 (modId={})", MODID);
    }
}