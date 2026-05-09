package com.hayfeeder.feature.feeder;

import net.minecraft.world.item.Item;

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
