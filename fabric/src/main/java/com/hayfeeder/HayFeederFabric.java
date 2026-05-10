package com.hayfeeder;

import com.hayfeeder.registry.ModBlockEntities;
import com.hayfeeder.registry.ModBlocks;
import com.hayfeeder.registry.ModCreativeTabs;
import com.hayfeeder.registry.ModItems;
import com.hayfeeder.registry.ModMenuTypes;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class HayFeederFabric implements ModInitializer {
    public static final String MOD_ID = "hay_feeder";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        // Class-load triggers force the static initializers to run,
        // performing registration in the right order.
        ModBlocks.bootstrap();
        ModItems.bootstrap();
        ModBlockEntities.bootstrap();
        ModMenuTypes.bootstrap();
        ModCreativeTabs.bootstrap();

        LOGGER.info("Hay Feeder (Fabric) initialised");
    }
}
