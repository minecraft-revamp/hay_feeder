package com.hayfeeder.feature.feeder;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

public final class AcceptedFoods {
    private static final Set<Item> ACCEPTED = Set.of(
            Items.WHEAT, Items.CARROT, Items.BEETROOT,
            Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS,
            Items.COD, Items.SALMON, Items.TROPICAL_FISH,
            Items.SWEET_BERRIES, Items.GLOW_BERRIES,
            Items.APPLE
    );

    private AcceptedFoods() {}

    public static boolean isAccepted(ItemStack stack) {
        return !stack.isEmpty() && isAccepted(stack.getItem());
    }

    public static boolean isAccepted(Item item) {
        return ACCEPTED.contains(item);
    }
}
