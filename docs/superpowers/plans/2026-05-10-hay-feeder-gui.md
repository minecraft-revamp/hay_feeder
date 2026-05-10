# Hay Feeder GUI pivot — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the right-click-to-fill interaction with a chest-like GUI. Convert the BlockEntity to a `Container` + `MenuProvider`, add a Menu/Screen pair, migrate the blockstate from `feeds_left` IntegerProperty to a `fill_stage` EnumProperty, bump capacity to 64, and remove the now-redundant BlockEntityRenderer.

**Architecture:** Single-slot vanilla-style container UI. The slot enforces `AcceptedFoods.isAccepted` whitelist + same-type-at-a-time invariant via `Container.canPlaceItem`. Refill feedback (composter sound + item particles) migrates from the deleted `tryRefill` method to `Container.setItem`, fired on count↗ transitions. Stage→count mapping is decoupled from blockstate (3 visual stages: empty/half/full) regardless of exact count (0-64).

**Tech Stack:** Minecraft 26.1.2, NeoForge 26.1.2.41-beta, Fabric Loom 1.16.1, Java 21/25, Pillow for placeholder GUI texture.

**Spec:** [`../specs/2026-05-10-hay-feeder-gui-design.md`](../specs/2026-05-10-hay-feeder-gui-design.md)

---

## Toolchain prelude

```bash
# NeoForge
export JAVA_HOME=$HOME/.local/jdks/current PATH=$JAVA_HOME/bin:$PATH

# Fabric
export JAVA_HOME=$HOME/.local/jdks/current25 PATH=$JAVA_HOME/bin:$PATH
```

---

### Task 1: Blockstate refactor (NeoForge)

**Files:**
- Create: `neoforge/src/main/java/com/hayfeeder/feature/feeder/FillStage.java`
- Modify: `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java` (replace `FEEDS_LEFT` int prop with `FILL_STAGE` enum prop)
- Modify: `neoforge/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json` (3 variants instead of 9)

This refactor lands the `fill_stage` enum and the new blockstate variants in one atomic step. Subsequent tasks will reference `HayFeederBlock.FILL_STAGE` and `FillStage.fromCount(...)`.

> Note: `HayFeederBlockEntity` and the existing `tryRefill`/`tickFeeding` reference `HayFeederBlock.FEEDS_LEFT`. To keep the BE compiling during this task, **temporarily** keep both the old `FEEDS_LEFT` declaration as a private final IntegerProperty and add the new `FILL_STAGE`. Then in Task 2 the BE migrates and the old property is removed.
>
> **Simpler alternative**: do this task and Task 2 together as one atomic "blockstate + BE migration". The implementer can pick. If atomic, name the commit accordingly.

- [ ] **Step 1: Create `FillStage.java`**

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

- [ ] **Step 2: Update `HayFeederBlock.java` to use `FILL_STAGE`**

Read the current file. Replace:
- `import net.minecraft.world.level.block.state.properties.IntegerProperty;` → `import net.minecraft.world.level.block.state.properties.EnumProperty;`
- `public static final int MAX_FEEDS = 8;` → remove
- `public static final IntegerProperty FEEDS_LEFT = IntegerProperty.create("feeds_left", 0, MAX_FEEDS);` → `public static final EnumProperty<FillStage> FILL_STAGE = EnumProperty.create("fill_stage", FillStage.class);`
- In constructor: `setValue(FEEDS_LEFT, 0)` → `setValue(FILL_STAGE, FillStage.EMPTY)`
- In `createBlockStateDefinition`: `builder.add(FEEDS_LEFT)` → `builder.add(FILL_STAGE)`

Leave the rest of the class (newBlockEntity, randomTick, useItemOn, playerWillDestroy) unchanged — these will be updated in later tasks. Build will fail because the BE still references `FEEDS_LEFT`; that's expected and will be fixed in Task 2.

- [ ] **Step 3: Update blockstate JSON**

Overwrite `neoforge/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json` with:

