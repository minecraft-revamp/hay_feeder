# Hay Feeder — UX & game-feel polish

**Date:** 2026-05-10
**Status:** Approved — ready for implementation plan
**Mod:** `hay_feeder` (part of the [Minecraft Revamp](../../../../README.md) collective)
**Builds on:** [`2026-05-09-hay-feeder-feeding-design.md`](2026-05-09-hay-feeder-feeding-design.md) (the v1 feeding-logic spec — implemented, working in-game on 2026-05-10)

## Summary

The v1 feeding logic is functional but the player can't **see** what it's doing: the bale is visually identical to vanilla `hay_block`, the contents are invisible, and refill is silent. This iteration adds three independent UX layers:

1. **Distinct block model with 3 fill stages** (full / half / empty) on a wooden trough base, replacing the vanilla `hay_block` model parent.
2. **A `BlockEntityRenderer`** that floats the stored `ItemStack` above the bale with rotation, bobbing, and a numeric count — so the player can read contents at a glance.
3. **Refill feedback**: when right-click absorbs food, play the composter fill-success sound and spawn item-typed particles. Distinct from the existing feed-event feedback.

The form factor (bale on a trough) is a **v1 placeholder** — the author may iterate on the bale shape later. The BER and refill-feedback logic are model-agnostic and survive any model swap.

## Locked decisions

| Question | Decision |
|---|---|
| **Stage count** | 3 stages: full / half / empty |
| **Stage→state mapping** | `feeds_left == 8` → full · `feeds_left ∈ [1..7]` → half · `feeds_left == 0` → empty |
| **Visual identity element** | Wooden trough at the base, slightly wider than the bale (1-pixel overhang on each side) |
| **BER style** | Floating item rotating on Y-axis + vertical bobbing + small numeric count (1-8) |
| **BER visibility** | Renders only when `contents` is non-empty |
| **Refill sound** | `block.composter.fill_success` |
| **Refill particles** | `ItemParticleOption(ParticleTypes.ITEM, absorbedStack)` — type-specific |
| **Refill particle count** | 5-8 in a small upward spray |
| **Feed event feedback** | Unchanged from v1 spec: `block.grass.break` + `ParticleTypes.HAPPY_VILLAGER` |
| **Iteration on form factor** | Open — implementation must not couple BER or feedback logic to the model |

## Stage→model mapping

The blockstate JSON (`assets/hay_feeder/blockstates/hay_feeder.json`) currently has 9 variants pointing to the same model. Updated mapping:

```
feeds_left=0       → hay_feeder:block/hay_feeder_empty
feeds_left=1..7    → hay_feeder:block/hay_feeder_half
feeds_left=8       → hay_feeder:block/hay_feeder_full
```

No Java change required for this — pure resource-pack remap.

## Architecture

Three independent units. Each can ship without the others; together they form the full UX upgrade.

### Unit 1: Block models (3 stages + trough)

**Files (mirrored both loaders):**
- `assets/hay_feeder/models/block/hay_feeder_full.json` — bale on trough, hay overflowing
- `assets/hay_feeder/models/block/hay_feeder_half.json` — bale on trough, hay flush with trough top
- `assets/hay_feeder/models/block/hay_feeder_empty.json` — trough with thin hay layer at the bottom
- `assets/hay_feeder/models/item/hay_feeder.json` — points at `block/hay_feeder_full` for inventory render
- `assets/hay_feeder/textures/block/hay_top.png`, `hay_side.png`, `trough_side.png`, `trough_top.png` — placeholder PNGs derived from vanilla `hay_block` and `oak_planks`

The **trough geometry** sits in each model's `elements` array as a thin (e.g. 2-pixel-tall) wider cube under the main bale cube. Both cubes get their own face textures.

The author will provide refined PNG textures later. v1 implementation can ship with simple programmatically-derived placeholders (vanilla hay color shifted darker for the trough planks).

### Unit 2: `HayFeederBlockEntityRenderer` (client-side)

**Files:**
- `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntityRenderer.java`
- `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntityRenderer.java`

Each implements `BlockEntityRenderer<HayFeederBlockEntity>` with the same `render(BE, partialTick, poseStack, buffers, packedLight, packedOverlay)` signature.

**Render logic:**
```
ItemStack contents = be.getContents();
if (contents.isEmpty()) return;

float yaw = (be.getLevel().getGameTime() + partialTick) * 2.0f;        // 2 deg/tick
float bobOffset = Mth.sin((be.getLevel().getGameTime() + partialTick) * 0.05f) * 0.05f;

poseStack.pushPose();
poseStack.translate(0.5, 1.25 + bobOffset, 0.5);
poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
poseStack.scale(0.6f, 0.6f, 0.6f);
itemRenderer.renderStatic(contents, ItemDisplayContext.GROUND, packedLight, packedOverlay, poseStack, buffers, level, 0);
poseStack.popPose();

// numeric count
if (contents.getCount() > 1) {
    String text = String.valueOf(contents.getCount());
    poseStack.pushPose();
    poseStack.translate(0.5, 1.05 + bobOffset, 0.5);
    poseStack.mulPose(camera.rotation());                 // billboard toward camera
    poseStack.scale(-0.025f, -0.025f, 0.025f);
    Font font = Minecraft.getInstance().font;
    float w = -font.width(text) / 2.0f;
    font.drawInBatch(text, w, 0, 0xFFFFFF, false, poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, packedLight);
    poseStack.popPose();
}
```

