package com.hayfeeder.feature.feeder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * MC 26.1 BlockEntityRenderer rewrite.
 *
 * The legacy {@code render(BE, partialTick, PoseStack, MultiBufferSource, light, overlay)} API
 * was removed in MC 26.1 in favor of an extracted-render-state pattern modelled after entities:
 *
 *   1. {@link #createRenderState()} produces a fresh state holder.
 *   2. {@link #extractRenderState} copies the BE's per-frame data into that state on the main thread.
 *   3. {@link #submit} runs on the render thread and submits draw nodes to a {@link SubmitNodeCollector}.
 *
 * Direct rendering via {@code MultiBufferSource} / {@code ItemRenderer.renderStatic} / {@code Font.drawInBatch}
 * is no longer available; we go through {@link ItemStackRenderState#submit} for the floating item and
 * {@link SubmitNodeCollector#submitText} for the count overlay.
 *
 * Reference: {@code net.minecraft.client.renderer.blockentity.VaultRenderer} (item-rotating BER) and
 * {@code AbstractSignRenderer} (text submission).
 */
public class HayFeederBlockEntityRenderer
        implements BlockEntityRenderer<HayFeederBlockEntity, HayFeederBlockEntityRenderer.RenderState> {

    private final ItemModelResolver itemModelResolver;
    private final Font font;

    public HayFeederBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemModelResolver = ctx.itemModelResolver();
        this.font = ctx.font();
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(
            HayFeederBlockEntity blockEntity,
            RenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        ItemStack contents = blockEntity.getContents();
        if (contents.isEmpty() || blockEntity.getLevel() == null) {
            state.hasContents = false;
            return;
        }
        state.hasContents = true;
        state.count = contents.getCount();

        long gameTime = blockEntity.getLevel().getGameTime();
        // Slow Y-axis spin (~2 deg/tick) for the floating item.
        state.yawDeg = ((gameTime % 360L) + partialTicks) * 2.0f;
        // Subtle vertical bob.
        state.bob = Mth.sin((gameTime + partialTicks) * 0.05f) * 0.05f;

        // Cache camera orientation so we can billboard the count text.
        // (CameraRenderState is delivered to submit(), so we don't need to capture it here —
        // see submit() below.)

        // Resolve the item model into a render state we can submit later.
        this.itemModelResolver.updateForTopItem(
                state.itemRenderState,
                contents,
                ItemDisplayContext.GROUND,
                blockEntity.getLevel(),
                null,
                0);
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera) {
        if (!state.hasContents) return;

        // Floating item, ~1.25 blocks above the bale center, scaled to 0.6, slow Y-axis spin + bob.
        poseStack.pushPose();
        poseStack.translate(0.5f, 1.25f + state.bob, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yawDeg));
        poseStack.scale(0.6f, 0.6f, 0.6f);
        state.itemRenderState.submit(
                poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();

        // Count overlay (only when stack > 1), billboarded toward camera ~1.05 blocks above.
        if (state.count > 1) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 1.05f + state.bob, 0.5f);
            poseStack.mulPose(camera.orientation);
            // Y-flip so the text reads upright; matches vanilla nameplate scaling.
            poseStack.scale(-0.025f, -0.025f, 0.025f);

            String text = String.valueOf(state.count);
            FormattedCharSequence sequence = FormattedCharSequence.forward(text, Style.EMPTY);
            float halfWidth = -this.font.width(text) / 2.0f;

            submitNodeCollector.submitText(
                    poseStack,
                    halfWidth,
                    0.0f,
                    sequence,
                    /* dropShadow */ false,
                    Font.DisplayMode.NORMAL,
                    state.lightCoords,
                    /* color */ 0xFFFFFFFF,
                    /* backgroundColor */ 0,
                    /* outlineColor */ 0);
            poseStack.popPose();
        }
    }

    /**
     * Per-frame state extracted from the BE on the main thread, then read on the render thread.
     */
    public static class RenderState extends BlockEntityRenderState {
        public boolean hasContents;
        public int count;
        public float yawDeg;
        public float bob;
        public final ItemStackRenderState itemRenderState = new ItemStackRenderState();
    }
}