```json
{
  "variants": {
    "fill_stage=empty": { "model": "hay_feeder:block/hay_feeder_empty" },
    "fill_stage=half":  { "model": "hay_feeder:block/hay_feeder_half" },
    "fill_stage=full":  { "model": "hay_feeder:block/hay_feeder_full" }
  }
}
```

- [ ] **Step 4: Verify (build will fail — that's OK)**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build 2>&1 | tail -20
```

Expected: `BUILD FAILED`. The error should be in `HayFeederBlockEntity.java` referring to `HayFeederBlock.FEEDS_LEFT` (no longer exists) or `MAX_FEEDS`. Confirm the failure is *only* about those references — if there are other unexpected errors, investigate.

- [ ] **Step 5: Do NOT commit yet**

This task is paired with Task 2 — the codebase isn't compileable in between. The combined commit lands at the end of Task 2.

---

### Task 2: BE Container conversion + capacity 64 + refill feedback migration (NeoForge)

**Files:**
- Modify: `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java`
- Delete: `neoforge/src/test/java/com/hayfeeder/feature/feeder/ComputeAbsorbTest.java`

The BE goes from a custom-state holder to a vanilla `Container`. `tryRefill` and `computeAbsorb` are removed; the slot constraint logic moves to `canPlaceItem`. The capacity bumps from 8 to 64. Refill feedback (composter sound + item particles) fires from `setItem` on count↗.

- [ ] **Step 1: Replace `HayFeederBlockEntity.java`**

Overwrite with:

```java
package com.hayfeeder.feature.feeder;

import com.hayfeeder.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class HayFeederBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int CAPACITY = 64;
    public static final double FEED_RADIUS = 6.0;

    private ItemStack contents = ItemStack.EMPTY;

    public HayFeederBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAY_FEEDER.get(), pos, state);
    }

    public ItemStack getContents() { return contents; }

    public void tickFeeding(ServerLevel level, BlockPos pos, BlockState state) {
        if (contents.isEmpty()) return;
        AABB box = AABB.ofSize(pos.getCenter(), FEED_RADIUS * 2, FEED_RADIUS * 2, FEED_RADIUS * 2);
        List<Animal> targets = level.getEntitiesOfClass(Animal.class, box,
                a -> a.isAlive() && a.isFood(contents));
        if (targets.isEmpty()) return;
        for (Animal a : targets) {
            FeedingMechanic.feedAnimal(level, a);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                5, 0.2, 0.05, 0.2, 0.0);
        level.playSound(null, pos, SoundEvents.GRASS_BREAK,
                SoundSource.BLOCKS, 0.3f, 1.0f);
        contents.shrink(1);
        syncToWorld();
    }

    // --- Container --------------------------------------------------------

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return contents.isEmpty(); }

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
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        if (stack.getCount() > CAPACITY) stack.setCount(CAPACITY);
        ItemStack old = this.contents;
        this.contents = stack;
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

    // --- MenuProvider -----------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.hay_feeder.hay_feeder");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new HayFeederMenu(containerId, playerInventory, this);
    }

    // --- Internal ---------------------------------------------------------

    private void syncToWorld() {
        setChanged();
        if (level != null) {
            level.setBlock(getBlockPos(),
                    getBlockState().setValue(HayFeederBlock.FILL_STAGE, FillStage.fromCount(contents.getCount())),
                    Block.UPDATE_ALL);
        }
    }

    // --- Persistence ------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.contents = input.read("contents", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!contents.isEmpty()) {
            output.store("contents", ItemStack.CODEC, contents);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
