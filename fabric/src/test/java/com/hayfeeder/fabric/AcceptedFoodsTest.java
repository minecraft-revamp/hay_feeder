package com.hayfeeder.fabric;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptedFoodsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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
    void null_item_is_rejected() {
        assertFalse(AcceptedFoods.isAccepted((Item) null));
    }
}
