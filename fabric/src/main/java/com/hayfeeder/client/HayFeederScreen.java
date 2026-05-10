package com.hayfeeder.client;

import com.hayfeeder.HayFeederFabric;
import com.hayfeeder.feature.feeder.HayFeederMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class HayFeederScreen extends AbstractContainerScreen<HayFeederMenu> {
    private static final Identifier BG = Identifier.fromNamespaceAndPath(
            HayFeederFabric.MOD_ID, "textures/gui/container/hay_feeder.png");

    public HayFeederScreen(HayFeederMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, 176, 166);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        // Base GUI chrome + inventory slot indicators.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BG, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        // Per-feeder food slot frames: blit the hidden template at (176, 0) at each food slot's position.
        int n = this.menu.getFoodSlotCount();
        for (int i = 0; i < n; i++) {
            Slot slot = this.menu.slots.get(i);
            graphics.blit(RenderPipelines.GUI_TEXTURED, BG,
                    x + slot.x - 1, y + slot.y - 1,
                    176.0F, 0.0F, 18, 18, 256, 256);
        }
    }
}
