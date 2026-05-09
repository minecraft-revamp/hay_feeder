package com.hayfeeder.fabric;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

public final class FeedingMechanic {
    private static final byte ENTITY_EVENT_IN_LOVE_HEARTS = 18;

    private FeedingMechanic() {}

    public static void feedAnimal(ServerLevel level, Animal animal) {
        if (animal.isBaby()) {
            animal.ageUp((int) ((-animal.getAge() / 20) * 0.1F), true);
        } else if (animal.canFallInLove()) {
            animal.setInLove(null);
        }
        level.broadcastEntityEvent(animal, ENTITY_EVENT_IN_LOVE_HEARTS);
    }
}
