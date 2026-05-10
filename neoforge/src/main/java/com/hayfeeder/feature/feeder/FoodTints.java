package com.hayfeeder.feature.feeder;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Maps each accepted food to a representative tint color (ARGB int) used by the BlockColor
 * handler to recolor the contents inside the hay_feeder block. The tint is multiplied with
 * the underlying hay texture (yellow), so cool/dark colors will darken; warm colors will
 * shift hue subtly.
 *
 * Default (no contents or unknown food) returns 0xFFFFFFFF (no tint, hay stays yellow).
 */
public final class FoodTints {

    private static final int DEFAULT = 0xFFFFFFFF;

    private static final Map<Item, Integer> TINTS = Map.ofEntries(
            Map.entry(Items.WHEAT,         0xFFFFFFFF),  // default yellow hay
            Map.entry(Items.CARROT,        0xFFFF9040),  // orange
            Map.entry(Items.BEETROOT,      0xFFD04040),  // deep red
            Map.entry(Items.WHEAT_SEEDS,   0xFFE0E090),  // pale yellow-green
            Map.entry(Items.MELON_SEEDS,   0xFFB0C060),  // green-tan
            Map.entry(Items.PUMPKIN_SEEDS, 0xFFD0A060),  // tan
            Map.entry(Items.BEETROOT_SEEDS,0xFFC08080),  // dusty pink
            Map.entry(Items.COD,           0xFFE0C0A0),  // pale flesh
            Map.entry(Items.SALMON,        0xFFFF8090),  // pink
            Map.entry(Items.TROPICAL_FISH, 0xFF80E0FF),  // cyan
            Map.entry(Items.SWEET_BERRIES, 0xFFC04040),  // red berry
            Map.entry(Items.GLOW_BERRIES,  0xFFFFC060),  // orange-yellow glow
            Map.entry(Items.APPLE,         0xFFD04040)   // red apple
    );

    private FoodTints() {}

    public static int colorFor(Item item) {
        return TINTS.getOrDefault(item, DEFAULT);
    }
}
