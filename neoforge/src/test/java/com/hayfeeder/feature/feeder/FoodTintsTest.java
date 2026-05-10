package com.hayfeeder.feature.feeder;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodTintsTest {

    // Pure-logic test of the FoodTints.colorFor(Item) lookup table:
    // exact tint values for a representative whitelist sample, plus
    // the default fallback for an item not in the map.

    @Test
    void wheat_returns_default_hay_yellow() {
        assertEquals(0xFFFFFFFF, FoodTints.colorFor(Items.WHEAT));
    }

    @Test
    void carrot_returns_orange_tint() {
        assertEquals(0xFFFF9040, FoodTints.colorFor(Items.CARROT));
    }

    @Test
    void salmon_returns_pink_tint() {
        assertEquals(0xFFFF8090, FoodTints.colorFor(Items.SALMON));
    }

    @Test
    void unknown_item_falls_back_to_default() {
        assertEquals(0xFFFFFFFF, FoodTints.colorFor(Items.DIAMOND));
    }
}
