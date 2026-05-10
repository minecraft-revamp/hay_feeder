package com.hayfeeder.feature.rancher;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/**
 * Canonical, code-side manifest of the rancher's trade table.
 *
 * <p>Trades themselves are registered as datapack JSON under
 * {@code data/hay_feeder/villager_trade/rancher/N/<id>.json}, with per-level
 * tags at {@code data/hay_feeder/tags/villager_trade/rancher/level_N.json}
 * and per-level {@code TradeSet}s at
 * {@code data/hay_feeder/trade_set/rancher/level_N.json}. This class exists
 * so unit tests can assert that the *intent* of each level is preserved —
 * the JSON is the source of truth at runtime, but a stale JSON drift will
 * break a JSON-shape parity test, not silently behave wrong.
 *
 * <p>Each {@link Trade} reads as "give {@code give} × {@code giveCount} for
 * {@code want} × {@code wantCount}, with {@code maxUses} uses and
 * {@code xp} merchant XP". Fields match the {@code VillagerTrade} JSON keys
 * one-to-one.
 */
public final class RancherTrades {

    private RancherTrades() {}

    public record Trade(
            Item want, int wantCount,
            Item give, int giveCount,
            int maxUses, int xp) {}

    public static final List<Trade> LEVEL_1 = List.of(
            // Novice — buy feed, sell eggs.
            new Trade(Items.WHEAT,   20, Items.EMERALD, 1, 16, 2),
            new Trade(Items.CARROT,  22, Items.EMERALD, 1, 16, 2),
            new Trade(Items.EMERALD,  1, Items.EGG,     4, 16, 1));

    public static final List<Trade> LEVEL_2 = List.of(
            // Apprentice — bulk hay buy, seeds, leather sell.
            new Trade(Items.HAY_BLOCK,   14, Items.EMERALD, 1, 16, 5),
            new Trade(Items.WHEAT_SEEDS, 26, Items.EMERALD, 1, 16, 5),
            new Trade(Items.EMERALD,      1, Items.LEATHER, 1, 12, 5));

    public static final List<Trade> LEVEL_3 = List.of(
            // Journeyman — buy raw leather, sell premium animal products.
            new Trade(Items.LEATHER,  8, Items.EMERALD,     1, 16, 20),
            new Trade(Items.EMERALD,  4, Items.MILK_BUCKET, 1,  4, 30),
            new Trade(Items.EMERALD,  6, Items.WHITE_WOOL,  1,  8, 30));

    public static final List<Trade> LEVEL_4 = List.of(
            // Expert — transport & feather drops.
            new Trade(Items.EMERALD,  8, Items.LEAD,    1,  6, 30),
            new Trade(Items.EMERALD,  1, Items.FEATHER, 5, 12, 30));

    public static final List<Trade> LEVEL_5 = List.of(
            // Master — the prized items.
            new Trade(Items.EMERALD, 12, Items.SADDLE,   1, 1, 30),
            new Trade(Items.EMERALD, 24, Items.NAME_TAG, 1, 1, 30));

    public static final Map<Integer, List<Trade>> BY_LEVEL = Map.of(
            1, LEVEL_1,
            2, LEVEL_2,
            3, LEVEL_3,
            4, LEVEL_4,
            5, LEVEL_5);
}
