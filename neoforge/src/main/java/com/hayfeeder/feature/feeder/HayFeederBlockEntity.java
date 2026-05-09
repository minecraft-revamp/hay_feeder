package com.hayfeeder.feature.feeder;

import com.hayfeeder.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class HayFeederBlockEntity extends BlockEntity {
    public static final int CAPACITY = 8;
    public static final double FEED_RADIUS = 6.0;

    private ItemStack contents = ItemStack.EMPTY;

    public HayFeederBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAY_FEEDER.get(), pos, state);
    }

    public ItemStack getContents() {
        return contents;
    }

    public int tryRefill(ItemStack incoming) {
        Item containedType = contents.isEmpty() ? null : contents.getItem();
        int absorbed = computeAbsorb(containedType, contents.getCount(),
                incoming.getItem(), incoming.getCount());
        if (absorbed == 0) return 0;
        if (this.contents.isEmpty()) {
            this.contents = incoming.copyWithCount(absorbed);
        } else {
            this.contents.grow(absorbed);
        }
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.setBlock(getBlockPos(),
                    state.setValue(HayFeederBlock.FEEDS_LEFT, this.contents.getCount()),
                    Block.UPDATE_ALL);
        }
        return absorbed;
    }

    public void tickFeeding(ServerLevel level, BlockPos pos, BlockState state) {
        if (contents.isEmpty()) return;
        AABB box = AABB.ofSize(pos.getCenter(), FEED_RADIUS * 2, FEED_RADIUS * 2, FEED_RADIUS * 2);
        List<Animal> targets = level.getEntitiesOfClass(Animal.class, box,
                a -> a.isAlive() && a.isFood(contents));
        if (targets.isEmpty()) return;
        for (Animal a : targets) {
            FeedingMechanic.feedAnimal(level, a);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                5, 0.2, 0.05, 0.2, 0.0);
        level.playSound(null, pos, SoundEvents.GRASS_BREAK,
                SoundSource.BLOCKS, 0.3f, 1.0f);
        contents.shrink(1);
        level.setBlock(pos, state.setValue(HayFeederBlock.FEEDS_LEFT, contents.getCount()),
                Block.UPDATE_ALL);
        setChanged();
    }

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

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.contents = input.read("contents", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!contents.isEmpty()) {
            output.store("contents", ItemStack.CODEC, contents);
        }
    }
}
