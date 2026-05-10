# Hay Feeder UX & game-feel polish — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the hay_feeder mod legible to players: distinct block model with 3 fill stages on a wooden trough, a `BlockEntityRenderer` showing stored contents floating above the bale with a count, and audible+visual feedback on refill. Plus removing the `[HF-DEBUG]` log statements added during runtime debugging.

**Architecture:** Three independent units. (1) Resource-pack changes: 3-stage block models referencing 4 placeholder textures, blockstate JSON remapping `feeds_left` 0-8 to {empty, half, full}, item model parented on the full block. (2) New client-side `BlockEntityRenderer` rendering the contents `ItemStack` with rotation + bobbing + numeric count. (3) Server-side particle+sound spawn in `HayFeederBlockEntity.tryRefill` on successful absorb. Implemented NeoForge first (where the runtime works and we have fast feedback), then mirrored to Fabric.

**Tech Stack:** Minecraft 26.1.2, NeoForge 26.1.2.41-beta, Fabric Loom 1.16.1, Python 3 + PIL/Pillow for placeholder texture generation.

**Spec:** [`../specs/2026-05-10-hay-feeder-ux-polish-design.md`](../specs/2026-05-10-hay-feeder-ux-polish-design.md)

---

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
- `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java` — remove `[HF-DEBUG]` lines, remove unused `HayFeeder` import
- `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java` — remove `[HF-DEBUG]` lines, add particle+sound spawn in `tryRefill`
- `neoforge/src/main/java/com/hayfeeder/client/HayFeederClient.java` — register the BER on `EntityRenderersEvent.RegisterRenderers`
- `neoforge/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json` — 9 variants → 3 model targets
- `neoforge/src/main/resources/assets/hay_feeder/models/item/hay_feeder.json` — parent points at `block/hay_feeder_full`

### Fabric — mirror set

Same set under `fabric/src/main/java/com/hayfeeder/fabric/...` (flat package). Resources under `fabric/src/main/resources/assets/hay_feeder/...`. BER registration uses Fabric API.

---

## Toolchain prelude

```bash
# NeoForge
export JAVA_HOME=$HOME/.local/jdks/current PATH=$JAVA_HOME/bin:$PATH

# Fabric
export JAVA_HOME=$HOME/.local/jdks/current25 PATH=$JAVA_HOME/bin:$PATH

# Texture generation
python3 -c "import PIL" 2>/dev/null || pip install --user Pillow
```

---

### Task 1: Remove `[HF-DEBUG]` log statements (NeoForge)

**Why first:** clean baseline. The debug logs were diagnostic-only and not part of the production mod. Subsequent tasks will modify these files; remove the noise first so diffs are clear.

**Files:**
- Modify: `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java`
- Modify: `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java`

- [ ] **Step 1: Restore `HayFeederBlock.java` to debug-free version**

Overwrite `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java` with:

```java
package com.hayfeeder.feature.feeder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class HayFeederBlock extends Block implements EntityBlock {
    public static final int MAX_FEEDS = 8;
    public static final IntegerProperty FEEDS_LEFT = IntegerProperty.create("feeds_left", 0, MAX_FEEDS);

    public HayFeederBlock(BlockBehaviour.Properties props) {
        super(props.randomTicks());
        registerDefaultState(stateDefinition.any().setValue(FEEDS_LEFT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FEEDS_LEFT);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HayFeederBlockEntity(pos, state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
            be.tickFeeding(level, pos, state);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof HayFeederBlockEntity be)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        int absorbed = be.tryRefill(stack);
        if (absorbed == 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!player.getAbilities().instabuild) {
            stack.shrink(absorbed);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.getAbilities().instabuild) {
            if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
                ItemStack remaining = be.getContents();
                if (!remaining.isEmpty()) {
                    Block.popResource(level, pos, remaining.copy());
                }
            }
            Block.popResource(level, pos, new ItemStack(Items.HAY_BLOCK));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
```

(The `import com.hayfeeder.HayFeeder` line at the top is gone; no `HayFeeder.LOGGER` references remain.)

- [ ] **Step 2: Restore `HayFeederBlockEntity.java` to debug-free version**

