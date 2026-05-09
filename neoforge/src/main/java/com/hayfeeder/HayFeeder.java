package com.hayfeeder;

import com.hayfeeder.registry.ModBlockEntities;
import com.hayfeeder.registry.ModBlocks;
import com.hayfeeder.registry.ModCreativeTabs;
import com.hayfeeder.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(HayFeeder.MOD_ID)
public class HayFeeder {
    public static final String MOD_ID = "hay_feeder";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HayFeeder(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        LOGGER.info("Hay Feeder initialised");
    }
}
