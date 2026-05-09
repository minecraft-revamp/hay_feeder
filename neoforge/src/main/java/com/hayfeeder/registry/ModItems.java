package com.hayfeeder.registry;

import com.hayfeeder.HayFeeder;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HayFeeder.MOD_ID);

    public static final DeferredItem<BlockItem> HAY_FEEDER = ITEMS.registerItem(
            "hay_feeder",
            props -> new BlockItem(ModBlocks.HAY_FEEDER.get(), props));

    private ModItems() {}
}
