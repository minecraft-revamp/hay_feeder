package com.hayfeeder.feature.feeder;

import net.minecraft.world.entity.animal.Animal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Adds the {@link FollowFeederGoal} to every {@link Animal} that joins a
 * server level, so existing wild and bred mobs gain the trough-seeking
 * behaviour without needing a mixin or per-species hook.
 *
 * <p>Priority 3 sits above the default LookAt (8) and Wander (7), and just
 * below Panic (1) and TemptGoal (2) — animals will follow a feeder unless
 * they're being chased or actively tempted by a held item.
 */
public final class AnimalGoalRegistration {

    private AnimalGoalRegistration() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Animal animal) {
            animal.goalSelector.addGoal(3, new FollowFeederGoal(animal));
        }
    }
}
