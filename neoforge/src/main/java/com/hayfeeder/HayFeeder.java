package com.hayfeeder;

import com.hayfeeder.feature.feeder.AnimalGoalRegistration;
import com.hayfeeder.registry.ModBlockEntities;
import com.hayfeeder.registry.ModBlocks;
import com.hayfeeder.registry.ModCreativeTabs;
import com.hayfeeder.registry.ModItems;
import com.hayfeeder.registry.ModMenuTypes;
import com.hayfeeder.registry.ModPoiTypes;
import com.hayfeeder.registry.ModVillagerProfessions;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.GameData;
import org.slf4j.Logger;

import java.util.Map;

@Mod(HayFeeder.MOD_ID)
public class HayFeeder {
    public static final String MOD_ID = "hay_feeder";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HayFeeder(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModPoiTypes.POI_TYPES.register(modEventBus);
        ModVillagerProfessions.PROFESSIONS.register(modEventBus);

        modEventBus.addListener(HayFeeder::onCommonSetup);

        NeoForge.EVENT_BUS.register(AnimalGoalRegistration.class);

        LOGGER.info("Hay Feeder initialised");
    }

    /**
     * Wire each hay-feeder blockstate into vanilla's {@code PoiTypes.TYPE_BY_STATE}
     * lookup explicitly. NeoForge's registry callbacks claim to do this automatically
     * for modded PoIs, but in MC 26.1 villagers were silently failing to bind to the
     * workstation in our testing — populating the map ourselves here is idempotent
     * if the auto-wire already ran, and fixes the binding if it didn't.
     */
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Map<BlockState, Holder<PoiType>> map = GameData.getBlockStatePointOfInterestTypeMap();
            Holder<PoiType> holder = ModPoiTypes.HAY_FEEDER;
            int added = 0;
            for (BlockState state : ModBlocks.HAY_FEEDER.get().getStateDefinition().getPossibleStates()) {
                if (map.put(state, holder) == null) {
                    added++;
                }
            }
            LOGGER.info("Bound {} hay_feeder blockstates to PoI hay_feeder:hay_feeder", added);
        });
    }
}
