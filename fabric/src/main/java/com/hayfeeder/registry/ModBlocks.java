package com.hayfeeder.registry;

import com.hayfeeder.HayFeederFabric;
import com.hayfeeder.feature.feeder.HayFeederBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

public final class ModBlocks {
    public static final HayFeederBlock HAY_FEEDER = registerBlock(
            "hay_feeder",
            HayFeederBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.HAY_BLOCK)
                    .sound(SoundType.WOOD));

    private ModBlocks() {}

    private static <T extends Block> T registerBlock(
            String name,
            Function<BlockBehaviour.Properties, T> factory,
            BlockBehaviour.Properties baseProps) {
        Identifier id = Identifier.fromNamespaceAndPath(HayFeederFabric.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        BlockBehaviour.Properties props = baseProps.setId(key);
        T block = factory.apply(props);
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        return block;
    }

    public static void bootstrap() {}
}
