package com.hayfeeder.registry;

import com.google.common.collect.ImmutableSet;
import com.hayfeeder.HayFeeder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the Point-of-Interest type backing the {@code hay_feeder} block as
 * a villager workstation, mirroring vanilla's {@code minecraft:composter} →
 * farmer pairing (see {@code PoiTypes#bootstrap}).
 *
 * <p>Key path: {@code hay_feeder:hay_feeder} — we follow vanilla's convention
 * of naming the PoI after its workstation block, not the profession.
 *
 * <p>NeoForge auto-registers the block→PoI mapping via
 * {@code NeoForgeRegistryCallbacks} once the {@link DeferredRegister} fires,
 * so no manual {@code registerBlockStates} call is required (cf. vanilla
 * {@code PoiTypes#registerBlockStates}, which only runs for built-in types).
 */
public final class ModPoiTypes {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, HayFeeder.MOD_ID);

    public static final ResourceKey<PoiType> HAY_FEEDER_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(HayFeeder.MOD_ID, "hay_feeder"));

    public static final DeferredHolder<PoiType, PoiType> HAY_FEEDER = POI_TYPES.register(
            "hay_feeder",
            () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.HAY_FEEDER.get().getStateDefinition().getPossibleStates()),
                    /* maxTickets */ 1,
                    /* validRange */ 1));

    private ModPoiTypes() {}
}
