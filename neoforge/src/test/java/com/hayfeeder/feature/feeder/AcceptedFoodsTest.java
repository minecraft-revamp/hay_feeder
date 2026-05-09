package com.hayfeeder.feature.feeder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptedFoodsTest {

    // The ItemStack(Item) constructor reads holder.components(), which is
    // only bound during world/registry load — not by Bootstrap.bootStrap()
    // in the FML JUnit harness. So the non-empty cases assert via the
    // isAccepted(Item) overload; the empty case still asserts via the
    // ItemStack overload because ItemStack.EMPTY short-circuits before any
    // component lookup. Production callers in-game always pass an ItemStack.

    @Test
    void wheat_is_accepted() {
        assertTrue(AcceptedFoods.isAccepted(Items.WHEAT));
    }

    @Test
    void carrot_is_accepted() {
        assertTrue(AcceptedFoods.isAccepted(Items.CARROT));
    }

    @Test
    void salmon_is_accepted() {
        assertTrue(AcceptedFoods.isAccepted(Items.SALMON));
    }

    @Test
    void glow_berries_are_accepted() {
        assertTrue(AcceptedFoods.isAccepted(Items.GLOW_BERRIES));
    }

    @Test
    void cookie_is_rejected_to_protect_parrots() {
        assertFalse(AcceptedFoods.isAccepted(Items.COOKIE));
    }

    @Test
    void golden_carrot_is_rejected_to_avoid_trivialising_horse_breeding() {
        assertFalse(AcceptedFoods.isAccepted(Items.GOLDEN_CARROT));
    }

    @Test
    void enchanted_golden_apple_is_rejected() {
        assertFalse(AcceptedFoods.isAccepted(Items.ENCHANTED_GOLDEN_APPLE));
    }

    @Test
    void diamond_is_rejected() {
        assertFalse(AcceptedFoods.isAccepted(Items.DIAMOND));
    }

    @Test
    void empty_stack_is_rejected() {
        assertFalse(AcceptedFoods.isAccepted(ItemStack.EMPTY));
    }
}
