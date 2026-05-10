package com.hayfeeder.feature.feeder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Pulls an animal toward the closest hay-feeder containing food it eats — same
 * shape as vanilla {@link net.minecraft.world.entity.ai.goal.TemptGoal} but the
 * target is a {@link BlockPos} rather than a player. Only kicks in when the
 * animal can actually benefit (baby or {@code canFallInLove}); the actual
 * feed/charge consumption is still handled by {@code HayFeederBlockEntity#tickFeeding}.
 */
public class FollowFeederGoal extends Goal {

    private static final double SCAN_RADIUS = 16.0;
    private static final int    SCAN_COOLDOWN_TICKS = 20;        // 1s between rescans
    private static final double SPEED_MODIFIER = 1.0;
    private static final double STOP_DISTANCE_SQR = 1.5 * 1.5;   // close enough; tickFeeding takes over

    private final Animal animal;
    private BlockPos targetPos;
    private int cooldown;

    public FollowFeederGoal(Animal animal) {
        this.animal = animal;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        cooldown = SCAN_COOLDOWN_TICKS;
        if (!HayFeederBlockEntity.canBenefit(animal)) return false;
        targetPos = findNearestFeederWithFoodFor(animal);
        return targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return targetPos != null
                && HayFeederBlockEntity.canBenefit(animal)
                && stillValid(targetPos, animal)
                && animal.distanceToSqr(Vec3.atCenterOf(targetPos)) > STOP_DISTANCE_SQR;
    }

    @Override
    public void start() {
        animal.getNavigation().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5,
                SPEED_MODIFIER);
    }

    @Override
    public void stop() {
        animal.getNavigation().stop();
        targetPos = null;
    }

    /** Closest hay_feeder BE in {@link #SCAN_RADIUS} whose contents this animal eats. */
    private static BlockPos findNearestFeederWithFoodFor(Animal a) {
        Level level = a.level();
        BlockPos origin = a.blockPosition();
        int r = (int) Math.ceil(SCAN_RADIUS);
        BlockPos best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (!(level.getBlockEntity(p) instanceof HayFeederBlockEntity be)) continue;
            ItemStack stack = be.getContents();
            if (stack.isEmpty() || !a.isFood(stack)) continue;
            double d = a.distanceToSqr(Vec3.atCenterOf(p));
            if (d < bestDistSqr) {
                bestDistSqr = d;
                best = p.immutable();
            }
        }
        return best;
    }

    private static boolean stillValid(BlockPos pos, Animal a) {
        return a.level().getBlockEntity(pos) instanceof HayFeederBlockEntity be
                && !be.getContents().isEmpty()
                && a.isFood(be.getContents());
    }
}
