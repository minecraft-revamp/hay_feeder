package com.hayfeeder.feature.feeder;

import com.hayfeeder.registry.ModMenuTypes;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class HayFeederMenu extends AbstractContainerMenu {
    private final Container container;

    public HayFeederMenu(int containerId, Inventory playerInventory, Container container) {
        super(ModMenuTypes.HAY_FEEDER.get(), containerId);
        checkContainerSize(container, 1);
        this.container = container;
        container.startOpen(playerInventory.player);

        addSlot(new HayFeederFoodSlot(container, 0, 80, 35));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    public HayFeederMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(1));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack returned = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack here = slot.getItem();
            returned = here.copy();
            if (slotIndex == 0) {
                if (!moveItemStackTo(here, 1, 37, true)) return ItemStack.EMPTY;
            } else {
                if (!moveItemStackTo(here, 0, 1, false)) return ItemStack.EMPTY;
            }
            if (here.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return returned;
    }

    @Override
    public boolean stillValid(Player player) { return container.stillValid(player); }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    private static class HayFeederFoodSlot extends Slot {
        HayFeederFoodSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public int getMaxStackSize() { return container.getMaxStackSize(); }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(0, stack);
        }
    }
}
