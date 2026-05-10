package com.hayfeeder.feature.feeder;

import com.hayfeeder.registry.ModMenuTypes;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class HayFeederMenu extends AbstractContainerMenu {
    private final List<? extends Container> members;
    private final int foodSlotCount;

    public int getFoodSlotCount() { return foodSlotCount; }

    public HayFeederMenu(int containerId, Inventory playerInventory, List<? extends Container> members) {
        super(ModMenuTypes.HAY_FEEDER.get(), containerId);
        if (members.isEmpty()) throw new IllegalArgumentException("hay_feeder menu requires at least 1 member");
        this.members = members;
        this.foodSlotCount = members.size();

        for (Container c : members) c.startOpen(playerInventory.player);

        int totalWidth = foodSlotCount * 18;
        int startX = (176 - totalWidth) / 2;
        for (int i = 0; i < foodSlotCount; i++) {
            Container c = members.get(i);
            addSlot(new GroupedFoodSlot(members, c, 0, startX + i * 18 + 1, 35));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** Client-side: builds dummy containers; server will sync slot states. */
    public HayFeederMenu(int containerId, Inventory playerInventory, int memberCount) {
        this(containerId, playerInventory, dummyMembers(memberCount));
    }

    private static List<Container> dummyMembers(int count) {
        List<Container> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new SimpleContainer(1));
        return list;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack returned = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack here = slot.getItem();
            returned = here.copy();
            int invStart = foodSlotCount;
            int invEnd = foodSlotCount + 36;
            if (slotIndex < foodSlotCount) {
                if (!moveItemStackTo(here, invStart, invEnd, true)) return ItemStack.EMPTY;
            } else {
                if (!moveItemStackTo(here, 0, foodSlotCount, false)) return ItemStack.EMPTY;
            }
            if (here.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return returned;
    }

    @Override
    public boolean stillValid(Player player) {
        for (Container c : members) {
            if (!c.stillValid(player)) return false;
        }
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        for (Container c : members) c.stopOpen(player);
    }

    private static class GroupedFoodSlot extends Slot {
        private final List<? extends Container> allMembers;

        GroupedFoodSlot(List<? extends Container> allMembers, Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.allMembers = allMembers;
        }

        @Override
        public int getMaxStackSize() { return HayFeederBlockEntity.CAPACITY; }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!AcceptedFoods.isAccepted(stack)) return false;
            for (Container c : allMembers) {
                ItemStack content = c.getItem(0);
                if (!content.isEmpty() && !content.is(stack.getItem())) return false;
            }
            return true;
        }
    }
}