```

This file references `HayFeederMenu` (not yet created — Task 3). The build will still fail until Task 3 lands. That's expected.

- [ ] **Step 2: Delete `ComputeAbsorbTest.java`**

```bash
rm ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge/src/test/java/com/hayfeeder/feature/feeder/ComputeAbsorbTest.java
```

`computeAbsorb` is gone — the constraint logic now lives in `Container.canPlaceItem` (no separate static helper). The test had value when there was a separate function; now there's nothing to unit-test that doesn't require MC bootstrap.

`AcceptedFoodsTest` stays — `AcceptedFoods.isAccepted(Item)` is still the whitelist primitive used by `canPlaceItem`.

- [ ] **Step 3: Don't commit yet**

Build still fails (HayFeederMenu missing). Combined commit at end of Task 3.

---

### Task 3: HayFeederMenu + ModMenuTypes (NeoForge)

**Files:**
- Create: `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederMenu.java`
- Create: `neoforge/src/main/java/com/hayfeeder/registry/ModMenuTypes.java`
- Modify: `neoforge/src/main/java/com/hayfeeder/HayFeeder.java` (register `ModMenuTypes`)

- [ ] **Step 1: Create `HayFeederMenu.java`**

```java
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

        // Player main inventory (3 rows × 9 cols)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar (9 slots)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** Client-side ctor used by Screen registration. */
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

        @Override public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(0, stack);
        }
    }
}
```

- [ ] **Step 2: Create `ModMenuTypes.java`**

```java
package com.hayfeeder.registry;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.feature.feeder.HayFeederMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, HayFeeder.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<HayFeederMenu>> HAY_FEEDER =
            MENU_TYPES.register("hay_feeder",
                    () -> IMenuTypeExtension.create(
                            (containerId, inv, data) -> new HayFeederMenu(containerId, inv)));

    private ModMenuTypes() {}
}
```

- [ ] **Step 3: Wire `ModMenuTypes` in `HayFeeder.java`**

Modify the constructor to add a 5th register call:

```java
public HayFeeder(IEventBus modEventBus, ModContainer modContainer) {
    ModBlocks.BLOCKS.register(modEventBus);
    ModItems.ITEMS.register(modEventBus);
    ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
    ModMenuTypes.MENU_TYPES.register(modEventBus);
    ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

    LOGGER.info("Hay Feeder initialised");
}
```

Add the import: `import com.hayfeeder.registry.ModMenuTypes;`.

- [ ] **Step 4: Build to verify (now should compile)**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build
```

Use `timeout: 600000` ms. Expected: BUILD SUCCESSFUL. The 9 `AcceptedFoodsTest` tests should still pass (the 8 ComputeAbsorbTest tests are gone).

If `IMenuTypeExtension.create(factory)` doesn't exist or has different semantics in MC 26.1: alternatives include `MenuType<>(factory, FeatureFlags.VANILLA_SET)` or `IMenuTypeExtension.create(IContainerFactory)`. Adapt and report.

- [ ] **Step 5: Commit (combined Tasks 1+2+3)**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/feature/feeder/FillStage.java \
        neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java \
        neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java \
        neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederMenu.java \
        neoforge/src/main/java/com/hayfeeder/registry/ModMenuTypes.java \
        neoforge/src/main/java/com/hayfeeder/HayFeeder.java \
        neoforge/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json
git rm neoforge/src/test/java/com/hayfeeder/feature/feeder/ComputeAbsorbTest.java
git commit -m "refactor(neoforge): convert hay_feeder to Container+Menu interaction model

- New FillStage enum (EMPTY/HALF/FULL) replaces feeds_left IntegerProperty
- HayFeederBlockEntity implements Container + MenuProvider, capacity 64
- New HayFeederMenu (1 food slot + 36 inventory slots) with whitelist constraint
- New ModMenuTypes registry
- Refill feedback (composter sound + item particles) migrates from
  removed tryRefill to Container.setItem on count-grow transition
