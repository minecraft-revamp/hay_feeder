package com.hayfeeder.registry;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.feature.feeder.HayFeederBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HayFeeder.MOD_ID);

    public static final DeferredBlock<HayFeederBlock> HAY_FEEDER = BLOCKS.registerBlock(
            "hay_feeder",
            HayFeederBlock::new,
            () -> BlockBehaviour.Properties.ofFullCopy(Blocks.HAY_BLOCK));

    private ModBlocks() {}
}
