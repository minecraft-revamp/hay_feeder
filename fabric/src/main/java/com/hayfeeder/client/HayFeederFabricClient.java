package com.hayfeeder.client;

import com.hayfeeder.HayFeederFabric;
import com.hayfeeder.registry.ModBlockEntities;
import com.hayfeeder.registry.ModMenuTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

public class HayFeederFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // MenuScreens.register is vanilla; both loaders share it on MC 26.1.
        MenuScreens.register(ModMenuTypes.HAY_FEEDER, HayFeederScreen::new);
        // Fabric BER hookup. NeoForge equivalent fires on EntityRenderersEvent.RegisterRenderers.
        BlockEntityRendererRegistry.register(ModBlockEntities.HAY_FEEDER, HayFeederContentsRenderer::new);

        HayFeederFabric.LOGGER.info("Hay Feeder (Fabric) client setup");
    }
}