Overwrite `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java` with:

```java
package com.hayfeeder.feature.feeder;

import com.hayfeeder.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class HayFeederBlockEntity extends BlockEntity {
    public static final int CAPACITY = 8;
    public static final double FEED_RADIUS = 6.0;

    private ItemStack contents = ItemStack.EMPTY;

    public HayFeederBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAY_FEEDER.get(), pos, state);
    }

    public ItemStack getContents() {
        return contents;
    }

    public int tryRefill(ItemStack incoming) {
        Item containedType = contents.isEmpty() ? null : contents.getItem();
        int absorbed = computeAbsorb(containedType, contents.getCount(),
                incoming.getItem(), incoming.getCount());
        if (absorbed == 0) return 0;
        if (this.contents.isEmpty()) {
            this.contents = incoming.copyWithCount(absorbed);
        } else {
            this.contents.grow(absorbed);
        }
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.setBlock(getBlockPos(),
                    state.setValue(HayFeederBlock.FEEDS_LEFT, this.contents.getCount()),
                    Block.UPDATE_ALL);
        }
        return absorbed;
    }

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
        level.setBlock(pos, state.setValue(HayFeederBlock.FEEDS_LEFT, contents.getCount()),
                Block.UPDATE_ALL);
        setChanged();
    }

    static int computeAbsorb(Item containedType, int containedCount, Item incomingType, int incomingCount) {
        if (containedType == null || containedCount == 0) {
            return AcceptedFoods.isAccepted(incomingType)
                    ? Math.min(incomingCount, CAPACITY)
                    : 0;
        }
        if (containedType == incomingType) {
            return Math.min(incomingCount, CAPACITY - containedCount);
        }
        return 0;
    }

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
}
```

(Note: `import com.hayfeeder.HayFeeder` is gone; no `HayFeeder.LOGGER` references; no `[HF-DEBUG]` log lines anywhere.)

- [ ] **Step 3: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
export JAVA_HOME=$HOME/.local/jdks/current PATH=$JAVA_HOME/bin:$PATH
./gradlew build
```

Use `timeout: 600000` ms. Expected: BUILD SUCCESSFUL, all 17 tests still passing.

- [ ] **Step 4: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java \
        neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java
git commit -m "chore(neoforge): remove [HF-DEBUG] log statements after runtime verification"
```

---

### Task 2: Refill feedback (NeoForge)

**Files:**
- Modify: `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java`

Add particle + sound spawn at the end of the success path in `tryRefill`. No unit test (server-side particle/sound effects are impractical to test in JUnit; verified manually in-game).

- [ ] **Step 1: Add particle + sound spawn**

Modify `HayFeederBlockEntity.tryRefill`. Currently the method ends with `return absorbed;` after blockstate sync. Insert the feedback block **before** the final return.

The full updated method body:

```java
    public int tryRefill(ItemStack incoming) {
        Item containedType = contents.isEmpty() ? null : contents.getItem();
        int absorbed = computeAbsorb(containedType, contents.getCount(),
                incoming.getItem(), incoming.getCount());
        if (absorbed == 0) return 0;
        if (this.contents.isEmpty()) {
            this.contents = incoming.copyWithCount(absorbed);
        } else {
            this.contents.grow(absorbed);
        }
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = getBlockPos();
            BlockState state = getBlockState();
            level.setBlock(pos,
                    state.setValue(HayFeederBlock.FEEDS_LEFT, this.contents.getCount()),
                    Block.UPDATE_ALL);
            serverLevel.sendParticles(
                    new net.minecraft.core.particles.ItemParticleOption(
                            net.minecraft.core.particles.ParticleTypes.ITEM, incoming),
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    6, 0.2, 0.05, 0.2, 0.05);
            serverLevel.playSound(null, pos, SoundEvents.COMPOSTER_FILL_SUCCESS,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return absorbed;
    }
```