The exact `Font.drawInBatch` signature and the camera-billboard accessor must be verified against MC 26.1 during implementation; the formula above is canonical for MC 1.21.x and likely carries over.

**Registration** (loader-specific):
- **NeoForge:** add a `@SubscribeEvent` handler in `HayFeederClient.java` for `EntityRenderersEvent.RegisterRenderers`, calling `event.registerBlockEntityRenderer(ModBlockEntities.HAY_FEEDER.get(), HayFeederBlockEntityRenderer::new)`.
- **Fabric:** in `HayFeederFabricClient.onInitializeClient`, call `BlockEntityRendererRegistry.register(ModBlockEntities.HAY_FEEDER, HayFeederBlockEntityRenderer::new)` (or `BlockEntityRenderers.register(...)` — MC 26.1 may have renamed the Fabric helper; verify at impl).

The BER class itself is identical between loaders (only package differs).

### Unit 3: Refill feedback

Modify `HayFeederBlockEntity.tryRefill(ItemStack incoming)`. Currently the method already absorbs and updates the blockstate. Add **after the successful-absorb branch, before `return absorbed`**:

```java
if (level instanceof ServerLevel serverLevel) {
    BlockPos pos = getBlockPos();
    serverLevel.sendParticles(
        new ItemParticleOption(ParticleTypes.ITEM, incoming),
        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
        6, 0.2, 0.05, 0.2, 0.05);
    serverLevel.playSound(null, pos, SoundEvents.COMPOSTER_FILL_SUCCESS,
        SoundSource.BLOCKS, 1.0f, 1.0f);
}
```

`incoming` here is the player's stack BEFORE shrinking — its `getItem()` and remaining count are still meaningful for the particle type (we want particles of the food being absorbed, not of nothing).

The existing feed-event feedback in `tickFeeding` is unchanged.

## File map

### NeoForge — files to create
```
neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntityRenderer.java
neoforge/src/main/resources/assets/hay_feeder/models/block/hay_feeder_full.json
neoforge/src/main/resources/assets/hay_feeder/models/block/hay_feeder_half.json
neoforge/src/main/resources/assets/hay_feeder/models/block/hay_feeder_empty.json
neoforge/src/main/resources/assets/hay_feeder/textures/block/hay_top.png
neoforge/src/main/resources/assets/hay_feeder/textures/block/hay_side.png
neoforge/src/main/resources/assets/hay_feeder/textures/block/trough_top.png
neoforge/src/main/resources/assets/hay_feeder/textures/block/trough_side.png
```

### NeoForge — files to modify
- `neoforge/src/main/java/com/hayfeeder/client/HayFeederClient.java` — register BER on `EntityRenderersEvent.RegisterRenderers`
- `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java` — add particles + sound in `tryRefill`; **remove all `[HF-DEBUG]` log statements** added during 2026-05-10 debugging
- `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java` — **remove all `[HF-DEBUG]` log statements**
- `neoforge/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json` — remap 9 variants to 3 model targets
- `neoforge/src/main/resources/assets/hay_feeder/models/item/hay_feeder.json` — point parent at `block/hay_feeder_full`

### Fabric — mirror

Same structure, package paths under `com.hayfeeder.fabric.*` (flat). Resource paths identical. Registration uses Fabric API, not NeoForge events.

## Out of scope

- **Idle activity feedback** (continuous wisps when feeder has contents and animals nearby) — explicitly skipped during brainstorm
- **Tooltip / Jade-style hover HUD** — explicitly skipped
- **Form factor refinement** — author will iterate after seeing v1 in-game; the design must not couple to specific model geometry
- **Tinted bale per food type** — rejected in favor of BER (more legible)
- **Sound for the feed event** — already in v1 spec, no change here

## Carry-over MC 26.1 API findings

Implementation must respect the API drift discoveries from the v1 implementation, especially:

- `ItemParticleOption(ParticleType, ItemStack)` — verify the constructor accepts a non-data-bound `ItemStack` (since component binding is still incomplete in some contexts).
- `BlockEntityRenderer.render` signature in MC 26.1 — may have additional parameters for `Vec3 cameraPos` or similar; verify against vanilla decompiled `BlockEntityRenderer.java`.
- Fabric BER registration: the helper class may be `BlockEntityRendererRegistry`, `BlockEntityRenderers`, or `BlockEntityRendererFactories` — verify and adapt.
- Removing `HF-DEBUG` log lines: there are 5 such log statements across `HayFeederBlock.java` and `HayFeederBlockEntity.java`. All must go in this iteration's commit.

## References

- v1 feeding-logic spec: [`2026-05-09-hay-feeder-feeding-design.md`](2026-05-09-hay-feeder-feeding-design.md)
- v1 implementation plan: [`../plans/2026-05-09-hay-feeder-feeding-logic.md`](../plans/2026-05-09-hay-feeder-feeding-logic.md)
- Per-mod CLAUDE.md: [`../../../CLAUDE.md`](../../../CLAUDE.md)
- Collective vision: [`../../../../CLAUDE.md`](../../../../CLAUDE.md)
- Brainstorm transcript: 2026-05-10 conversation with Claude
