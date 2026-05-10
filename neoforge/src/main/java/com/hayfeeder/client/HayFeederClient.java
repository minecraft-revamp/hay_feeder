package com.hayfeeder.client;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.feature.feeder.HayFeederBlockEntityRenderer;
import com.hayfeeder.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = HayFeeder.MOD_ID, dist = Dist.CLIENT)
public class HayFeederClient {
    public HayFeederClient(ModContainer container) {
        container.getEventBus().addListener(HayFeederClient::onRegisterRenderers);
        HayFeeder.LOGGER.info("Hay Feeder client setup");
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.HAY_FEEDER.get(),
                HayFeederBlockEntityRenderer::new);
    }
}