Add the import `import net.minecraft.server.level.ServerLevel;` (it's already imported in the file). Add `import net.minecraft.core.particles.ItemParticleOption;` if not already present (the inline qualified name above avoids the import for the patch but a clean import is preferred).

Cleaner version with imports added at top of file:

```java
import net.minecraft.core.particles.ItemParticleOption;
```

And the method body uses:

```java
    public int tryRefill(ItemStack incoming) {
        Item containedType = contents.isEmpty() ? null : contents.getItem();
        int absorbed = computeAbsorb(containedType, contents.getCount(),
                incoming.getItem(), incoming.getCount());
        if (absorbed == 0) return 0;
        if (this.contents.isEmpty()) {
            this.contents = incoming.copyWithCount(absorbed);
        } else {
            this.contents.grow(absorbed);
        }
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = getBlockPos();
            level.setBlock(pos,
                    getBlockState().setValue(HayFeederBlock.FEEDS_LEFT, this.contents.getCount()),
                    Block.UPDATE_ALL);
            serverLevel.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, incoming),
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    6, 0.2, 0.05, 0.2, 0.05);
            serverLevel.playSound(null, pos, SoundEvents.COMPOSTER_FILL_SUCCESS,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return absorbed;
    }
```

> **Note:** the previous version's `if (level != null)` was loose typing. The new version narrows to `ServerLevel` since particle and sound APIs require server-side. The blockstate sync now lives inside the same branch (it doesn't matter on client; the BE's state mutation triggers a vanilla update tick).

- [ ] **Step 2: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build
```

Use `timeout: 600000` ms. Expected: BUILD SUCCESSFUL.

If `SoundEvents.COMPOSTER_FILL_SUCCESS` does not exist in MC 26.1: check the actual constant name — possibilities include `COMPOSTER_FILL`, `COMPOSTER_FILL_SUCCESSFUL`. Adapt and report.

If `ItemParticleOption(ParticleType, ItemStack)` constructor doesn't exist: check the class — newer versions may use `new ItemParticleOption(ParticleTypes.ITEM, stack)` or expect a `Holder<Item>` instead.

- [ ] **Step 3: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java
git commit -m "feat(neoforge): refill feedback — composter fill_success sound + item-typed particles"
```

---

### Task 3: Generate placeholder textures (NeoForge)

**Files (create):**
- `neoforge/src/main/resources/assets/hay_feeder/textures/block/hay_top.png`
- `neoforge/src/main/resources/assets/hay_feeder/textures/block/hay_side.png`
- `neoforge/src/main/resources/assets/hay_feeder/textures/block/trough_top.png`
- `neoforge/src/main/resources/assets/hay_feeder/textures/block/trough_side.png`

Generated programmatically via Pillow. The author will replace these with real artwork later.

- [ ] **Step 1: Verify Pillow is available**

```bash
python3 -c "from PIL import Image, ImageDraw; print('OK')"
```

If it errors: `pip install --user Pillow`.

- [ ] **Step 2: Generate the 4 placeholder textures**

Save the following script as `/tmp/gen_textures.py` and run it:

```python
from PIL import Image, ImageDraw
from pathlib import Path

OUT = Path.home() / "Dev/mods/minecraft-revamp/hay_feeder/neoforge/src/main/resources/assets/hay_feeder/textures/block"
OUT.mkdir(parents=True, exist_ok=True)

HAY_BASE = (229, 194, 74)
HAY_DARK = (180, 140, 50)
WOOD_BASE = (153, 107, 59)
WOOD_DARK = (100, 70, 40)

def make(filename, base_color, dark_color, mode):
    img = Image.new("RGBA", (16, 16), base_color + (255,))
    draw = ImageDraw.Draw(img)
    if mode == "hay_top":
        for x in range(0, 16, 4):
            for y in range(16):
                draw.point((x, y), fill=dark_color + (255,))
        for y in range(0, 16, 4):
            for x in range(16):
                draw.point((x, y), fill=dark_color + (255,))
    elif mode == "hay_side":
        for y in (3, 7, 11):
            for x in range(16):
                draw.point((x, y), fill=dark_color + (255,))
    elif mode == "trough_top":
        for x in (0, 4, 8, 12, 15):
            for y in range(16):
                draw.point((x, y), fill=dark_color + (255,))
    elif mode == "trough_side":
        for y in (0, 4, 8, 12, 15):
            for x in range(16):
                draw.point((x, y), fill=dark_color + (255,))
    img.save(OUT / filename)
    print(f"Wrote {filename}")

make("hay_top.png",     HAY_BASE,  HAY_DARK,  "hay_top")
make("hay_side.png",    HAY_BASE,  HAY_DARK,  "hay_side")
make("trough_top.png",  WOOD_BASE, WOOD_DARK, "trough_top")
make("trough_side.png", WOOD_BASE, WOOD_DARK, "trough_side")
```

Run:
```bash
python3 /tmp/gen_textures.py
```

Expected output:
```
Wrote hay_top.png
Wrote hay_side.png
Wrote trough_top.png
Wrote trough_side.png
```

- [ ] **Step 3: Verify files**

```bash
ls -la ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge/src/main/resources/assets/hay_feeder/textures/block/
file ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge/src/main/resources/assets/hay_feeder/textures/block/*.png
```

Expected: 4 PNG files, each "PNG image data, 16 x 16, 8-bit/color RGBA, non-interlaced".

- [ ] **Step 4: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/resources/assets/hay_feeder/textures/block/
git commit -m "feat(neoforge): add placeholder block textures (hay + trough, 16x16)"
```

---

### Task 4: Block models — 3 stages (NeoForge)

**Files (create):**
- `neoforge/src/main/resources/assets/hay_feeder/models/block/hay_feeder_full.json`
- `neoforge/src/main/resources/assets/hay_feeder/models/block/hay_feeder_half.json`
- `neoforge/src/main/resources/assets/hay_feeder/models/block/hay_feeder_empty.json`

Each model has two `elements`: a flat trough (0,0,0 → 16,2,16) at the bottom, and a hay cube above it. The hay cube shrinks across stages.

- [ ] **Step 1: Create `hay_feeder_full.json`**

```json
{
  "parent": "minecraft:block/block",
  "textures": {
    "hay_top": "hay_feeder:block/hay_top",
    "hay_side": "hay_feeder:block/hay_side",
    "trough_top": "hay_feeder:block/trough_top",
    "trough_side": "hay_feeder:block/trough_side",
    "particle": "hay_feeder:block/hay_side"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 2, 16],
      "faces": {
        "down":  { "texture": "#trough_top" },
        "up":    { "texture": "#trough_top" },
        "north": { "texture": "#trough_side" },
        "south": { "texture": "#trough_side" },
        "east":  { "texture": "#trough_side" },
        "west":  { "texture": "#trough_side" }
      }
    },
    {
      "from": [2, 2, 2],
      "to": [14, 16, 14],
      "faces": {
        "down":  { "texture": "#hay_top" },
        "up":    { "texture": "#hay_top" },
        "north": { "texture": "#hay_side" },
        "south": { "texture": "#hay_side" },
        "east":  { "texture": "#hay_side" },
        "west":  { "texture": "#hay_side" }
      }
    }
  ]
}
```

- [ ] **Step 2: Create `hay_feeder_half.json`**

Same structure as full, but the hay element is smaller:

```json
{
  "parent": "minecraft:block/block",
  "textures": {
    "hay_top": "hay_feeder:block/hay_top",
    "hay_side": "hay_feeder:block/hay_side",
    "trough_top": "hay_feeder:block/trough_top",
    "trough_side": "hay_feeder:block/trough_side",
    "particle": "hay_feeder:block/hay_side"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 2, 16],
      "faces": {
        "down":  { "texture": "#trough_top" },
        "up":    { "texture": "#trough_top" },
        "north": { "texture": "#trough_side" },
        "south": { "texture": "#trough_side" },
        "east":  { "texture": "#trough_side" },
        "west":  { "texture": "#trough_side" }
      }
    },
    {
      "from": [3, 2, 3],
      "to": [13, 11, 13],
      "faces": {
        "down":  { "texture": "#hay_top" },
        "up":    { "texture": "#hay_top" },
        "north": { "texture": "#hay_side" },
        "south": { "texture": "#hay_side" },
        "east":  { "texture": "#hay_side" },
        "west":  { "texture": "#hay_side" }
      }
    }
  ]
}
```

- [ ] **Step 3: Create `hay_feeder_empty.json`**

Just a thin pile of hay on the trough:

```json
{
  "parent": "minecraft:block/block",
  "textures": {
    "hay_top": "hay_feeder:block/hay_top",
    "hay_side": "hay_feeder:block/hay_side",
    "trough_top": "hay_feeder:block/trough_top",
    "trough_side": "hay_feeder:block/trough_side",
    "particle": "hay_feeder:block/trough_side"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 2, 16],
      "faces": {
        "down":  { "texture": "#trough_top" },
        "up":    { "texture": "#trough_top" },
        "north": { "texture": "#trough_side" },
        "south": { "texture": "#trough_side" },
        "east":  { "texture": "#trough_side" },
        "west":  { "texture": "#trough_side" }
      }
    },
    {
      "from": [4, 2, 4],
      "to": [12, 3, 12],
      "faces": {
        "down":  { "texture": "#hay_top" },
        "up":    { "texture": "#hay_top" },
        "north": { "texture": "#hay_side" },
        "south": { "texture": "#hay_side" },
        "east":  { "texture": "#hay_side" },
        "west":  { "texture": "#hay_side" }
      }
    }
  ]
}
```

- [ ] **Step 4: Build to verify JSONs are valid**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build
```