- ComputeAbsorbTest removed (computeAbsorb logic now in Container.canPlaceItem)
- 3-variant blockstate JSON replaces 9-variant feeds_left mapping"
```

---

### Task 4: HayFeederBlock.useItemOn → openMenu + cleanup BER (NeoForge)

**Files:**
- Modify: `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java`
- Delete: `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntityRenderer.java`
- Modify: `neoforge/src/main/java/com/hayfeeder/client/HayFeederClient.java`

- [ ] **Step 1: Update `HayFeederBlock.useItemOn` to open the menu**

Replace the existing `useItemOn` body (currently does refill via `be.tryRefill(stack)`) with:

```java
@Override
protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                      Player player, InteractionHand hand, BlockHitResult hit) {
    if (level.isClientSide()) return InteractionResult.SUCCESS;
    if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
        player.openMenu(be, pos);
    }
    return InteractionResult.SUCCESS_SERVER;
}
```

Also add a `useWithoutItem` override so empty-handed right-click also opens the menu:

```java
@Override
protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (level.isClientSide()) return InteractionResult.SUCCESS;
    if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
        player.openMenu(be, pos);
    }
    return InteractionResult.SUCCESS_SERVER;
}
```

The `playerWillDestroy` override (for hay_block + contents drop on break) stays as-is — it still uses `be.getContents()` which is preserved.

- [ ] **Step 2: Delete the BER file**

```bash
rm ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntityRenderer.java
```

- [ ] **Step 3: Simplify `HayFeederClient.java`**

The current file registers the BER on `EntityRenderersEvent.RegisterRenderers`. Since the BER is gone, simplify to:

```java
package com.hayfeeder.client;

import com.hayfeeder.HayFeeder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = HayFeeder.MOD_ID, dist = Dist.CLIENT)
public class HayFeederClient {
    public HayFeederClient(ModContainer container) {
        HayFeeder.LOGGER.info("Hay Feeder client setup");
    }
}
```

Task 5 will re-add a screen registration here.

- [ ] **Step 4: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java \
        neoforge/src/main/java/com/hayfeeder/client/HayFeederClient.java
git rm neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntityRenderer.java
git commit -m "refactor(neoforge): right-click opens HayFeederMenu, remove BER

useItemOn and useWithoutItem now both call player.openMenu on the BE
(MenuProvider). The BER (floating item indicator) is removed in favor
of the GUI as the canonical content view."
```

---

### Task 5: HayFeederScreen + GUI texture + screen registration + lang (NeoForge)

**Files:**
- Create: `neoforge/src/main/java/com/hayfeeder/client/HayFeederScreen.java`
- Create: `neoforge/src/main/resources/assets/hay_feeder/textures/gui/container/hay_feeder.png`
- Modify: `neoforge/src/main/java/com/hayfeeder/client/HayFeederClient.java` (add screen registration)
- Modify: `neoforge/src/main/resources/assets/hay_feeder/lang/en_us.json` (add GUI title)

- [ ] **Step 1: Create `HayFeederScreen.java`**

```java
package com.hayfeeder.client;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.feature.feeder.HayFeederMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class HayFeederScreen extends AbstractContainerScreen<HayFeederMenu> {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath(
            HayFeeder.MOD_ID, "textures/gui/container/hay_feeder.png");

    public HayFeederScreen(HayFeederMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderType::guiTextured, BG, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }
}
```

> The `graphics.blit(RenderType::guiTextured, ...)` signature is from MC 1.21.x. If MC 26.1 has changed the GuiGraphics API: check vanilla `BarrelScreen.java` for the canonical form.

- [ ] **Step 2: Generate placeholder GUI texture**

Save to `/tmp/gen_gui.py` and run:

