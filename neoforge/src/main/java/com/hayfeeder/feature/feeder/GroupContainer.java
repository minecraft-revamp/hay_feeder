package com.hayfeeder.feature.feeder;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class GroupContainer implements Container {
    private final List<HayFeederBlockEntity> members;

    public GroupContainer(List<HayFeederBlockEntity> members) {
        this.members = members;
    }

    @Override public int getContainerSize() { return 1; }

    @Override
    public boolean isEmpty() {
        for (HayFeederBlockEntity be : members) if (!be.getContents().isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack proto = ItemStack.EMPTY;
        int totalCount = 0;
        for (HayFeederBlockEntity be : members) {
            ItemStack c = be.getContents();
            if (c.isEmpty()) continue;
            if (proto.isEmpty()) proto = c;
            totalCount += c.getCount();
        }
        if (proto.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = proto.copy();
        out.setCount(totalCount);
        return out;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0) return ItemStack.EMPTY;
        ItemStack out = ItemStack.EMPTY;
        for (int i = members.size() - 1; i >= 0 && amount > 0; i--) {
            HayFeederBlockEntity be = members.get(i);
            ItemStack c = be.getContents();
            if (c.isEmpty()) continue;
            int take = Math.min(amount, c.getCount());
            ItemStack taken = be.removeItem(0, take);
            if (out.isEmpty()) out = taken;
            else out.grow(taken.getCount());
            amount -= take;
        }
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack out = getItem(0);
        for (HayFeederBlockEntity be : members) be.removeItemNoUpdate(0);
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        int remaining = stack.getCount();
        for (HayFeederBlockEntity be : members) {
            if (remaining <= 0) {
                be.setItem(0, ItemStack.EMPTY);
                continue;
            }
            int put = Math.min(remaining, HayFeederBlockEntity.CAPACITY);
            ItemStack chunk = stack.copyWithCount(put);
            be.setItem(0, chunk);
            remaining -= put;
        }
    }

    @Override
    public int getMaxStackSize() {
        return Math.max(1, members.size()) * HayFeederBlockEntity.CAPACITY;
    }

    @Override
    public boolean stillValid(Player player) {
        return !members.isEmpty() && members.get(0).stillValid(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot != 0) return false;
        if (!AcceptedFoods.isAccepted(stack)) return false;
        for (HayFeederBlockEntity be : members) {
            ItemStack c = be.getContents();
            if (!c.isEmpty() && !c.is(stack.getItem())) return false;
        }
        return true;
    }

    @Override
    public void clearContent() {
        for (HayFeederBlockEntity be : members) be.clearContent();
    }

    @Override
    public void setChanged() {
        for (HayFeederBlockEntity be : members) be.setChanged();
    }
}
