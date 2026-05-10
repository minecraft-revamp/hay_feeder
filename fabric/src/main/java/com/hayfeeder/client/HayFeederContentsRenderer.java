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
 *
 * <p>When this feeder has neighbours that also contain food, additional 2-px
 * bridging cuboids are rendered into the gaps so connected feeders read as one
 * continuous trough. Bridges always use this feeder's tint; mismatched-food
 * neighbours still bridge (matching the old static-extension behaviour).
 */
public class HayFeederContentsRenderer
        implements BlockEntityRenderer<HayFeederBlockEntity, HayFeederContentsRenderer.HayFeederContentsRenderState> {

    private static final SpriteId TOP_SPRITE = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("hay_block_top");
    private static final SpriteId SIDE_SPRITE = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("hay_block_side");

    // Local block-space inset (in 1/16 units): trough interior is 2..14 on x/z.
    private static final float INSET = 2.0F / 16.0F;
    private static final float OPP   = 14.0F / 16.0F;
    private static final float EDGE_LO = 0.0F;
    private static final float EDGE_HI = 16.0F / 16.0F;
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

        // Sample neighbour fill levels for bridging. A neighbour contributes
        // only if it is also a hay feeder with non-empty contents; we don't
        // sample its tint or item — bridges always render in this feeder's
        // colour, matching the old static-extension model behaviour.
        BlockPos pos = be.getBlockPos();
        state.countN  = neighbourCount(be, pos.north());
        state.countS  = neighbourCount(be, pos.south());
        state.countE  = neighbourCount(be, pos.east());
        state.countW  = neighbourCount(be, pos.west());
        state.countNE = neighbourCount(be, pos.north().east());
        state.countNW = neighbourCount(be, pos.north().west());
        state.countSE = neighbourCount(be, pos.south().east());
        state.countSW = neighbourCount(be, pos.south().west());
    }

    private static int neighbourCount(HayFeederBlockEntity origin, BlockPos pos) {
        var level = origin.getLevel();
        if (level == null) return 0;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof HayFeederBlockEntity feeder)) return 0;
        var stack = feeder.getContents();
        return stack.isEmpty() ? 0 : stack.getCount();
    }

    @Override
    public void submit(HayFeederContentsRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item == null || state.count <= 0) return;
        int selfPx = heightPx(state.count);
        float topY = BASE_Y + selfPx / 16.0F;

        int tint = FoodTints.colorFor(state.item);
        int light = state.packedLightAbove;
        TextureAtlasSprite top = this.sprites.get(TOP_SPRITE);
        TextureAtlasSprite side = this.sprites.get(SIDE_SPRITE);

        // Main central cuboid (12x12 footprint, 2..14 on x/z).
        submitCuboid(poseStack, collector, top, side,
                INSET, OPP, INSET, OPP, BASE_Y, topY, tint, light);

        // Cardinal bridges: present iff that neighbour is a non-empty feeder.
        // Height is min(self, neighbour) so the bridge meets the neighbour's
        // own central cube flush at the shared boundary.
        submitCardinal(state.countE,  selfPx, poseStack, collector, top, side,
                OPP, EDGE_HI, INSET, OPP, tint, light);
        submitCardinal(state.countW,  selfPx, poseStack, collector, top, side,
                EDGE_LO, INSET, INSET, OPP, tint, light);
        submitCardinal(state.countS,  selfPx, poseStack, collector, top, side,
                INSET, OPP, OPP, EDGE_HI, tint, light);
        submitCardinal(state.countN,  selfPx, poseStack, collector, top, side,
                INSET, OPP, EDGE_LO, INSET, tint, light);

        // Corner bridges: only when the two adjacent cardinals AND the diagonal
        // are all populated (no L-shaped half-plug). Height is the min of all
        // four contributors so the corner can never poke above any of them.
        submitCorner(state.countE, state.countS, state.countSE, selfPx,
                poseStack, collector, top, side, OPP, EDGE_HI, OPP, EDGE_HI, tint, light);
        submitCorner(state.countW, state.countS, state.countSW, selfPx,
                poseStack, collector, top, side, EDGE_LO, INSET, OPP, EDGE_HI, tint, light);
        submitCorner(state.countE, state.countN, state.countNE, selfPx,
                poseStack, collector, top, side, OPP, EDGE_HI, EDGE_LO, INSET, tint, light);
        submitCorner(state.countW, state.countN, state.countNW, selfPx,
                poseStack, collector, top, side, EDGE_LO, INSET, EDGE_LO, INSET, tint, light);
    }

    private static int heightPx(int count) {
        int h = (int) Math.ceil(count * (double) MAX_PX / HayFeederBlockEntity.CAPACITY);
        if (h < 1) h = 1;
        if (h > MAX_PX) h = MAX_PX;
        return h;
    }

    private void submitCardinal(int neighbourCount, int selfPx,
                                PoseStack poseStack, SubmitNodeCollector collector,
                                TextureAtlasSprite top, TextureAtlasSprite side,
                                float xMin, float xMax, float zMin, float zMax,
                                int tint, int light) {
        if (neighbourCount <= 0) return;
        int px = Math.min(selfPx, heightPx(neighbourCount));
        if (px <= 0) return;
        float topY = BASE_Y + px / 16.0F;
        submitCuboid(poseStack, collector, top, side,
                xMin, xMax, zMin, zMax, BASE_Y, topY, tint, light);
    }

    private void submitCorner(int cardinalA, int cardinalB, int diagonal, int selfPx,
                              PoseStack poseStack, SubmitNodeCollector collector,
                              TextureAtlasSprite top, TextureAtlasSprite side,
                              float xMin, float xMax, float zMin, float zMax,
                              int tint, int light) {
        if (cardinalA <= 0 || cardinalB <= 0 || diagonal <= 0) return;
        int px = Math.min(selfPx,
                Math.min(heightPx(cardinalA),
                Math.min(heightPx(cardinalB), heightPx(diagonal))));
        if (px <= 0) return;
        float topY = BASE_Y + px / 16.0F;
        submitCuboid(poseStack, collector, top, side,
                xMin, xMax, zMin, zMax, BASE_Y, topY, tint, light);
    }

    private static void submitCuboid(PoseStack poseStack, SubmitNodeCollector collector,
                                     TextureAtlasSprite top, TextureAtlasSprite side,
                                     float xMin, float xMax, float zMin, float zMax,
                                     float yMin, float yMax,
                                     int tint, int light) {
        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entitySolid(top.atlasLocation()),
                (pose, buf) -> renderTopAndBottom(pose, buf, top,
                        xMin, xMax, zMin, zMax, yMin, yMax, tint, light));
        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entitySolid(side.atlasLocation()),
                (pose, buf) -> renderSides(pose, buf, side,
                        xMin, xMax, zMin, zMax, yMin, yMax, tint, light));
    }

    private static void renderTopAndBottom(PoseStack.Pose pose, VertexConsumer buf,
                                           TextureAtlasSprite s,
                                           float xMin, float xMax, float zMin, float zMax,
                                           float yMin, float yMax,
                                           int color, int light) {
        // Sample the UV region matching the cuboid's x/z footprint so adjacent
        // cuboids tile seamlessly across the connected trough.
        float u0 = s.getU(xMin);
        float u1 = s.getU(xMax);
        float v0 = s.getV(zMin);
        float v1 = s.getV(zMax);

        // Top face (normal +Y), CCW seen from +Y
        addVertex(pose, buf, xMin, yMax, zMin, color, u0, v0, light, 0, 1, 0);
        addVertex(pose, buf, xMin, yMax, zMax, color, u0, v1, light, 0, 1, 0);
        addVertex(pose, buf, xMax, yMax, zMax, color, u1, v1, light, 0, 1, 0);
        addVertex(pose, buf, xMax, yMax, zMin, color, u1, v0, light, 0, 1, 0);

        // Bottom face (normal -Y), CCW seen from -Y
        addVertex(pose, buf, xMin, yMin, zMin, color, u0, v0, light, 0, -1, 0);
        addVertex(pose, buf, xMax, yMin, zMin, color, u1, v0, light, 0, -1, 0);
        addVertex(pose, buf, xMax, yMin, zMax, color, u1, v1, light, 0, -1, 0);
        addVertex(pose, buf, xMin, yMin, zMax, color, u0, v1, light, 0, -1, 0);
    }

    private static void renderSides(PoseStack.Pose pose, VertexConsumer buf,
                                    TextureAtlasSprite s,
                                    float xMin, float xMax, float zMin, float zMax,
                                    float yMin, float yMax,
                                    int color, int light) {
        float uX0 = s.getU(xMin);
        float uX1 = s.getU(xMax);
        float uZ0 = s.getU(zMin);
        float uZ1 = s.getU(zMax);
        // Always show the full hay_block_side sprite (top binding -> hay -> bottom
        // binding) on every face, regardless of how short the cuboid is. The texture
        // is squeezed vertically at low fill, but both bindings stay visible so the
        // shape always reads as a complete bale.
        float v0 = s.getV(0.0F);
        float v1 = s.getV(1.0F);

        // North face (-Z), normal -Z; vertices CCW seen from -Z
        addVertex(pose, buf, xMax, yMax, zMin, color, uX0, v0, light, 0, 0, -1);
        addVertex(pose, buf, xMax, yMin, zMin, color, uX0, v1, light, 0, 0, -1);
        addVertex(pose, buf, xMin, yMin, zMin, color, uX1, v1, light, 0, 0, -1);
        addVertex(pose, buf, xMin, yMax, zMin, color, uX1, v0, light, 0, 0, -1);

        // South face (+Z), normal +Z
        addVertex(pose, buf, xMin, yMax, zMax, color, uX0, v0, light, 0, 0, 1);
        addVertex(pose, buf, xMin, yMin, zMax, color, uX0, v1, light, 0, 0, 1);
        addVertex(pose, buf, xMax, yMin, zMax, color, uX1, v1, light, 0, 0, 1);
        addVertex(pose, buf, xMax, yMax, zMax, color, uX1, v0, light, 0, 0, 1);

        // West face (-X), normal -X
        addVertex(pose, buf, xMin, yMax, zMin, color, uZ0, v0, light, -1, 0, 0);
        addVertex(pose, buf, xMin, yMin, zMin, color, uZ0, v1, light, -1, 0, 0);
        addVertex(pose, buf, xMin, yMin, zMax, color, uZ1, v1, light, -1, 0, 0);
        addVertex(pose, buf, xMin, yMax, zMax, color, uZ1, v0, light, -1, 0, 0);

        // East face (+X), normal +X
        addVertex(pose, buf, xMax, yMax, zMax, color, uZ0, v0, light, 1, 0, 0);
        addVertex(pose, buf, xMax, yMin, zMax, color, uZ0, v1, light, 1, 0, 0);
        addVertex(pose, buf, xMax, yMin, zMin, color, uZ1, v1, light, 1, 0, 0);
        addVertex(pose, buf, xMax, yMax, zMin, color, uZ1, v0, light, 1, 0, 0);
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
        // Neighbour fill counts for bridging. 0 = absent / non-feeder / empty.
        public int countN, countS, countE, countW;
        public int countNE, countNW, countSE, countSW;
    }
}
