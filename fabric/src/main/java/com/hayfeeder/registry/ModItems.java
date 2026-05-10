package com.hayfeeder.registry;

import com.hayfeeder.HayFeederFabric;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModItems {
    public static final BlockItem HAY_FEEDER = registerBlockItem("hay_feeder", ModBlocks.HAY_FEEDER);

    private ModItems() {}

    private static BlockItem registerBlockItem(String name, Block block) {
        Identifier id = Identifier.fromNamespaceAndPath(HayFeederFabric.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item.Properties props = new Item.Properties().setId(key).useBlockDescriptionPrefix();
        BlockItem item = new BlockItem(block, props);
        Registry.register(BuiltInRegistries.ITEM, id, item);
        return item;
    }

    public static void bootstrap() {}
}
