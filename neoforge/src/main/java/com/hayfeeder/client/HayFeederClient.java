package com.hayfeeder.client;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.registry.ModBlockEntities;
import com.hayfeeder.registry.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = HayFeeder.MOD_ID, dist = Dist.CLIENT)
public class HayFeederClient {
    public HayFeederClient(ModContainer container) {
        container.getEventBus().addListener(HayFeederClient::onRegisterMenuScreens);
        container.getEventBus().addListener(HayFeederClient::onRegisterRenderers);
        HayFeeder.LOGGER.info("Hay Feeder client setup");
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.HAY_FEEDER.get(), HayFeederScreen::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.HAY_FEEDER.get(), HayFeederContentsRenderer::new);
    }
}
