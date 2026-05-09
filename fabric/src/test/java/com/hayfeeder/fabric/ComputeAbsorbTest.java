package com.hayfeeder.fabric;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComputeAbsorbTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void empty_takes_accepted_food_capped_at_capacity() {
        int n = HayFeederBlockEntity.computeAbsorb(null, 0, Items.WHEAT, 16);
        assertEquals(8, n);
    }

    @Test
    void empty_takes_partial_stack_in_full() {
        int n = HayFeederBlockEntity.computeAbsorb(null, 0, Items.WHEAT, 5);
        assertEquals(5, n);
    }

    @Test
    void empty_rejects_non_accepted() {
        int n = HayFeederBlockEntity.computeAbsorb(null, 0, Items.DIAMOND, 5);
        assertEquals(0, n);
    }

    @Test
    void empty_rejects_cookie() {
        int n = HayFeederBlockEntity.computeAbsorb(null, 0, Items.COOKIE, 8);
        assertEquals(0, n);
    }

    @Test
    void partial_tops_up_same_food() {
        int n = HayFeederBlockEntity.computeAbsorb(Items.WHEAT, 3, Items.WHEAT, 4);
        assertEquals(4, n);
    }

    @Test
    void partial_caps_at_capacity_during_topup() {
        int n = HayFeederBlockEntity.computeAbsorb(Items.WHEAT, 3, Items.WHEAT, 16);
        assertEquals(5, n);
    }

    @Test
    void partial_rejects_different_food() {
        int n = HayFeederBlockEntity.computeAbsorb(Items.WHEAT, 3, Items.CARROT, 5);
        assertEquals(0, n);
    }

    @Test
    void full_rejects_more_of_same_food() {
        int n = HayFeederBlockEntity.computeAbsorb(Items.WHEAT, 8, Items.WHEAT, 5);
        assertEquals(0, n);
    }
}
