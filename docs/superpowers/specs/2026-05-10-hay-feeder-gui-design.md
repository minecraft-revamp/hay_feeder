# Hay Feeder — GUI-based interaction

**Date:** 2026-05-10
**Status:** Approved — ready for implementation plan
**Mod:** `hay_feeder`
**Supersedes parts of:** [`2026-05-10-hay-feeder-ux-polish-design.md`](2026-05-10-hay-feeder-ux-polish-design.md) — specifically the BER axis (axis 2). Stage models and refill-feedback (axes 1 and 3) carry over.

## Summary

Replace the right-click-to-fill interaction with a chest-like GUI. The player right-clicks the bale and a window opens containing one slot (the bale's stored food) plus the standard 36-slot player inventory. Drag-and-drop is native (provided by `AbstractContainerMenu`); the slot enforces the whitelist constraint and the same-type-at-a-time invariant, with capacity bumped from 8 to 64 (a full stack). The BER (floating item indicator) is removed — the GUI is now the canonical content view. The 3-stage block model stays, but the blockstate is migrated from `IntegerProperty FEEDS_LEFT(0,8)` to `EnumProperty<FillStage>` with three values `{EMPTY, HALF, FULL}`.

## Locked decisions

| Question | Decision |
|---|---|
| Right-click behavior | Opens `HayFeederMenu`. No quick-fill, no shift-modifier — single mode. |
| Slot count | 1 (food slot) + 36 (player inventory) |
| Slot constraint | `AcceptedFoods.isAccepted(stack) && (contents.isEmpty() || contents.is(stack.getItem()))` |
| Capacity | 64 (full stack) |
| Blockstate | `EnumProperty<FillStage> FILL_STAGE` with `EMPTY`, `HALF`, `FULL` |
| Stage→count mapping | `0 → EMPTY` · `1..63 → HALF` · `64 → FULL` |
| BER | Removed |
| Refill feedback | Migrated from `tryRefill` to `Container.setItem` — fires on count↗ transition |
| Stage models | Unchanged (3 JSON files from previous iteration are reused) |

## Architecture

### Unit 1: `FillStage` enum (new)

```java
package com.hayfeeder.feature.feeder;

import net.minecraft.util.StringRepresentable;

public enum FillStage implements StringRepresentable {
    EMPTY("empty"),
    HALF("half"),
    FULL("full");

    public static final int FULL_THRESHOLD = 64;

    private final String name;

    FillStage(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }

    public static FillStage fromCount(int count) {
        if (count <= 0) return EMPTY;
        if (count >= FULL_THRESHOLD) return FULL;
        return HALF;
    }
}
```

### Unit 2: `HayFeederBlock` (modified)

Replace `IntegerProperty FEEDS_LEFT` with `EnumProperty<FillStage> FILL_STAGE`.

```java
public static final EnumProperty<FillStage> FILL_STAGE = EnumProperty.create("fill_stage", FillStage.class);
```

`createBlockStateDefinition` adds `FILL_STAGE` instead of `FEEDS_LEFT`. Default state: `FillStage.EMPTY`.

`useItemOn` no longer calls `tryRefill`. New body:

```java
@Override
protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                      Player player, InteractionHand hand, BlockHitResult hit) {
    if (level.isClientSide()) {
        return InteractionResult.SUCCESS;
    }
    if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
        player.openMenu(be, pos);
    }
    return InteractionResult.SUCCESS_SERVER;
}
```

`useWithoutItem` is also overridden to open the menu (so a player with empty hand can also open it).

`MAX_FEEDS = 8` and the `IntegerProperty` declaration are removed. References across the codebase migrate.

### Unit 3: `HayFeederBlockEntity` (modified) — now implements `Container` and `MenuProvider`

```java
public class HayFeederBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int CAPACITY = 64;
    public static final double FEED_RADIUS = 6.0;

    private ItemStack contents = ItemStack.EMPTY;

    public HayFeederBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAY_FEEDER.get(), pos, state);
    }

    public ItemStack getContents() { return contents; }

    // Container ---------------------------------------------------------------

    @Override
    public int getContainerSize() { return 1; }

    @Override
    public boolean isEmpty() { return contents.isEmpty(); }

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
        ItemStack old = this.contents;
        this.contents = stack;
        if (stack.getCount() > CAPACITY) stack.setCount(CAPACITY);
        boolean grew = stack.getCount() > old.getCount();
        syncToWorld();
        if (grew && level instanceof ServerLevel server) {
            BlockPos pos = getBlockPos();
            server.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()),
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

    // MenuProvider ------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.hay_feeder.hay_feeder");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new HayFeederMenu(containerId, playerInventory, this);
    }

    // Internal ----------------------------------------------------------------

    private void syncToWorld() {
        setChanged();
        if (level != null) {
            level.setBlock(getBlockPos(),
                    getBlockState().setValue(HayFeederBlock.FILL_STAGE, FillStage.fromCount(contents.getCount())),
                    Block.UPDATE_ALL);
        }
    }

    // tickFeeding, persistence, getUpdateTag/getUpdatePacket, computeAbsorb removed/migrated as needed
}
```

`tickFeeding`'s blockstate update changes from `FEEDS_LEFT` (int) to `FILL_STAGE` (enum). The `contents.shrink(1)` mutation stays; afterward `syncToWorld()` is called to update both the blockstate and the BE-dirty flag.

`tryRefill` is removed.

`computeAbsorb` is removed (no longer needed — the Slot's `mayPlace` constraint replaces it).

`getUpdateTag` / `getUpdatePacket` (added in the persistence fix) **stay** — needed for client-side BE state sync.

`loadAdditional` / `saveAdditional` stay, persisting `contents` via `ItemStack.CODEC`.

### Unit 4: `HayFeederMenu` (new) — `AbstractContainerMenu`

```java
public class HayFeederMenu extends AbstractContainerMenu {
    private final Container container;

    // Server-side ctor
    public HayFeederMenu(int containerId, Inventory playerInventory, Container container) {
        super(ModMenuTypes.HAY_FEEDER.get(), containerId);
        this.container = container;
        checkContainerSize(container, 1);
        container.startOpen(playerInventory.player);

        addSlot(new HayFeederFoodSlot(container, 0, 80, 35));

        // standard inventory layout
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    // Client-side ctor (data deserialization), used by the Screen
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
        @Override public int getMaxStackSize() { return HayFeederBlockEntity.CAPACITY; }
        @Override public boolean mayPlace(ItemStack stack) { return container.canPlaceItem(0, stack); }
    }
}
```

### Unit 5: `HayFeederScreen` (new, client-only) — `AbstractContainerScreen<HayFeederMenu>`

```java
public class HayFeederScreen extends AbstractContainerScreen<HayFeederMenu> {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath(
            HayFeeder.MOD_ID, "textures/gui/container/hay_feeder.png");

    public HayFeederScreen(HayFeederMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderType::guiTextured, BG, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }
}
```

If the `GuiGraphics.blit` signature differs in MC 26.1, adapt — vanilla `BarrelScreen.java` is a clean reference.

### Unit 6: `ModMenuTypes` (new) — registry of `MenuType`s

```java
public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, HayFeeder.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<HayFeederMenu>> HAY_FEEDER =
            MENU_TYPES.register("hay_feeder",
                    () -> IMenuTypeExtension.create((containerId, inv, data) ->
                            new HayFeederMenu(containerId, inv)));

    private ModMenuTypes() {}
}
```

NeoForge-specific: `IMenuTypeExtension.create` (with no extra data). On Fabric, the equivalent is `new ExtendedScreenHandlerType<>(...)` or `new MenuType<>(factory, FeatureFlags.VANILLA_SET)` depending on the API.

### Unit 7: GUI background texture (new)

`assets/hay_feeder/textures/gui/container/hay_feeder.png` — 256×256 PNG, content area 176×166 in the top-left. Placeholder generated by Pillow: gray container chrome, single 18×18 slot at center, 36-slot player inventory grid below.

### Removed units

- `HayFeederBlockEntityRenderer` (NeoForge file deleted)
- BER registration in `HayFeederClient.onRegisterRenderers` (block deleted; the entire `onRegisterRenderers` listener is removed since there's nothing else to register)
- `tryRefill` and `computeAbsorb` methods in `HayFeederBlockEntity`
- `ComputeAbsorbTest.java` (8 unit tests removed — the constraint logic now lives in `Container.canPlaceItem`)
- `MAX_FEEDS` constant and `FEEDS_LEFT` IntegerProperty in `HayFeederBlock`

## Resource files

### Updated

- `assets/hay_feeder/blockstates/hay_feeder.json` — variants are now `fill_stage=empty/half/full` (3 variants instead of 9)
- `assets/hay_feeder/lang/en_us.json` — add `container.hay_feeder.hay_feeder` (GUI title)

### New

- `assets/hay_feeder/textures/gui/container/hay_feeder.png` — placeholder GUI bg

### Unchanged

- The 3 stage model JSONs (`hay_feeder_full.json`, `hay_feeder_half.json`, `hay_feeder_empty.json`)
- The 4 block textures (hay top/side, trough top/side)
- Item model, recipe

## Two-loader parity

Java content is identical between NeoForge and Fabric. Registry idioms differ:

| Concept | NeoForge | Fabric |
|---|---|---|
| MenuType registration | `IMenuTypeExtension.create(factory)` | `new MenuType<>(factory, FeatureFlags.VANILLA_SET)` (or `ExtendedScreenHandlerType` if data-bearing) |
| Screen registration | `RegisterMenuScreensEvent` (`@SubscribeEvent` on client mod bus) | `MenuScreens.register(MENU_TYPE, HayFeederScreen::new)` in `onInitializeClient` |
| Container interface | Vanilla `net.minecraft.world.Container` | Same |

## Carry-over MC 26.1 API findings (revised)

- `BlockEntityRenderer` rewrite (axis 2 of UX-polish v1) is no longer relevant — BER removed.
- `getUpdateTag(HolderLookup.Provider)` + `getUpdatePacket()` overrides STAY (BE state still needs to sync to clients for the Container UI to be in sync — though the menu uses its own sync path, the BE NBT is needed at chunk load).
- `level.setBlock(... UPDATE_ALL)` / `level.isClientSide()` patterns unchanged.
- New territory: `MenuType` registration, `AbstractContainerMenu`, `Slot`, `AbstractContainerScreen`, `GuiGraphics.blit` — all standard vanilla shapes likely intact in MC 26.1, but some signatures may have moved (e.g. `blit` may take a `RenderType` first arg in 26.1; check `BarrelScreen` decompiled).

## Out of scope

- Sounds/particles when items leave the slot (only on entry — leaving = empty BER quietly)
- Comparator output (still skipped)
- Hopper auto-fill into the slot (could be added later via Container exposure)
- Custom GUI background art beyond placeholder
- Refining `FillStage.HALF` to multiple sub-stages (e.g. quarter/three-quarter) — keep 3 stages

## Plan impact

The in-flight UX-polish plan `2026-05-10-hay-feeder-ux-polish.md` has Tasks 1-6 done on NeoForge (cleanup, refill feedback, textures, models, blockstate remap, BER). Tasks 7-11 (manual test, Fabric port) were pending. **This pivot supersedes the BER-related work entirely:**

- Task 6's BER class file gets deleted
- Task 6's HayFeederClient registration block gets simplified
- Task 7 (NeoForge manual test) becomes obsolete in its current form — replaced by the new GUI manual test
- Tasks 8-11 (Fabric port of refill feedback / assets / BER / manual test) are reshaped: refill feedback moves to `Container.setItem`, assets mirror with the new blockstate enum, no BER, and manual test exercises the GUI

A new implementation plan should be written that handles both:
1. The forward GUI work (Menu, MenuType, Screen, Container conversion, blockstate enum migration)
2. The cleanup/migration of the partial UX-polish work (delete BER, migrate refill feedback, blockstate refactor)

## References

- v1 feeding-logic spec: [`2026-05-09-hay-feeder-feeding-design.md`](2026-05-09-hay-feeder-feeding-design.md)
- v2 UX-polish spec (partially superseded): [`2026-05-10-hay-feeder-ux-polish-design.md`](2026-05-10-hay-feeder-ux-polish-design.md)
- v1 plan (executed): [`../plans/2026-05-09-hay-feeder-feeding-logic.md`](../plans/2026-05-09-hay-feeder-feeding-logic.md)
- v2 UX-polish plan (Tasks 1-6 done, 7-11 superseded): [`../plans/2026-05-10-hay-feeder-ux-polish.md`](../plans/2026-05-10-hay-feeder-ux-polish.md)
