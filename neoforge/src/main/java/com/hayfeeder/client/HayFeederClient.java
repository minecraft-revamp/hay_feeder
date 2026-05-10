package com.hayfeeder.client;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.registry.ModBlocks;
import com.hayfeeder.registry.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.List;

@Mod(value = HayFeeder.MOD_ID, dist = Dist.CLIENT)
public class HayFeederClient {
    public HayFeederClient(ModContainer container) {
        container.getEventBus().addListener(HayFeederClient::onRegisterMenuScreens);
        container.getEventBus().addListener(HayFeederClient::onRegisterBlockTintSources);
        HayFeeder.LOGGER.info("Hay Feeder client setup");
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.HAY_FEEDER.get(), HayFeederScreen::new);
    }

    /**
     * MC 26.1 renamed the per-block color event to {@code BlockTintSources} (plural).
     * The {@code register(List, Block...)} signature replaces the old per-block-and-
     * index registration: each list entry is a tint source consumed at its index,
     * matching the {@code tintindex} values in the block model JSON.
     */
    private static void onRegisterBlockTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(new HayFeederBlockColor()), ModBlocks.HAY_FEEDER.get());
    }
}