Use `timeout: 600000` ms. Expected: BUILD SUCCESSFUL. JSON parse errors would surface here.

- [ ] **Step 5: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/resources/assets/hay_feeder/models/block/
git commit -m "feat(neoforge): add 3-stage block models (full/half/empty) with trough geometry"
```

---

### Task 5: Update blockstate variants + item model (NeoForge)

**Files:**
- Modify: `neoforge/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json`
- Modify: `neoforge/src/main/resources/assets/hay_feeder/models/item/hay_feeder.json`

- [ ] **Step 1: Replace blockstate JSON**

Overwrite `neoforge/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json` with:

```json
{
  "variants": {
    "feeds_left=0": { "model": "hay_feeder:block/hay_feeder_empty" },
    "feeds_left=1": { "model": "hay_feeder:block/hay_feeder_half" },
    "feeds_left=2": { "model": "hay_feeder:block/hay_feeder_half" },
    "feeds_left=3": { "model": "hay_feeder:block/hay_feeder_half" },
    "feeds_left=4": { "model": "hay_feeder:block/hay_feeder_half" },
    "feeds_left=5": { "model": "hay_feeder:block/hay_feeder_half" },
    "feeds_left=6": { "model": "hay_feeder:block/hay_feeder_half" },
    "feeds_left=7": { "model": "hay_feeder:block/hay_feeder_half" },
    "feeds_left=8": { "model": "hay_feeder:block/hay_feeder_full" }
  }
}
```

- [ ] **Step 2: Replace item model**

Overwrite `neoforge/src/main/resources/assets/hay_feeder/models/item/hay_feeder.json` with:

```json
{
  "parent": "hay_feeder:block/hay_feeder_full"
}
```

The item model previously pointed at `hay_feeder:block/hay_feeder` which no longer exists.

- [ ] **Step 3: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json \
        neoforge/src/main/resources/assets/hay_feeder/models/item/hay_feeder.json
git commit -m "feat(neoforge): map feeds_left blockstate variants to 3 stage models"
```