```python
from PIL import Image, ImageDraw
from pathlib import Path

OUT = Path.home() / "Dev/mods/minecraft-revamp/hay_feeder/neoforge/src/main/resources/assets/hay_feeder/textures/gui/container"
OUT.mkdir(parents=True, exist_ok=True)

# Vanilla container background palette (taken from generic_54.png idiom)
BG_LIGHT = (198, 198, 198, 255)   # main panel
BG_MID   = (139, 139, 139, 255)   # darker accent
BG_DARK  = (85, 85, 85, 255)      # outer border
SLOT_BG  = (139, 139, 139, 255)
SLOT_HI  = (255, 255, 255, 255)
SLOT_LO  = (85, 85, 85, 255)
TRANSPARENT = (0, 0, 0, 0)

img = Image.new("RGBA", (256, 256), TRANSPARENT)
draw = ImageDraw.Draw(img)

# Container area: 176×166 in top-left
# Outer border 1px dark
draw.rectangle([0, 0, 175, 165], fill=BG_LIGHT, outline=BG_DARK)

# Title bar separator (subtle)
draw.line([(7, 17), (168, 17)], fill=BG_MID)

# Single food slot at center (slot center = (80, 35), so slot bg = 80-1, 35-1 to 80+17, 35+17 → 79,34 to 97,52)
# vanilla slot is 18×18 with 1px shadow
def draw_slot(x, y):
    # background dark
    draw.rectangle([x, y, x + 17, y + 17], fill=SLOT_LO)
    # interior light
    draw.rectangle([x + 1, y + 1, x + 16, y + 16], fill=SLOT_BG)
    # bottom-right highlight
    draw.line([(x + 17, y), (x + 17, y + 17)], fill=SLOT_HI)
    draw.line([(x, y + 17), (x + 17, y + 17)], fill=SLOT_HI)

draw_slot(79, 34)

# Player inventory: 3×9 main + 1×9 hotbar
# Main inventory at (8, 84) to (8 + 9*18 - 1, 84 + 3*18 - 1)
for row in range(3):
    for col in range(9):
        draw_slot(7 + col * 18, 83 + row * 18)
# Hotbar at (8, 142)
for col in range(9):
    draw_slot(7 + col * 18, 141)

img.save(OUT / "hay_feeder.png")
print(f"Wrote {OUT / 'hay_feeder.png'}")
```

Run:
```bash
python3 /tmp/gen_gui.py
```

Expected: `Wrote .../hay_feeder.png`. The file should be 256×256 RGBA.

- [ ] **Step 3: Register the screen in `HayFeederClient.java`**

Replace the simplified version from Task 4 with:

```java
package com.hayfeeder.client;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = HayFeeder.MOD_ID, dist = Dist.CLIENT)
public class HayFeederClient {
    public HayFeederClient(ModContainer container) {
        container.getEventBus().addListener(HayFeederClient::onRegisterMenuScreens);
        HayFeeder.LOGGER.info("Hay Feeder client setup");
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.HAY_FEEDER.get(), HayFeederScreen::new);
    }
}
```

> If `RegisterMenuScreensEvent` doesn't exist in MC 26.1 NeoForge, an alternative is to call `MenuScreens.register(MENU_TYPE, FACTORY)` directly from a `FMLClientSetupEvent`. Check NeoForge's RegisterMenuScreensEvent.

- [ ] **Step 4: Update lang file**

Replace `neoforge/src/main/resources/assets/hay_feeder/lang/en_us.json` with:

```json
{
  "itemGroup.hay_feeder.main": "Hay Feeder",
  "block.hay_feeder.hay_feeder": "Hay Feeder",
  "container.hay_feeder.hay_feeder": "Hay Feeder"
}
```

