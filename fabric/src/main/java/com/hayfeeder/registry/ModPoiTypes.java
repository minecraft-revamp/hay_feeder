package com.hayfeeder.registry;

import com.google.common.collect.ImmutableSet;
import com.hayfeeder.HayFeederFabric;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Fabric mirror of the NeoForge {@code ModPoiTypes}: registers the
 * {@code hay_feeder:hay_feeder} Point-of-Interest type backing the
 * {@code hay_feeder} block as a villager workstation.
 *
 * <p>On Fabric there's no DeferredRegister; we touch {@link BuiltInRegistries}
 * directly. Vanilla also maintains an internal blockstate→PoI map (used by
 * {@link PoiTypes#forState}) — NeoForge fills it automatically for modded
 * types, but on Fabric we have to write into it explicitly (the field is
 * exposed via {@code hay_feeder.accesswidener}).
 */
public final class ModPoiTypes {
    public static final ResourceKey<PoiType> HAY_FEEDER_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    Identifier.fromNamespaceAndPath(HayFeederFabric.MOD_ID, "hay_feeder"));

    public static final PoiType HAY_FEEDER;

    static {
        Set<BlockState> states = ImmutableSet.copyOf(
                ModBlocks.HAY_FEEDER.getStateDefinition().getPossibleStates());
        PoiType type = new PoiType(states, /* maxTickets */ 1, /* validRange */ 1);
        Holder<PoiType> holder = Registry.registerForHolder(
                BuiltInRegistries.POINT_OF_INTEREST_TYPE, HAY_FEEDER_KEY, type);
        HAY_FEEDER = type;

        // Wire blockstates → PoI holder into the internal map. NeoForge does
        // this via NeoForgeRegistryCallbacks; vanilla Fabric needs an explicit
        // poke. PoiTypes.TYPE_BY_STATE is access-widened in our AW.
        for (BlockState state : states) {
            PoiTypes.TYPE_BY_STATE.put(state, holder);
        }
    }

    private ModPoiTypes() {}

    public static void bootstrap() {
        // Static initialiser does the work; this method exists so the
        // entrypoint can force class-load in a well-defined order.
    }
}
