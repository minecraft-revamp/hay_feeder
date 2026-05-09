package com.hayfeeder.fabric.client;

import com.hayfeeder.fabric.HayFeederFabric;
import net.fabricmc.api.ClientModInitializer;

public class HayFeederFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HayFeederFabric.LOGGER.info("Hay Feeder (Fabric) client setup");
    }
}
