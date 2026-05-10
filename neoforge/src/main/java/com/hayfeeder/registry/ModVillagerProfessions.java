package com.hayfeeder.registry;

import com.google.common.collect.ImmutableSet;
import com.hayfeeder.HayFeeder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.TradeSet;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Predicate;

/**
 * Registers the {@code hay_feeder:rancher} villager profession, which uses
 * the {@code hay_feeder} block as its workstation.
 *
 * <p>Trade tables are entirely data-driven in MC 26.1: each level is a
 * {@link TradeSet} loaded from {@code data/hay_feeder/trade_set/rancher/level_N.json},
 * which itself references a {@code #hay_feeder:rancher/level_N} tag of
 * individual {@code VillagerTrade} JSON files. See {@code feature/rancher/RancherTrades}
 * for the canonical Java-side manifest used by tests.
 *
 * <p>Work sound: {@code VILLAGER_WORK_FARMER} — the farmer's sound is the
 * closest thematic match (animal-keeper voice, not the butcher's cleaver
 * sound, and not the shepherd's loom).
 *
 * <p>Profession name {@link Component#translatable} key is derived by
 * vanilla as {@code entity.<namespace>.villager.<path>} — for us that's
 * {@code entity.hay_feeder.villager.rancher}.
 */
public final class ModVillagerProfessions {
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, HayFeeder.MOD_ID);

    public static final ResourceKey<VillagerProfession> RANCHER_KEY =
            ResourceKey.create(Registries.VILLAGER_PROFESSION,
                    Identifier.fromNamespaceAndPath(HayFeeder.MOD_ID, "rancher"));

    /** Trade-set keys per level, referencing JSON files we ship in resources. */
    public static final ResourceKey<TradeSet> RANCHER_LEVEL_1 = tradeSetKey("rancher/level_1");
    public static final ResourceKey<TradeSet> RANCHER_LEVEL_2 = tradeSetKey("rancher/level_2");
    public static final ResourceKey<TradeSet> RANCHER_LEVEL_3 = tradeSetKey("rancher/level_3");
    public static final ResourceKey<TradeSet> RANCHER_LEVEL_4 = tradeSetKey("rancher/level_4");
    public static final ResourceKey<TradeSet> RANCHER_LEVEL_5 = tradeSetKey("rancher/level_5");

    public static final DeferredHolder<VillagerProfession, VillagerProfession> RANCHER =
            PROFESSIONS.register("rancher", () -> {
                Predicate<Holder<PoiType>> site = poi -> poi.is(ModPoiTypes.HAY_FEEDER_KEY);
                Int2ObjectMap<ResourceKey<TradeSet>> trades = Int2ObjectMap.ofEntries(
                        Int2ObjectMap.entry(1, RANCHER_LEVEL_1),
                        Int2ObjectMap.entry(2, RANCHER_LEVEL_2),
                        Int2ObjectMap.entry(3, RANCHER_LEVEL_3),
                        Int2ObjectMap.entry(4, RANCHER_LEVEL_4),
                        Int2ObjectMap.entry(5, RANCHER_LEVEL_5));
                return new VillagerProfession(
                        Component.translatable("entity." + HayFeeder.MOD_ID + ".villager.rancher"),
                        site,
                        site,
                        ImmutableSet.of(Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS, Items.HAY_BLOCK),
                        ImmutableSet.of(),
                        SoundEvents.VILLAGER_WORK_FARMER,
                        trades);
            });

    private ModVillagerProfessions() {}

    private static ResourceKey<TradeSet> tradeSetKey(String path) {
        return ResourceKey.create(Registries.TRADE_SET,
                Identifier.fromNamespaceAndPath(HayFeeder.MOD_ID, path));
    }
}