The `block.hay_feeder.hay_feeder.empty` key from the previous iteration is removed (no longer used — there's no tooltip and no BER).

- [ ] **Step 5: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/client/HayFeederScreen.java \
        neoforge/src/main/java/com/hayfeeder/client/HayFeederClient.java \
        neoforge/src/main/resources/assets/hay_feeder/textures/gui/container/hay_feeder.png \
        neoforge/src/main/resources/assets/hay_feeder/lang/en_us.json
git commit -m "feat(neoforge): HayFeederScreen + GUI texture + screen registration"
```

---

### Task 6: NeoForge manual integration test

Human-only. The user runs:

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
export JAVA_HOME=$HOME/.local/jdks/current PATH=$JAVA_HOME/bin:$PATH
./gradlew build
cp build/libs/hay_feeder-0.1.0.jar "/home/darthica/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Revamp NeoForge/minecraft/mods/"
```

Then launches MC from the "Revamp NeoForge" Prism instance and verifies:

- [ ] Right-click on a placed hay_feeder block opens a GUI with one slot + player inventory
- [ ] Drag-and-drop a stack of wheat into the slot — it absorbs up to 64
- [ ] Try to drop a non-whitelisted item (cookie, golden carrot, diamond) — slot rejects
- [ ] Try to drop a different food when slot has wheat — slot rejects
- [ ] Closing the GUI persists the contents
- [ ] Save & quit, reopen the world — GUI shows the same contents (BE persistence still works)
- [ ] feeds animals nearby with `/gamerule randomTickSpeed 500` + summoned cows
- [ ] Block visual: empty → wooden trough only, half → small bale, full → big bale (use `/setblock` or just drain via feeding to test transitions)
- [ ] Sneak-break drops 1 hay_block + remaining contents

If anything fails, report and the BER fix loop continues for the GUI.

---

### Task 7: Fabric blockstate refactor + BE Container conversion + Menu

Mirror Tasks 1+2+3 on Fabric. Same files, Fabric package paths (`com.hayfeeder.fabric.*` flat), Fabric registry idioms.

**Files (create):**
- `fabric/src/main/java/com/hayfeeder/fabric/FillStage.java`
- `fabric/src/main/java/com/hayfeeder/fabric/HayFeederMenu.java`
- `fabric/src/main/java/com/hayfeeder/fabric/ModMenuTypes.java`

**Files (modify):**
- `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlock.java`
- `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntity.java`
- `fabric/src/main/java/com/hayfeeder/fabric/HayFeederFabric.java`
- `fabric/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json`

**Files (delete):**
- `fabric/src/test/java/com/hayfeeder/fabric/ComputeAbsorbTest.java`

The Java content is identical to NeoForge except for package path and registry idiom for `ModMenuTypes`. Read the NeoForge files as the reference, port to Fabric.

### Fabric-specific differences

**`ModMenuTypes` (Fabric idiom):**

```java
package com.hayfeeder.fabric;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.flag.FeatureFlags;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;

public final class ModMenuTypes {
    public static final MenuType<HayFeederMenu> HAY_FEEDER = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(HayFeederFabric.MOD_ID, "hay_feeder"),
            new MenuType<>(
                    (containerId, inv) -> new HayFeederMenu(containerId, inv),
                    FeatureFlags.VANILLA_SET));

    private ModMenuTypes() {}

    public static void bootstrap() {}
}
```

> If `MenuType` constructor signature differs in MC 26.1 Fabric: check vanilla `MenuType.java`. The 2-arg `(MenuConstructor, FeatureFlagSet)` is canonical for MC 1.21+. If Fabric needs `ExtendedScreenHandlerType` for menu data, use that instead — but we have no extra data to pass.

**`HayFeederMenu` (Fabric):**

Same as NeoForge except:
- Package `com.hayfeeder.fabric`
- `super(ModMenuTypes.HAY_FEEDER, containerId)` (no `.get()` since Fabric uses static fields, not DeferredHolder)

**`HayFeederFabric.java` change:** Add `ModMenuTypes.bootstrap()` call alongside the other `bootstrap()` calls.

**`HayFeederBlockEntity` Fabric:** same content as NeoForge. Pay attention to `super(ModBlockEntities.HAY_FEEDER, pos, state)` (no `.get()`).

Tasks 1-3 from NeoForge are atomic — same here. One commit at the end.

- [ ] **Step 1-7: Mirror NeoForge Tasks 1-3 file-by-file**

(Read NeoForge files as canonical reference, write Fabric equivalents.)

- [ ] **Step 8: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
export JAVA_HOME=$HOME/.local/jdks/current25 PATH=$JAVA_HOME/bin:$PATH
./gradlew build
```

Expected: BUILD SUCCESSFUL. AcceptedFoodsTest still passes (9 tests).

- [ ] **Step 9: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add fabric/src/main/java/com/hayfeeder/fabric/FillStage.java \
        fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlock.java \
        fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntity.java \
        fabric/src/main/java/com/hayfeeder/fabric/HayFeederMenu.java \
        fabric/src/main/java/com/hayfeeder/fabric/ModMenuTypes.java \
        fabric/src/main/java/com/hayfeeder/fabric/HayFeederFabric.java \
        fabric/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json
git rm fabric/src/test/java/com/hayfeeder/fabric/ComputeAbsorbTest.java
git commit -m "refactor(fabric): convert hay_feeder to Container+Menu interaction model

Mirror of NeoForge refactor. Differences are limited to package path
and Fabric MenuType registration (Registry.register + FeatureFlags.VANILLA_SET
instead of NeoForge's IMenuTypeExtension.create)."
```

---

### Task 8: Fabric Block.useItemOn + Screen + texture mirror

Mirror NeoForge Tasks 4 + 5.

**Files:**
- Modify: `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlock.java` (useItemOn → openMenu, useWithoutItem mirror)
- Create: `fabric/src/main/java/com/hayfeeder/fabric/client/HayFeederScreen.java`
- Modify: `fabric/src/main/java/com/hayfeeder/fabric/client/HayFeederFabricClient.java`
- Create: `fabric/src/main/resources/assets/hay_feeder/textures/gui/container/hay_feeder.png` (copy from NeoForge)
- Modify: `fabric/src/main/resources/assets/hay_feeder/lang/en_us.json`

### Fabric-specific differences

**Screen registration in `HayFeederFabricClient`:**

```java
@Override
public void onInitializeClient() {
    MenuScreens.register(ModMenuTypes.HAY_FEEDER, HayFeederScreen::new);
    HayFeederFabric.LOGGER.info("Hay Feeder (Fabric) client setup");
}
```

(Fabric uses `MenuScreens.register` directly from vanilla, no event needed.)

- [ ] **Step 1-5: Mirror NeoForge Tasks 4 + 5 file-by-file**

For the GUI texture, simplest is `cp` from NeoForge:
```bash
cp ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge/src/main/resources/assets/hay_feeder/textures/gui/container/hay_feeder.png \
   ~/Dev/mods/minecraft-revamp/hay_feeder/fabric/src/main/resources/assets/hay_feeder/textures/gui/container/
```

(Make the directory first: `mkdir -p .../fabric/.../textures/gui/container`.)

- [ ] **Step 6: Build**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlock.java \
        fabric/src/main/java/com/hayfeeder/fabric/client/HayFeederScreen.java \
        fabric/src/main/java/com/hayfeeder/fabric/client/HayFeederFabricClient.java \
        fabric/src/main/resources/assets/hay_feeder/textures/gui/container/hay_feeder.png \
        fabric/src/main/resources/assets/hay_feeder/lang/en_us.json
git commit -m "feat(fabric): right-click opens HayFeederMenu, Screen + texture + lang"
```

---

### Task 9: Fabric manual integration test

Human-only. Same scenarios as Task 6, but on the "Revamp - Fabric" Prism instance.

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
export JAVA_HOME=$HOME/.local/jdks/current25 PATH=$JAVA_HOME/bin:$PATH
./gradlew build
cp build/libs/hay_feeder-fabric-0.1.0.jar "/home/darthica/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Revamp - Fabric/minecraft/mods/"
```

Verify all the same gameplay scenarios from Task 6.

---

## Done criteria

- [ ] Right-click on placed hay_feeder opens a GUI with 1 slot + player inventory on both loaders
- [ ] Drag-and-drop respects whitelist + same-type-at-a-time (cookie/golden carrot/diamond rejected, mixing wheat+carrot rejected)
- [ ] Capacity is 64 (a stack of 64 wheat goes fully into the empty slot in one drag)
- [ ] Visual stages reflect contents: empty count → empty model, 1-63 → half, 64 → full
- [ ] Refill feedback (composter sound + item particles) fires on each count↗ in the slot
- [ ] BER (floating item indicator) is gone — no related files remain
- [ ] Save/load round-trip preserves contents and the GUI shows them on reopen
- [ ] All `AcceptedFoodsTest` unit tests pass on both loaders (9 tests each)
- [ ] Both `./gradlew build` are green from clean cache
- [ ] User has manually verified Task 6 and Task 9 in-game on respective Prism instances

When all boxes check, the GUI pivot is complete.