---

### Task 6: BlockEntityRenderer (NeoForge)

**Files (create):**
- `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntityRenderer.java`

**Files (modify):**
- `neoforge/src/main/java/com/hayfeeder/client/HayFeederClient.java`

- [ ] **Step 1: Create `HayFeederBlockEntityRenderer.java`**

Create `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntityRenderer.java`:

```java
package com.hayfeeder.feature.feeder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class HayFeederBlockEntityRenderer implements BlockEntityRenderer<HayFeederBlockEntity> {

    private final ItemRenderer itemRenderer;
    private final Font font;

    public HayFeederBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
        this.font = ctx.getFont();
    }

    @Override
    public void render(HayFeederBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ItemStack contents = be.getContents();
        if (contents.isEmpty()) return;

        long gameTime = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        float yawDeg = ((gameTime % 360L) + partialTick) * 2.0f;
        float bob = Mth.sin((gameTime + partialTick) * 0.05f) * 0.05f;

        // floating item
        poseStack.pushPose();
        poseStack.translate(0.5, 1.25 + bob, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
        poseStack.scale(0.6f, 0.6f, 0.6f);
        itemRenderer.renderStatic(contents, ItemDisplayContext.GROUND,
                packedLight, packedOverlay, poseStack, buffers, be.getLevel(), 0);
        poseStack.popPose();

        // numeric count (only if > 1)
        int count = contents.getCount();
        if (count > 1) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            String text = String.valueOf(count);
            poseStack.pushPose();
            poseStack.translate(0.5, 1.05 + bob, 0.5);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-0.025f, -0.025f, 0.025f);
            float w = -font.width(text) / 2.0f;
            font.drawInBatch(text, w, 0, 0xFFFFFF, false,
                    poseStack.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, packedLight);
            poseStack.popPose();
        }
    }
}
```

