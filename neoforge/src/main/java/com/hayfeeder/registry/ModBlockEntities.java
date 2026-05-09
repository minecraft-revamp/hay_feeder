package com.hayfeeder.registry;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.feature.feeder.HayFeederBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HayFeeder.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HayFeederBlockEntity>> HAY_FEEDER =
            BLOCK_ENTITIES.register("hay_feeder", () ->
                    new BlockEntityType<>(HayFeederBlockEntity::new, ModBlocks.HAY_FEEDER.get()));

    private ModBlockEntities() {}
}
