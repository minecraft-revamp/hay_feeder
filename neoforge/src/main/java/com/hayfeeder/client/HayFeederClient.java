package com.hayfeeder.client;

import com.hayfeeder.HayFeeder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = HayFeeder.MOD_ID, dist = Dist.CLIENT)
public class HayFeederClient {
    public HayFeederClient(ModContainer container) {
        HayFeeder.LOGGER.info("Hay Feeder client setup");
    }
}
