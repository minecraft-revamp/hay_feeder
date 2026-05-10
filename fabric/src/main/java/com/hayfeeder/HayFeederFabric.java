package com.hayfeeder;

import com.hayfeeder.feature.feeder.FollowFeederGoal;
import com.hayfeeder.registry.ModBlockEntities;
import com.hayfeeder.registry.ModBlocks;
import com.hayfeeder.registry.ModCreativeTabs;
import com.hayfeeder.registry.ModItems;
import com.hayfeeder.registry.ModMenuTypes;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.animal.Animal;
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

        // Inject FollowFeederGoal into every Animal that loads on the server.
        // Priority 3 sits above LookAt (8) and Wander (7), below Panic (1) and TemptGoal (2).
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Animal animal) {
                animal.goalSelector.addGoal(3, new FollowFeederGoal(animal));
            }
        });

        LOGGER.info("Hay Feeder (Fabric) initialised");
    }
}
