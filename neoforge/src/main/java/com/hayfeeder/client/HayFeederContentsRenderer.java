package com.hayfeeder.client;

import com.hayfeeder.feature.feeder.FoodTints;
import com.hayfeeder.feature.feeder.HayFeederBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the dynamic content cube floating inside a hay feeder. The y-extent
 * scales linearly with the stored count (1..12 px tall over 1..64 items), and
 * the cuboid is tinted by {@link FoodTints} so different foods read distinctly.
 */
public class HayFeederContentsRenderer
        implements BlockEntityRenderer<HayFeederBlockEntity, HayFeederContentsRenderer.HayFeederContentsRenderState> {

    private static final SpriteId TOP_SPRITE = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("hay_block_top");
    private static final SpriteId SIDE_SPRITE = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("hay_block_side");

    // Local block-space inset (in 1/16 units): trough interior is 2..14 on x/z.
    private static final float INSET = 2.0F / 16.0F;
    private static final float OPP   = 14.0F / 16.0F;
    private static final float BASE_Y = 2.0F / 16.0F;
    // Height in 1/16 units, derived from count out of CAPACITY=64.
    private static final int   MAX_PX = 12;

    private final SpriteGetter sprites;

    public HayFeederContentsRenderer(BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
    }

    @Override
    public HayFeederContentsRenderState createRenderState() {
        return new HayFeederContentsRenderState();
    }

    @Override
    public void extractRenderState(
            HayFeederBlockEntity be,
            HayFeederContentsRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
        var stack = be.getContents();
        if (stack.isEmpty()) {
            state.item = null;
            state.count = 0;
        } else {
            state.item = stack.getItem();
            state.count = stack.getCount();
        }
        // Sample light from the air above so the floating box doesn't read as
        // black sitting inside an opaque hay-coloured volume.
        if (be.getLevel() != null) {
            BlockPos above = be.getBlockPos().above();
            state.packedLightAbove = LevelRenderer.getLightCoords(be.getLevel(), above);
        } else {
            state.packedLightAbove = state.lightCoords;
        }
    }

    @Override
    public void submit(HayFeederContentsRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item == null || state.count <= 0) return;
        int heightPx = (int) Math.ceil(state.count * (double) MAX_PX / HayFeederBlockEntity.CAPACITY);
        if (heightPx < 1) heightPx = 1;
        if (heightPx > MAX_PX) heightPx = MAX_PX;
        float topY = BASE_Y + heightPx / 16.0F;
        float fillFracV = heightPx / 16.0F; // V-extent on side sprites scales with height

        int tint = FoodTints.colorFor(state.item);
        int light = state.packedLightAbove;
        TextureAtlasSprite top = this.sprites.get(TOP_SPRITE);
        TextureAtlasSprite side = this.sprites.get(SIDE_SPRITE);

        // Top + bottom share the same square 12x12 region; we sample
        // [INSET..OPP] of UV on the top sprite so it tiles proportionally.
        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entitySolid(top.atlasLocation()),
                (pose, buf) -> renderTopAndBottom(pose, buf, top, BASE_Y, topY, tint, light));

        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entitySolid(side.atlasLocation()),
                (pose, buf) -> renderSides(pose, buf, side, BASE_Y, topY, fillFracV, tint, light));
    }

    private static void renderTopAndBottom(PoseStack.Pose pose, VertexConsumer buf,
                                           TextureAtlasSprite s, float yMin, float yMax,
                                           int color, int light) {
        float u0 = s.getU(INSET);
        float u1 = s.getU(OPP);
        float v0 = s.getV(INSET);
        float v1 = s.getV(OPP);

        // Top face (normal +Y), CCW seen from +Y
        addVertex(pose, buf, INSET, yMax, INSET, color, u0, v0, light, 0, 1, 0);
        addVertex(pose, buf, INSET, yMax, OPP,   color, u0, v1, light, 0, 1, 0);
        addVertex(pose, buf, OPP,   yMax, OPP,   color, u1, v1, light, 0, 1, 0);
        addVertex(pose, buf, OPP,   yMax, INSET, color, u1, v0, light, 0, 1, 0);

        // Bottom face (normal -Y), CCW seen from -Y
        addVertex(pose, buf, INSET, yMin, INSET, color, u0, v0, light, 0, -1, 0);
        addVertex(pose, buf, OPP,   yMin, INSET, color, u1, v0, light, 0, -1, 0);
        addVertex(pose, buf, OPP,   yMin, OPP,   color, u1, v1, light, 0, -1, 0);
        addVertex(pose, buf, INSET, yMin, OPP,   color, u0, v1, light, 0, -1, 0);
    }

    private static void renderSides(PoseStack.Pose pose, VertexConsumer buf,
                                    TextureAtlasSprite s, float yMin, float yMax,
                                    float fillFracV, int color, int light) {
        float u0 = s.getU(INSET);
        float u1 = s.getU(OPP);
        // V grows downward on textures, so V at top of cuboid is sampled higher,
        // i.e. v0 = sprite top (offset 0), v1 = offset = fillFracV.
        float v0 = s.getV(0.0F);
        float v1 = s.getV(fillFracV);

        // North face (-Z), normal -Z; vertices CCW seen from -Z
        addVertex(pose, buf, OPP,   yMax, INSET, color, u0, v0, light, 0, 0, -1);
        addVertex(pose, buf, OPP,   yMin, INSET, color, u0, v1, light, 0, 0, -1);
        addVertex(pose, buf, INSET, yMin, INSET, color, u1, v1, light, 0, 0, -1);
        addVertex(pose, buf, INSET, yMax, INSET, color, u1, v0, light, 0, 0, -1);

        // South face (+Z), normal +Z
        addVertex(pose, buf, INSET, yMax, OPP,   color, u0, v0, light, 0, 0, 1);
        addVertex(pose, buf, INSET, yMin, OPP,   color, u0, v1, light, 0, 0, 1);
        addVertex(pose, buf, OPP,   yMin, OPP,   color, u1, v1, light, 0, 0, 1);
        addVertex(pose, buf, OPP,   yMax, OPP,   color, u1, v0, light, 0, 0, 1);

        // West face (-X), normal -X
        addVertex(pose, buf, INSET, yMax, INSET, color, u0, v0, light, -1, 0, 0);
        addVertex(pose, buf, INSET, yMin, INSET, color, u0, v1, light, -1, 0, 0);
        addVertex(pose, buf, INSET, yMin, OPP,   color, u1, v1, light, -1, 0, 0);
        addVertex(pose, buf, INSET, yMax, OPP,   color, u1, v0, light, -1, 0, 0);

        // East face (+X), normal +X
        addVertex(pose, buf, OPP,   yMax, OPP,   color, u0, v0, light, 1, 0, 0);
        addVertex(pose, buf, OPP,   yMin, OPP,   color, u0, v1, light, 1, 0, 0);
        addVertex(pose, buf, OPP,   yMin, INSET, color, u1, v1, light, 1, 0, 0);
        addVertex(pose, buf, OPP,   yMax, INSET, color, u1, v0, light, 1, 0, 0);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer buf,
                                  float x, float y, float z, int color, float u, float v, int light,
                                  float nx, float ny, float nz) {
        buf.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    public static class HayFeederContentsRenderState extends BlockEntityRenderState {
        public @Nullable Item item;
        public int count;
        public int packedLightAbove;
    }
}
