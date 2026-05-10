package com.hayfeeder.feature.feeder;

import com.hayfeeder.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class HayFeederBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int CAPACITY = 64;
    public static final double FEED_RADIUS = 6.0;

    private ItemStack contents = ItemStack.EMPTY;

    public HayFeederBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAY_FEEDER.get(), pos, state);
    }

    public ItemStack getContents() { return contents; }

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
        syncToWorld();
    }

    // --- Container ---------------------------------------------------------

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return contents.isEmpty(); }

    @Override
    public ItemStack getItem(int slot) { return slot == 0 ? contents : ItemStack.EMPTY; }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || contents.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = contents.split(amount);
        syncToWorld();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack out = contents;
        contents = ItemStack.EMPTY;
        return out;  // no syncToWorld per "noUpdate" contract
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        ItemStack copy = stack.copy();
        if (copy.getCount() > CAPACITY) copy.setCount(CAPACITY);
        ItemStack old = this.contents;
        this.contents = copy;
        boolean grew = copy.getCount() > old.getCount();
        syncToWorld();
        if (grew && level instanceof ServerLevel server) {
            BlockPos pos = getBlockPos();
            server.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, copy.getItem()),
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    6, 0.2, 0.05, 0.2, 0.05);
            server.playSound(null, pos, SoundEvents.COMPOSTER_FILL_SUCCESS,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0
                && AcceptedFoods.isAccepted(stack)
                && (contents.isEmpty() || contents.is(stack.getItem()));
    }

    @Override
    public void clearContent() {
        contents = ItemStack.EMPTY;
        syncToWorld();
    }

    // --- MenuProvider ------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.hay_feeder.hay_feeder");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new HayFeederMenu(containerId, playerInventory, java.util.List.of(this));
    }

    // --- Internal ----------------------------------------------------------

    private void syncToWorld() {
        setChanged();
        if (level != null) {
            level.setBlock(getBlockPos(),
                    getBlockState().setValue(HayFeederBlock.FILL_STAGE, FillStage.fromCount(contents.getCount())),
                    Block.UPDATE_ALL);
        }
    }

    // --- Persistence -------------------------------------------------------

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

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
