package com.hayfeeder.client;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.feature.feeder.HayFeederMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class HayFeederScreen extends AbstractContainerScreen<HayFeederMenu> {
    private static final Identifier BG = Identifier.fromNamespaceAndPath(
            HayFeeder.MOD_ID, "textures/gui/container/hay_feeder.png");

    public HayFeederScreen(HayFeederMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, 176, 166);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BG, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }
}
