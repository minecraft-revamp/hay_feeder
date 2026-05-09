package com.hayfeeder.fabric;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final BlockEntityType<HayFeederBlockEntity> HAY_FEEDER = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(HayFeederFabric.MOD_ID, "hay_feeder"),
            FabricBlockEntityTypeBuilder.create(HayFeederBlockEntity::new, ModBlocks.HAY_FEEDER).build());

    private ModBlockEntities() {}

    public static void bootstrap() {}
}
