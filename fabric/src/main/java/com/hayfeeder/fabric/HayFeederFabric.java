package com.hayfeeder.fabric;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class HayFeederFabric implements ModInitializer {
    public static final String MOD_ID = "hay_feeder";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        ModBlocks.bootstrap();
        ModItems.bootstrap();
        ModBlockEntities.bootstrap();
        ModCreativeTabs.bootstrap();

        LOGGER.info("Hay Feeder (Fabric) initialised");
    }
}
