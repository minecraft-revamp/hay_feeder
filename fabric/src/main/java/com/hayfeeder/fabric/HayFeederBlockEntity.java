package com.hayfeeder.fabric;

import net.minecraft.world.item.Item;

/**
 * Skeleton placeholder for the hay feeder's container logic on Fabric.
 *
 * <p>Task 9 of the feeding-logic plan ports only the pure-logic units
 * ({@code CAPACITY} and {@link #computeAbsorb}) so they can be unit-tested
 * in isolation, mirroring the NeoForge port. Task 11 promotes this class
 * to a real {@code net.minecraft.world.level.block.entity.BlockEntity} with
 * persistence, ticking, and animal-feeding behaviour.
 */
public class HayFeederBlockEntity {
    public static final int CAPACITY = 8;

    static int computeAbsorb(Item containedType, int containedCount, Item incomingType, int incomingCount) {
        if (containedType == null || containedCount == 0) {
            return AcceptedFoods.isAccepted(incomingType)
                    ? Math.min(incomingCount, CAPACITY)
                    : 0;
        }
        if (containedType == incomingType) {
            return Math.min(incomingCount, CAPACITY - containedCount);
        }
        return 0;
    }
}
