package com.hayfeeder.registry;

import com.google.common.collect.ImmutableSet;
import com.hayfeeder.HayFeederFabric;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.TradeSet;

import java.util.function.Predicate;

/**
 * Fabric mirror of the NeoForge {@code ModVillagerProfessions}: registers the
 * {@code hay_feeder:rancher} villager profession that uses the
 * {@code hay_feeder} block as its workstation.
 *
 * <p>Trades are data-driven in MC 26.1 (see {@link TradeSet}); this file only
 * wires up the workstation predicate and references trade-set keys. The
 * actual trade lists ship as JSON under {@code data/hay_feeder/villager_trade/},
 * {@code data/hay_feeder/tags/villager_trade/} and
 * {@code data/hay_feeder/trade_set/}.
 *
 * <p>Display name lang key: {@code entity.hay_feeder.villager.rancher} —
 * vanilla derives the translation key as
 * {@code entity.<namespace>.villager.<path>} (see {@code VillagerProfession#register}).
 */
public final class ModVillagerProfessions {
    public static final ResourceKey<VillagerProfession> RANCHER_KEY =
            ResourceKey.create(Registries.VILLAGER_PROFESSION,
                    Identifier.fromNamespaceAndPath(HayFeederFabric.MOD_ID, "rancher"));

    public static final ResourceKey<TradeSet> RANCHER_LEVEL_1 = tradeSetKey("rancher/level_1");
    public static final ResourceKey<TradeSet> RANCHER_LEVEL_2 = tradeSetKey("rancher/level_2");
    public static final ResourceKey<TradeSet> RANCHER_LEVEL_3 = tradeSetKey("rancher/level_3");
    public static final ResourceKey<TradeSet> RANCHER_LEVEL_4 = tradeSetKey("rancher/level_4");
    public static final ResourceKey<TradeSet> RANCHER_LEVEL_5 = tradeSetKey("rancher/level_5");

    public static final VillagerProfession RANCHER;

    static {
        Predicate<Holder<PoiType>> site = poi -> poi.is(ModPoiTypes.HAY_FEEDER_KEY);
        Int2ObjectMap<ResourceKey<TradeSet>> trades = Int2ObjectMap.ofEntries(
                Int2ObjectMap.entry(1, RANCHER_LEVEL_1),
                Int2ObjectMap.entry(2, RANCHER_LEVEL_2),
                Int2ObjectMap.entry(3, RANCHER_LEVEL_3),
                Int2ObjectMap.entry(4, RANCHER_LEVEL_4),
                Int2ObjectMap.entry(5, RANCHER_LEVEL_5));
        VillagerProfession profession = new VillagerProfession(
                Component.translatable("entity." + HayFeederFabric.MOD_ID + ".villager.rancher"),
                site,
                site,
                ImmutableSet.of(Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS, Items.HAY_BLOCK),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_FARMER,
                trades);
        RANCHER = Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, RANCHER_KEY, profession);
    }

    private ModVillagerProfessions() {}

    public static void bootstrap() {
        // Static initialiser does the work; this method exists so the
        // entrypoint can force class-load in a well-defined order.
    }

    private static ResourceKey<TradeSet> tradeSetKey(String path) {
        return ResourceKey.create(Registries.TRADE_SET,
                Identifier.fromNamespaceAndPath(HayFeederFabric.MOD_ID, path));
    }
}