> If MC 26.1 has different signatures (e.g. `BlockEntityRenderer.render` adds a `Vec3 cameraPos` parameter, or `Font.drawInBatch` is renamed), check vanilla `EnchantTableRenderer` or `BeaconRenderer` decompiled sources for the correct signature, adapt, and note in the report.

- [ ] **Step 2: Register the BER in `HayFeederClient.java`**

Read the current state of `neoforge/src/main/java/com/hayfeeder/client/HayFeederClient.java`. It should be:

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

Replace with:

```java
package com.hayfeeder.client;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.feature.feeder.HayFeederBlockEntityRenderer;
import com.hayfeeder.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = HayFeeder.MOD_ID, dist = Dist.CLIENT)
public class HayFeederClient {
    public HayFeederClient(ModContainer container) {
        container.getEventBus().addListener(HayFeederClient::onRegisterRenderers);
        HayFeeder.LOGGER.info("Hay Feeder client setup");
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.HAY_FEEDER.get(),
                HayFeederBlockEntityRenderer::new);
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build
```

Expected: BUILD SUCCESSFUL. If `EntityRenderersEvent.RegisterRenderers` differs in MC 26.1: adapt the import and method signature.

- [ ] **Step 4: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntityRenderer.java \
        neoforge/src/main/java/com/hayfeeder/client/HayFeederClient.java
git commit -m "feat(neoforge): BlockEntityRenderer with floating item, rotation, bobbing, numeric count"
```

---

### Task 7: NeoForge manual test

This task is **human-only**. The agent stops here for the NeoForge side and waits for the user to verify in-game.

The user runs:
```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
export JAVA_HOME=$HOME/.local/jdks/current PATH=$JAVA_HOME/bin:$PATH
./gradlew build
cp build/libs/hay_feeder-0.1.0.jar "/home/darthica/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Minecraft 2/minecraft/mods/"
# Launch MC from Prism, try the mod
```

What to verify:
- [ ] Block looks distinct from vanilla `hay_block` (trough visible at base)
- [ ] When `feeds_left=8`, full hay is visible
- [ ] When you've used 1-7 charges, the bale is visibly smaller
- [ ] When `feeds_left=0`, just a thin hay layer is visible on the trough
- [ ] Right-click with wheat plays the composter fill sound + wheat-particle burst
- [ ] After right-click, a wheat item floats above the bale, rotating slowly with subtle bobbing
- [ ] If count > 1, a small white number appears below/near the floating item
- [ ] Refilling with carrots while feeder has wheat → no absorption (correct, behavior unchanged)

Report any visual or behavioral issues; subagents resume with Fabric port (Tasks 8–11) once NeoForge is confirmed working.

---

### Task 8: Refill feedback (Fabric)

**Files:**
- Modify: `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntity.java`

Mirror Task 2 for Fabric. The Java content is identical except for package paths.

- [ ] **Step 1: Update `tryRefill` in Fabric BE**

Modify `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntity.java`. Add the import:

```java
import net.minecraft.core.particles.ItemParticleOption;
```

And replace the `tryRefill` method body to match the NeoForge version (same logic, narrowed to `ServerLevel`):

```java
    public int tryRefill(ItemStack incoming) {
        Item containedType = contents.isEmpty() ? null : contents.getItem();
        int absorbed = computeAbsorb(containedType, contents.getCount(),
                incoming.getItem(), incoming.getCount());
        if (absorbed == 0) return 0;
        if (this.contents.isEmpty()) {
            this.contents = incoming.copyWithCount(absorbed);
        } else {
            this.contents.grow(absorbed);
        }
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = getBlockPos();
            level.setBlock(pos,
                    getBlockState().setValue(HayFeederBlock.FEEDS_LEFT, this.contents.getCount()),
                    Block.UPDATE_ALL);
            serverLevel.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, incoming),
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    6, 0.2, 0.05, 0.2, 0.05);
            serverLevel.playSound(null, pos, SoundEvents.COMPOSTER_FILL_SUCCESS,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return absorbed;
    }
```

- [ ] **Step 2: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
export JAVA_HOME=$HOME/.local/jdks/current25 PATH=$JAVA_HOME/bin:$PATH
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntity.java
git commit -m "feat(fabric): refill feedback — composter fill_success sound + item-typed particles"
```

---

### Task 9: Asset pack mirror (Fabric)

**Files (create):**
- `fabric/src/main/resources/assets/hay_feeder/textures/block/hay_top.png`
- `fabric/src/main/resources/assets/hay_feeder/textures/block/hay_side.png`
- `fabric/src/main/resources/assets/hay_feeder/textures/block/trough_top.png`
- `fabric/src/main/resources/assets/hay_feeder/textures/block/trough_side.png`
- `fabric/src/main/resources/assets/hay_feeder/models/block/hay_feeder_full.json`
- `fabric/src/main/resources/assets/hay_feeder/models/block/hay_feeder_half.json`
- `fabric/src/main/resources/assets/hay_feeder/models/block/hay_feeder_empty.json`

**Files (modify):**
- `fabric/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json`
- `fabric/src/main/resources/assets/hay_feeder/models/item/hay_feeder.json`

All identical to NeoForge. The simplest implementation is to copy from `neoforge/.../assets/hay_feeder/...` to `fabric/.../assets/hay_feeder/...`.

- [ ] **Step 1: Copy textures and models**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
mkdir -p fabric/src/main/resources/assets/hay_feeder/textures/block
cp neoforge/src/main/resources/assets/hay_feeder/textures/block/*.png \
   fabric/src/main/resources/assets/hay_feeder/textures/block/
cp neoforge/src/main/resources/assets/hay_feeder/models/block/hay_feeder_full.json \
   fabric/src/main/resources/assets/hay_feeder/models/block/
cp neoforge/src/main/resources/assets/hay_feeder/models/block/hay_feeder_half.json \
   fabric/src/main/resources/assets/hay_feeder/models/block/
cp neoforge/src/main/resources/assets/hay_feeder/models/block/hay_feeder_empty.json \
   fabric/src/main/resources/assets/hay_feeder/models/block/
cp neoforge/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json \
   fabric/src/main/resources/assets/hay_feeder/blockstates/hay_feeder.json
cp neoforge/src/main/resources/assets/hay_feeder/models/item/hay_feeder.json \
   fabric/src/main/resources/assets/hay_feeder/models/item/hay_feeder.json
```

- [ ] **Step 2: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add fabric/src/main/resources/assets/hay_feeder/
git commit -m "feat(fabric): mirror 3-stage models, textures, blockstate, item model"
```

---

### Task 10: BlockEntityRenderer (Fabric)

**Files (create):**
- `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntityRenderer.java`

**Files (modify):**
- `fabric/src/main/java/com/hayfeeder/fabric/client/HayFeederFabricClient.java`

- [ ] **Step 1: Create `HayFeederBlockEntityRenderer.java`**

Identical to NeoForge except for package. Create `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntityRenderer.java`:

```java
package com.hayfeeder.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class HayFeederBlockEntityRenderer implements BlockEntityRenderer<HayFeederBlockEntity> {

    private final ItemRenderer itemRenderer;
    private final Font font;

    public HayFeederBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
        this.font = ctx.getFont();
    }

    @Override
    public void render(HayFeederBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ItemStack contents = be.getContents();
        if (contents.isEmpty()) return;

        long gameTime = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        float yawDeg = ((gameTime % 360L) + partialTick) * 2.0f;
        float bob = Mth.sin((gameTime + partialTick) * 0.05f) * 0.05f;

        poseStack.pushPose();
        poseStack.translate(0.5, 1.25 + bob, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
        poseStack.scale(0.6f, 0.6f, 0.6f);
        itemRenderer.renderStatic(contents, ItemDisplayContext.GROUND,
                packedLight, packedOverlay, poseStack, buffers, be.getLevel(), 0);
        poseStack.popPose();

        int count = contents.getCount();
        if (count > 1) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            String text = String.valueOf(count);
            poseStack.pushPose();
            poseStack.translate(0.5, 1.05 + bob, 0.5);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-0.025f, -0.025f, 0.025f);
            float w = -font.width(text) / 2.0f;
            font.drawInBatch(text, w, 0, 0xFFFFFF, false,
                    poseStack.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, packedLight);
            poseStack.popPose();
        }
    }
}
```

- [ ] **Step 2: Register BER in `HayFeederFabricClient.java`**

Read the current state of `fabric/src/main/java/com/hayfeeder/fabric/client/HayFeederFabricClient.java`. It should be:

```java
package com.hayfeeder.fabric.client;

import com.hayfeeder.fabric.HayFeederFabric;
import net.fabricmc.api.ClientModInitializer;

public class HayFeederFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HayFeederFabric.LOGGER.info("Hay Feeder (Fabric) client setup");
    }
}
```

Replace with:

```java
package com.hayfeeder.fabric.client;

import com.hayfeeder.fabric.HayFeederBlockEntityRenderer;
import com.hayfeeder.fabric.HayFeederFabric;
import com.hayfeeder.fabric.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

public class HayFeederFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererRegistry.register(
                ModBlockEntities.HAY_FEEDER,
                HayFeederBlockEntityRenderer::new);
        HayFeederFabric.LOGGER.info("Hay Feeder (Fabric) client setup");
    }
}
```

> If `BlockEntityRendererRegistry` is not the right Fabric helper for MC 26.1: check `BlockEntityRenderers` (vanilla) or `BlockEntityRendererFactories` (older Fabric API). Adapt the import and call. The registration semantics are identical: BE type + factory.

- [ ] **Step 3: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntityRenderer.java \
        fabric/src/main/java/com/hayfeeder/fabric/client/HayFeederFabricClient.java
git commit -m "feat(fabric): BlockEntityRenderer with floating item, rotation, bobbing, numeric count"
```

---

### Task 11: Fabric manual test

Human-only. Mirror of Task 7 with Fabric instance.

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
export JAVA_HOME=$HOME/.local/jdks/current25 PATH=$JAVA_HOME/bin:$PATH
./gradlew build
# copy to Fabric Prism instance, launch, verify same scenarios as Task 7
```

Same checklist as Task 7. If the BER doesn't render or registration crashes, the most likely cause is the Fabric API helper class differing in MC 26.1 — adjust `BlockEntityRendererRegistry.register(...)` to whatever the version exposes.

---

## Done criteria

- [ ] All `[HF-DEBUG]` log statements removed from NeoForge sources
- [ ] Refill on either loader plays composter fill_success + item-typed particles
- [ ] Block has 3 visually distinct stages on either loader (full / half / empty)
- [ ] Floating item renders above the bale (with rotation + bobbing + count when > 1) on either loader
- [ ] All 17 unit tests still pass on each loader
- [ ] Both `./gradlew build` are green from clean cache
- [ ] User has manually verified Tasks 7 and 11 in-game

When all boxes check, the UX-polish iteration is complete. Future iterations may refine the form factor, add idle-activity feedback, or add tooltips — those are out of scope for this plan.
