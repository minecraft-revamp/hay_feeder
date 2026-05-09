# Hay Feeder feeding logic — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the placeholder `hay_feeder` block actually feed nearby vanilla animals when right-clicked with food, by storing the food in a `BlockEntity` and consuming charges via `randomTick`.

**Architecture:** Custom `Block` + `BlockEntity` pair. The block holds no mutable state; `BlockEntity` stores an `ItemStack` of capacity 8. Right-click absorbs food into the BE; `randomTick` reads the BE, scans for compatible animals in a 6-block radius, feeds them all (vanilla `setInLove` / `ageUp`), decrements the stored count, syncs the `feeds_left` blockstate. Pure logic units (`AcceptedFoods` whitelist, `computeAbsorb` refill math) are unit-tested with JUnit. Block-level behaviour is verified manually in `runClient`. Implemented on NeoForge first, then mirrored to Fabric with idiomatic registry adjustments.

**Tech Stack:** Minecraft 26.1.2 (post-deobfuscation), NeoForge 26.1.2.41-beta (NeoGradle 7, Java 21 driver auto-fetching JDK 25), Fabric Loom 1.16.1 (Java 25 driver), JUnit 5.

**Spec:** [`../specs/2026-05-09-hay-feeder-feeding-design.md`](../specs/2026-05-09-hay-feeder-feeding-design.md)

---

## File map

### NeoForge — files to create

```
neoforge/src/main/java/com/hayfeeder/feature/feeder/
├── HayFeederBlockEntity.java        ← persistent ItemStack, tickFeeding, tryRefill
├── FeedingMechanic.java             ← static feedAnimal helper
└── AcceptedFoods.java               ← static whitelist

neoforge/src/main/java/com/hayfeeder/registry/
└── ModBlockEntities.java            ← BlockEntityType registration

neoforge/src/main/resources/data/hay_feeder/recipe/
└── hay_feeder.json                  ← shapeless 1×1 hay_block → hay_feeder

neoforge/src/test/java/com/hayfeeder/feature/feeder/
├── AcceptedFoodsTest.java           ← whitelist assertions
└── ComputeAbsorbTest.java           ← refill math assertions
```

### NeoForge — files to modify

- `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java` — add `EntityBlock`, `newBlockEntity`, `randomTick`, `useItemOn`, `onRemove`
- `neoforge/src/main/java/com/hayfeeder/HayFeeder.java` — register `ModBlockEntities`
- `neoforge/src/main/resources/assets/hay_feeder/lang/en_us.json` — add `block.hay_feeder.hay_feeder.empty`

### Fabric — mirrored

Same set of files under `fabric/src/main/java/com/hayfeeder/fabric/...` (flat package, no `feature/feeder` / `registry` subpackages, matching the existing Fabric scaffold convention). Resource paths identical to NeoForge.

---

## Toolchain prelude

Each NeoForge command in this plan assumes:
```bash
export JAVA_HOME=$HOME/.local/jdks/current PATH=$JAVA_HOME/bin:$PATH
```

Each Fabric command assumes:
```bash
export JAVA_HOME=$HOME/.local/jdks/current25 PATH=$JAVA_HOME/bin:$PATH
```

---

### Task 1: Initialise git and verify the scaffold builds

**Why first:** Subsequent tasks rely on `git commit` per task. The scaffold has never been built; we need a green baseline before adding logic.

**Files:**
- Run: `git init` in `hay_feeder/`
- Run: `./gradlew build` in `hay_feeder/neoforge/`

- [ ] **Step 1: Initialise git in `hay_feeder/`**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git init
git add .
git commit -m "chore: initial scaffold for hay_feeder mod"
```

Expected: `Initial commit` created. `git status` reports clean.

- [ ] **Step 2: Build NeoForge scaffold**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
export JAVA_HOME=$HOME/.local/jdks/current PATH=$JAVA_HOME/bin:$PATH
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. JAR present at `build/libs/hay_feeder-0.1.0.jar`.

If compilation fails: scaffold has bugs; fix before proceeding. The most likely culprits are MC 26.1 API drift in the placeholder Block class or `setId` signature in Fabric registries (Task 12+).

- [ ] **Step 3: Build Fabric scaffold**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
export JAVA_HOME=$HOME/.local/jdks/current25 PATH=$JAVA_HOME/bin:$PATH
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. JAR present at `build/libs/hay_feeder-fabric-0.1.0.jar`.

- [ ] **Step 4: Commit baseline-green state**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
# .gradle/, build/, run/ already in .gitignore; nothing new to track
# This step is a no-op commit if the build produced no checked-in changes.
```

Expected: `git status` clean. No commit needed if no tracked-file changes.

---

### Task 2: AcceptedFoods whitelist (NeoForge) — TDD

**Files:**
- Create: `neoforge/src/test/java/com/hayfeeder/feature/feeder/AcceptedFoodsTest.java`
- Create: `neoforge/src/main/java/com/hayfeeder/feature/feeder/AcceptedFoods.java`

- [ ] **Step 1: Write the failing test**

Create `neoforge/src/test/java/com/hayfeeder/feature/feeder/AcceptedFoodsTest.java`:

```java
package com.hayfeeder.feature.feeder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptedFoodsTest {

    @Test
    void wheat_is_accepted() {
        assertTrue(AcceptedFoods.isAccepted(new ItemStack(Items.WHEAT)));
    }

    @Test
    void carrot_is_accepted() {
        assertTrue(AcceptedFoods.isAccepted(new ItemStack(Items.CARROT)));
    }

    @Test
    void salmon_is_accepted() {
        assertTrue(AcceptedFoods.isAccepted(new ItemStack(Items.SALMON)));
    }

    @Test
    void glow_berries_are_accepted() {
        assertTrue(AcceptedFoods.isAccepted(new ItemStack(Items.GLOW_BERRIES)));
    }

    @Test
    void cookie_is_rejected_to_protect_parrots() {
        assertFalse(AcceptedFoods.isAccepted(new ItemStack(Items.COOKIE)));
    }

    @Test
    void golden_carrot_is_rejected_to_avoid_trivialising_horse_breeding() {
        assertFalse(AcceptedFoods.isAccepted(new ItemStack(Items.GOLDEN_CARROT)));
    }

    @Test
    void enchanted_golden_apple_is_rejected() {
        assertFalse(AcceptedFoods.isAccepted(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
    }

    @Test
    void diamond_is_rejected() {
        assertFalse(AcceptedFoods.isAccepted(new ItemStack(Items.DIAMOND)));
    }

    @Test
    void empty_stack_is_rejected() {
        assertFalse(AcceptedFoods.isAccepted(ItemStack.EMPTY));
    }
}
```

- [ ] **Step 2: Run test, expect compile failure (`AcceptedFoods` does not exist)**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew test --tests AcceptedFoodsTest
```

Expected: `BUILD FAILED` with `error: cannot find symbol` for `AcceptedFoods`.

- [ ] **Step 3: Implement `AcceptedFoods`**

Create `neoforge/src/main/java/com/hayfeeder/feature/feeder/AcceptedFoods.java`:

```java
package com.hayfeeder.feature.feeder;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

public final class AcceptedFoods {
    private static final Set<Item> ACCEPTED = Set.of(
            Items.WHEAT, Items.CARROT, Items.BEETROOT,
            Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS,
            Items.COD, Items.SALMON, Items.TROPICAL_FISH,
            Items.SWEET_BERRIES, Items.GLOW_BERRIES,
            Items.APPLE
    );

    private AcceptedFoods() {}

    public static boolean isAccepted(ItemStack stack) {
        return !stack.isEmpty() && ACCEPTED.contains(stack.getItem());
    }
}
```

- [ ] **Step 4: Run test, expect pass**

```bash
./gradlew test --tests AcceptedFoodsTest
```

Expected: `BUILD SUCCESSFUL`, all 9 tests pass.

- [ ] **Step 5: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/feature/feeder/AcceptedFoods.java \
        neoforge/src/test/java/com/hayfeeder/feature/feeder/AcceptedFoodsTest.java
git commit -m "feat(neoforge): add AcceptedFoods whitelist for hay_feeder"
```

---

### Task 3: ComputeAbsorb refill logic (NeoForge) — TDD

**Why a separate static helper:** keeps the refill math pure and unit-testable. The instance method on `HayFeederBlockEntity` will call it.

**Files:**
- Create: `neoforge/src/test/java/com/hayfeeder/feature/feeder/ComputeAbsorbTest.java`
- The implementation will land inside `HayFeederBlockEntity` in Task 4 — but Task 3 introduces the static helper as a free function for now to keep the test isolated, and Task 4 moves it.

To avoid the move overhead, **Task 3 puts `computeAbsorb` directly into a new `HayFeederBlockEntity` skeleton** (without the BE machinery yet). Task 4 fleshes the rest out.

**Files (revised):**
- Create: `neoforge/src/test/java/com/hayfeeder/feature/feeder/ComputeAbsorbTest.java`
- Create: `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java` (skeleton with just `computeAbsorb` static + `CAPACITY` constant)

- [ ] **Step 1: Write the failing test**

Create `neoforge/src/test/java/com/hayfeeder/feature/feeder/ComputeAbsorbTest.java`:

```java
package com.hayfeeder.feature.feeder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComputeAbsorbTest {

    @Test
    void empty_takes_accepted_food_capped_at_capacity() {
        int n = HayFeederBlockEntity.computeAbsorb(ItemStack.EMPTY, new ItemStack(Items.WHEAT, 16));
        assertEquals(8, n);
    }

    @Test
    void empty_takes_partial_stack_in_full() {
        int n = HayFeederBlockEntity.computeAbsorb(ItemStack.EMPTY, new ItemStack(Items.WHEAT, 5));
        assertEquals(5, n);
    }

    @Test
    void empty_rejects_non_accepted() {
        int n = HayFeederBlockEntity.computeAbsorb(ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 5));
        assertEquals(0, n);
    }

    @Test
    void empty_rejects_cookie() {
        int n = HayFeederBlockEntity.computeAbsorb(ItemStack.EMPTY, new ItemStack(Items.COOKIE, 8));
        assertEquals(0, n);
    }

    @Test
    void partial_tops_up_same_food() {
        int n = HayFeederBlockEntity.computeAbsorb(new ItemStack(Items.WHEAT, 3), new ItemStack(Items.WHEAT, 4));
        assertEquals(4, n);
    }

    @Test
    void partial_caps_at_capacity_during_topup() {
        int n = HayFeederBlockEntity.computeAbsorb(new ItemStack(Items.WHEAT, 3), new ItemStack(Items.WHEAT, 16));
        assertEquals(5, n);
    }

    @Test
    void partial_rejects_different_food() {
        int n = HayFeederBlockEntity.computeAbsorb(new ItemStack(Items.WHEAT, 3), new ItemStack(Items.CARROT, 5));
        assertEquals(0, n);
    }

    @Test
    void full_rejects_more_of_same_food() {
        int n = HayFeederBlockEntity.computeAbsorb(new ItemStack(Items.WHEAT, 8), new ItemStack(Items.WHEAT, 5));
        assertEquals(0, n);
    }
}
```

- [ ] **Step 2: Run test, expect compile failure**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew test --tests ComputeAbsorbTest
```

Expected: `BUILD FAILED` with `error: cannot find symbol` for `HayFeederBlockEntity`.

- [ ] **Step 3: Create the BlockEntity skeleton with `computeAbsorb`**

Create `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java`:

```java
package com.hayfeeder.feature.feeder;

import net.minecraft.world.item.ItemStack;

public class HayFeederBlockEntity {
    public static final int CAPACITY = 8;

    static int computeAbsorb(ItemStack contents, ItemStack incoming) {
        if (contents.isEmpty()) {
            return AcceptedFoods.isAccepted(incoming)
                    ? Math.min(incoming.getCount(), CAPACITY)
                    : 0;
        }
        if (contents.is(incoming.getItem())) {
            return Math.min(incoming.getCount(), CAPACITY - contents.getCount());
        }
        return 0;
    }
}
```

> **Note:** This file does **not yet** extend `BlockEntity` — Task 4 promotes it. Keeping the skeleton minimal lets `computeAbsorb` be tested without dragging in MC's BE machinery.

- [ ] **Step 4: Run test, expect pass**

```bash
./gradlew test --tests ComputeAbsorbTest
```

Expected: `BUILD SUCCESSFUL`, 8 tests pass.

- [ ] **Step 5: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java \
        neoforge/src/test/java/com/hayfeeder/feature/feeder/ComputeAbsorbTest.java
git commit -m "feat(neoforge): add computeAbsorb refill math for hay_feeder"
```

---

### Task 4: Promote `HayFeederBlockEntity` to a real `BlockEntity`

**Files:**
- Modify: `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java`
- Create: `neoforge/src/main/java/com/hayfeeder/registry/ModBlockEntities.java`
- Modify: `neoforge/src/main/java/com/hayfeeder/HayFeeder.java`

This task has no new unit tests (mutation logic + registry wiring; verified by build success and downstream integration tests).

- [ ] **Step 1: Replace `HayFeederBlockEntity` with the full BE class**

Overwrite `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java`:

```java
package com.hayfeeder.feature.feeder;

import com.hayfeeder.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.ParticleTypes;

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
        int absorbed = computeAbsorb(this.contents, incoming);
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

    static int computeAbsorb(ItemStack contents, ItemStack incoming) {
        if (contents.isEmpty()) {
            return AcceptedFoods.isAccepted(incoming)
                    ? Math.min(incoming.getCount(), CAPACITY)
                    : 0;
        }
        if (contents.is(incoming.getItem())) {
            return Math.min(incoming.getCount(), CAPACITY - contents.getCount());
        }
        return 0;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("contents")) {
            this.contents = ItemStack.parseOptional(registries, tag.getCompound("contents"));
        } else {
            this.contents = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!contents.isEmpty()) {
            tag.put("contents", contents.save(registries, new CompoundTag()));
        }
    }
}
```

- [ ] **Step 2: Create `ModBlockEntities` registry**

Create `neoforge/src/main/java/com/hayfeeder/registry/ModBlockEntities.java`:

```java
package com.hayfeeder.registry;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.feature.feeder.HayFeederBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HayFeeder.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HayFeederBlockEntity>> HAY_FEEDER =
            BLOCK_ENTITIES.register("hay_feeder", () ->
                    BlockEntityType.Builder.of(HayFeederBlockEntity::new, ModBlocks.HAY_FEEDER.get()).build(null));

    private ModBlockEntities() {}
}
```

- [ ] **Step 3: Wire `ModBlockEntities` into the entry point**

Modify `neoforge/src/main/java/com/hayfeeder/HayFeeder.java`. The class currently has 3 `register` calls. Add a fourth.

Replace the constructor body (currently lines 16-22):

```java
    public HayFeeder(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        LOGGER.info("Hay Feeder initialised");
    }
```

Add the import at the top:

```java
import com.hayfeeder.registry.ModBlockEntities;
```

- [ ] **Step 4: Build to verify compilation**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. The Block class still compiles (it doesn't yet reference the BE — that's Task 6).

- [ ] **Step 5: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlockEntity.java \
        neoforge/src/main/java/com/hayfeeder/registry/ModBlockEntities.java \
        neoforge/src/main/java/com/hayfeeder/HayFeeder.java
git commit -m "feat(neoforge): register HayFeederBlockEntity with persistence and feeding tick"
```

---

### Task 5: `FeedingMechanic` static helper (NeoForge)

**Files:**
- Create: `neoforge/src/main/java/com/hayfeeder/feature/feeder/FeedingMechanic.java`

No unit test — depends on `Animal` server-side state which is impractical to bootstrap in JUnit. Verified by manual integration in Task 8.

- [ ] **Step 1: Create `FeedingMechanic`**

Create `neoforge/src/main/java/com/hayfeeder/feature/feeder/FeedingMechanic.java`:

```java
package com.hayfeeder.feature.feeder;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

public final class FeedingMechanic {
    private static final byte ENTITY_EVENT_IN_LOVE_HEARTS = 18;

    private FeedingMechanic() {}

    public static void feedAnimal(ServerLevel level, Animal animal) {
        if (animal.isBaby()) {
            animal.ageUp((int) ((-animal.getAge() / 20) * 0.1F), true);
        } else if (animal.canFallInLove()) {
            animal.setInLove(null);
        }
        level.broadcastEntityEvent(animal, ENTITY_EVENT_IN_LOVE_HEARTS);
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/feature/feeder/FeedingMechanic.java
git commit -m "feat(neoforge): add FeedingMechanic helper (vanilla setInLove + ageUp)"
```

---

### Task 6: Wire `HayFeederBlock` (NeoForge) — `EntityBlock`, `randomTick`, `useItemOn`, `onRemove`

**Files:**
- Modify: `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java`

The current scaffold extends `Block` and declares `FEEDS_LEFT`. We add `EntityBlock` implementation, the four overrides, and a tick-randomly hint.

- [ ] **Step 1: Replace `HayFeederBlock.java` with the full version**

Overwrite `neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java`:

```java
package com.hayfeeder.feature.feeder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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
        registerDefaultState(stateDefinition.any().setValue(FEEDS_LEFT, MAX_FEEDS));
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof HayFeederBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        int absorbed = be.tryRefill(stack);
        if (absorbed == 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!player.getAbilities().instabuild) {
            stack.shrink(absorbed);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
                ItemStack remaining = be.getContents();
                if (!remaining.isEmpty()) {
                    Block.popResource(level, pos, remaining.copy());
                }
            }
            Block.popResource(level, pos, new ItemStack(Items.HAY_BLOCK));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
```

> **Why `props.randomTicks()`:** the scaffold's `BlockBehaviour.Properties.ofFullCopy(Blocks.HAY_BLOCK)` does NOT enable random-ticking (vanilla hay_block is not random-ticked). We have to opt in explicitly.

- [ ] **Step 2: Build to verify compilation**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

If `ItemInteractionResult` is missing in MC 26.1: this enum was renamed in some versions. Likely alternatives: `InteractionResult` (older), `BlockInteractionResult`. Check compile error and adjust signature accordingly. The vanilla method to override is whatever `Block` exposes in your toolchain; `useItemOn(ItemStack, BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)` is the MC 1.21.x signature.

- [ ] **Step 3: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java
git commit -m "feat(neoforge): wire HayFeederBlock to BlockEntity for feeding logic"
```

---

### Task 7: Recipe + lang updates (NeoForge)

**Files:**
- Create: `neoforge/src/main/resources/data/hay_feeder/recipe/hay_feeder.json`
- Modify: `neoforge/src/main/resources/assets/hay_feeder/lang/en_us.json`

- [ ] **Step 1: Create the recipe JSON**

Create `neoforge/src/main/resources/data/hay_feeder/recipe/hay_feeder.json`:

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    "minecraft:hay_block"
  ],
  "result": {
    "id": "hay_feeder:hay_feeder",
    "count": 1
  }
}
```

- [ ] **Step 2: Add the empty-tooltip key to en_us**

Replace `neoforge/src/main/resources/assets/hay_feeder/lang/en_us.json` content with:

```json
{
  "itemGroup.hay_feeder.main": "Hay Feeder",
  "block.hay_feeder.hay_feeder": "Hay Feeder",
  "block.hay_feeder.hay_feeder.empty": "Empty — right-click with food to fill"
}
```

> The `.empty` key is wired to the item tooltip in a follow-up tweak (not in this plan); for now it just lives in the lang file ready to be referenced.

- [ ] **Step 3: Build to verify resources are valid JSON**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. Resource files are copied into the jar.

- [ ] **Step 4: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/resources/data/hay_feeder/recipe/hay_feeder.json \
        neoforge/src/main/resources/assets/hay_feeder/lang/en_us.json
git commit -m "feat(neoforge): add hay_feeder recipe and empty-state translation key"
```

---

### Task 8: NeoForge manual integration test

**Goal:** verify the feeder works in-game before porting to Fabric. NO unit tests for this task — pure manual verification.

- [ ] **Step 1: Launch dev client**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/neoforge
export JAVA_HOME=$HOME/.local/jdks/current PATH=$JAVA_HOME/bin:$PATH
./gradlew runClient
```

Expected: Minecraft launches into the main menu.

- [ ] **Step 2: Create a creative-mode test world**

In-game: `Singleplayer → Create New World → Game Mode: Creative → Difficulty: Peaceful → Create New World`.

- [ ] **Step 3: Verify creative tab + recipe**

- Open inventory (`E`), find the "Hay Feeder" tab (icon: hay feeder block).
- Pick up a `hay_feeder` block.
- Open the recipe book (`R` key inside crafting GUI). Search for "hay feeder". Confirm the recipe `1× hay_block → 1× hay_feeder` shows up.

Expected: tab visible, item retrievable, recipe listed.

- [ ] **Step 4: Verify block placement and blockstate**

- Place the `hay_feeder` on the ground.
- Look at it and press `F3`.
- In the F3 sidebar, confirm the block is `hay_feeder:hay_feeder` and `feeds_left=8`.

Expected: blockstate shows `feeds_left=8`.

- [ ] **Step 5: Verify right-click refill**

- With `wheat` in your hand, right-click the placed feeder.
- Check F3: `feeds_left` should still be `8` if you held a stack of ≥8 wheat (top-up no-op since already full). Place a new feeder with empty contents — actually wait, scaffold default state is `feeds_left=8` but the BE starts empty. So we need to fix the scaffold default OR this test fails.

> **Heads-up to verifier:** The scaffold's `registerDefaultState` sets `FEEDS_LEFT = 8` (cosmetic), but the BE's `contents` starts at `EMPTY`. On placement, the blockstate says "8" but actually nothing is loaded. **Fix:** in `HayFeederBlock`, change `registerDefaultState` to `setValue(FEEDS_LEFT, 0)`, OR sync on placement via `setPlacedBy`. Decide here:
> - Cleanest fix: change default `FEEDS_LEFT` to `0`. The blockstate now accurately reflects an empty feeder on placement.
> - Apply that fix now and re-build before continuing.

Apply the fix:
```java
// In HayFeederBlock constructor:
registerDefaultState(stateDefinition.any().setValue(FEEDS_LEFT, 0));
```

Re-run `./gradlew build` and `./gradlew runClient`. Then proceed.

- [ ] **Step 6: Verify refill flow**

- Place a fresh feeder (now `feeds_left=0`).
- Right-click with stack of 16 wheat. Expected: stack drops to 8 wheat in hand, `feeds_left=8` in F3.
- Right-click again with 16 carrots. Expected: nothing happens (different food, full feeder rejects).
- Sneak-break the feeder. Expected: hay_block + 8 wheat drop.

- [ ] **Step 7: Verify feeding tick**

- Place a feeder (full of wheat).
- Spawn 2-3 cows nearby (`/summon cow ~ ~ ~`).
- Wait. With `randomTickSpeed=3` (default), expect a feed event roughly every 60-90s.
- **Speed up for testing:** `/gamerule randomTickSpeed 100`. Now feeds fire ~33× faster. Within ~2-3s a feed event should occur:
  - Cows enter love mode (visible heart particles, breeding).
  - Particles puff at the bale.
  - `feeds_left` decrements in F3.
  - Soft `block.grass.break` sound plays.
- Reset `/gamerule randomTickSpeed 3` after.

Expected: all 4 feedback channels (hearts, particles, sound, blockstate decrement) fire on each tick.

- [ ] **Step 8: Commit any fixes from this task**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add neoforge/src/main/java/com/hayfeeder/feature/feeder/HayFeederBlock.java
git commit -m "fix(neoforge): default feeds_left to 0 to match empty BE state on placement"
```

If no fixes needed, skip this step.

---

### Task 9: Fabric port — pure-logic units (`AcceptedFoods`, `HayFeederBlockEntity` skeleton with `computeAbsorb`)

**Files:**
- Create: `fabric/src/main/java/com/hayfeeder/fabric/AcceptedFoods.java`
- Create: `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntity.java` *(initial skeleton just like Task 3, then promoted in Task 11)*
- Create: `fabric/src/test/java/com/hayfeeder/fabric/AcceptedFoodsTest.java`
- Create: `fabric/src/test/java/com/hayfeeder/fabric/ComputeAbsorbTest.java`

The Java is identical to NeoForge except for package paths. Repeat for clarity instead of pointing at "Task 2" — engineers may read out of order.

- [ ] **Step 1: Copy `AcceptedFoods` into Fabric, package-adjusted**

Create `fabric/src/main/java/com/hayfeeder/fabric/AcceptedFoods.java`:

```java
package com.hayfeeder.fabric;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

public final class AcceptedFoods {
    private static final Set<Item> ACCEPTED = Set.of(
            Items.WHEAT, Items.CARROT, Items.BEETROOT,
            Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS,
            Items.COD, Items.SALMON, Items.TROPICAL_FISH,
            Items.SWEET_BERRIES, Items.GLOW_BERRIES,
            Items.APPLE
    );

    private AcceptedFoods() {}

    public static boolean isAccepted(ItemStack stack) {
        return !stack.isEmpty() && ACCEPTED.contains(stack.getItem());
    }
}
```

- [ ] **Step 2: Copy `HayFeederBlockEntity` skeleton with `computeAbsorb`**

Create `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntity.java`:

```java
package com.hayfeeder.fabric;

import net.minecraft.world.item.ItemStack;

public class HayFeederBlockEntity {
    public static final int CAPACITY = 8;

    static int computeAbsorb(ItemStack contents, ItemStack incoming) {
        if (contents.isEmpty()) {
            return AcceptedFoods.isAccepted(incoming)
                    ? Math.min(incoming.getCount(), CAPACITY)
                    : 0;
        }
        if (contents.is(incoming.getItem())) {
            return Math.min(incoming.getCount(), CAPACITY - contents.getCount());
        }
        return 0;
    }
}
```

- [ ] **Step 3: Create the Fabric `AcceptedFoodsTest`**

Create `fabric/src/test/java/com/hayfeeder/fabric/AcceptedFoodsTest.java`:

```java
package com.hayfeeder.fabric;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptedFoodsTest {

    @Test
    void wheat_is_accepted() {
        assertTrue(AcceptedFoods.isAccepted(new ItemStack(Items.WHEAT)));
    }

    @Test
    void carrot_is_accepted() {
        assertTrue(AcceptedFoods.isAccepted(new ItemStack(Items.CARROT)));
    }

    @Test
    void salmon_is_accepted() {
        assertTrue(AcceptedFoods.isAccepted(new ItemStack(Items.SALMON)));
    }

    @Test
    void glow_berries_are_accepted() {
        assertTrue(AcceptedFoods.isAccepted(new ItemStack(Items.GLOW_BERRIES)));
    }

    @Test
    void cookie_is_rejected_to_protect_parrots() {
        assertFalse(AcceptedFoods.isAccepted(new ItemStack(Items.COOKIE)));
    }

    @Test
    void golden_carrot_is_rejected_to_avoid_trivialising_horse_breeding() {
        assertFalse(AcceptedFoods.isAccepted(new ItemStack(Items.GOLDEN_CARROT)));
    }

    @Test
    void enchanted_golden_apple_is_rejected() {
        assertFalse(AcceptedFoods.isAccepted(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
    }

    @Test
    void diamond_is_rejected() {
        assertFalse(AcceptedFoods.isAccepted(new ItemStack(Items.DIAMOND)));
    }

    @Test
    void empty_stack_is_rejected() {
        assertFalse(AcceptedFoods.isAccepted(ItemStack.EMPTY));
    }
}
```

- [ ] **Step 4: Create the Fabric `ComputeAbsorbTest`**

Create `fabric/src/test/java/com/hayfeeder/fabric/ComputeAbsorbTest.java`:

```java
package com.hayfeeder.fabric;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComputeAbsorbTest {

    @Test
    void empty_takes_accepted_food_capped_at_capacity() {
        int n = HayFeederBlockEntity.computeAbsorb(ItemStack.EMPTY, new ItemStack(Items.WHEAT, 16));
        assertEquals(8, n);
    }

    @Test
    void empty_takes_partial_stack_in_full() {
        int n = HayFeederBlockEntity.computeAbsorb(ItemStack.EMPTY, new ItemStack(Items.WHEAT, 5));
        assertEquals(5, n);
    }

    @Test
    void empty_rejects_non_accepted() {
        int n = HayFeederBlockEntity.computeAbsorb(ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 5));
        assertEquals(0, n);
    }

    @Test
    void empty_rejects_cookie() {
        int n = HayFeederBlockEntity.computeAbsorb(ItemStack.EMPTY, new ItemStack(Items.COOKIE, 8));
        assertEquals(0, n);
    }

    @Test
    void partial_tops_up_same_food() {
        int n = HayFeederBlockEntity.computeAbsorb(new ItemStack(Items.WHEAT, 3), new ItemStack(Items.WHEAT, 4));
        assertEquals(4, n);
    }

    @Test
    void partial_caps_at_capacity_during_topup() {
        int n = HayFeederBlockEntity.computeAbsorb(new ItemStack(Items.WHEAT, 3), new ItemStack(Items.WHEAT, 16));
        assertEquals(5, n);
    }

    @Test
    void partial_rejects_different_food() {
        int n = HayFeederBlockEntity.computeAbsorb(new ItemStack(Items.WHEAT, 3), new ItemStack(Items.CARROT, 5));
        assertEquals(0, n);
    }

    @Test
    void full_rejects_more_of_same_food() {
        int n = HayFeederBlockEntity.computeAbsorb(new ItemStack(Items.WHEAT, 8), new ItemStack(Items.WHEAT, 5));
        assertEquals(0, n);
    }
}
```

- [ ] **Step 5: Run Fabric tests**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
export JAVA_HOME=$HOME/.local/jdks/current25 PATH=$JAVA_HOME/bin:$PATH
./gradlew test --tests AcceptedFoodsTest --tests ComputeAbsorbTest
```

Expected: `BUILD SUCCESSFUL`, 17 tests pass (9 + 8).

- [ ] **Step 6: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add fabric/src/main/java/com/hayfeeder/fabric/AcceptedFoods.java \
        fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntity.java \
        fabric/src/test/java/com/hayfeeder/fabric/AcceptedFoodsTest.java \
        fabric/src/test/java/com/hayfeeder/fabric/ComputeAbsorbTest.java
git commit -m "feat(fabric): port AcceptedFoods and computeAbsorb refill math"
```

---

### Task 10: Fabric `FeedingMechanic`

**Files:**
- Create: `fabric/src/main/java/com/hayfeeder/fabric/FeedingMechanic.java`

- [ ] **Step 1: Create `FeedingMechanic`**

Create `fabric/src/main/java/com/hayfeeder/fabric/FeedingMechanic.java`:

```java
package com.hayfeeder.fabric;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

public final class FeedingMechanic {
    private static final byte ENTITY_EVENT_IN_LOVE_HEARTS = 18;

    private FeedingMechanic() {}

    public static void feedAnimal(ServerLevel level, Animal animal) {
        if (animal.isBaby()) {
            animal.ageUp((int) ((-animal.getAge() / 20) * 0.1F), true);
        } else if (animal.canFallInLove()) {
            animal.setInLove(null);
        }
        level.broadcastEntityEvent(animal, ENTITY_EVENT_IN_LOVE_HEARTS);
    }
}
```

- [ ] **Step 2: Build**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add fabric/src/main/java/com/hayfeeder/fabric/FeedingMechanic.java
git commit -m "feat(fabric): port FeedingMechanic helper"
```

---

### Task 11: Promote Fabric `HayFeederBlockEntity` and register `BlockEntityType`

**Files:**
- Modify: `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntity.java`
- Create: `fabric/src/main/java/com/hayfeeder/fabric/ModBlockEntities.java`
- Modify: `fabric/src/main/java/com/hayfeeder/fabric/HayFeederFabric.java`

- [ ] **Step 1: Replace `HayFeederBlockEntity` with the full BE class (Fabric)**

Overwrite `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntity.java`:

```java
package com.hayfeeder.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class HayFeederBlockEntity extends BlockEntity {
    public static final int CAPACITY = 8;
    public static final double FEED_RADIUS = 6.0;

    private ItemStack contents = ItemStack.EMPTY;

    public HayFeederBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAY_FEEDER, pos, state);
    }

    public ItemStack getContents() {
        return contents;
    }

    public int tryRefill(ItemStack incoming) {
        int absorbed = computeAbsorb(this.contents, incoming);
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

    static int computeAbsorb(ItemStack contents, ItemStack incoming) {
        if (contents.isEmpty()) {
            return AcceptedFoods.isAccepted(incoming)
                    ? Math.min(incoming.getCount(), CAPACITY)
                    : 0;
        }
        if (contents.is(incoming.getItem())) {
            return Math.min(incoming.getCount(), CAPACITY - contents.getCount());
        }
        return 0;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("contents")) {
            this.contents = ItemStack.parseOptional(registries, tag.getCompound("contents"));
        } else {
            this.contents = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!contents.isEmpty()) {
            tag.put("contents", contents.save(registries, new CompoundTag()));
        }
    }
}
```

- [ ] **Step 2: Create `ModBlockEntities` (Fabric)**

Create `fabric/src/main/java/com/hayfeeder/fabric/ModBlockEntities.java`:

```java
package com.hayfeeder.fabric;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final BlockEntityType<HayFeederBlockEntity> HAY_FEEDER = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(HayFeederFabric.MOD_ID, "hay_feeder"),
            BlockEntityType.Builder.of(HayFeederBlockEntity::new, ModBlocks.HAY_FEEDER).build(null));

    private ModBlockEntities() {}

    public static void bootstrap() {}
}
```

- [ ] **Step 3: Wire `ModBlockEntities.bootstrap()` in `HayFeederFabric`**

Modify `fabric/src/main/java/com/hayfeeder/fabric/HayFeederFabric.java`. Replace the body of `onInitialize()`:

```java
    @Override
    public void onInitialize() {
        ModBlocks.bootstrap();
        ModItems.bootstrap();
        ModBlockEntities.bootstrap();
        ModCreativeTabs.bootstrap();

        LOGGER.info("Hay Feeder (Fabric) initialised");
    }
```

- [ ] **Step 4: Build to verify**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. The `HayFeederBlock.java` (Fabric) does not yet reference the BE — that's the next task.

- [ ] **Step 5: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlockEntity.java \
        fabric/src/main/java/com/hayfeeder/fabric/ModBlockEntities.java \
        fabric/src/main/java/com/hayfeeder/fabric/HayFeederFabric.java
git commit -m "feat(fabric): register HayFeederBlockEntity with persistence and feeding tick"
```

---

### Task 12: Wire Fabric `HayFeederBlock` (`EntityBlock`, `randomTick`, `useItemOn`, `onRemove`)

**Files:**
- Modify: `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlock.java`

The Fabric block class mirrors NeoForge with one key signature difference: Fabric's `Block.useItemOn` may use a different return type. Check the local API and adapt; the logic is identical.

- [ ] **Step 1: Replace `HayFeederBlock.java` with the full version (Fabric)**

Overwrite `fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlock.java`:

```java
package com.hayfeeder.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof HayFeederBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        int absorbed = be.tryRefill(stack);
        if (absorbed == 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!player.getAbilities().instabuild) {
            stack.shrink(absorbed);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
                ItemStack remaining = be.getContents();
                if (!remaining.isEmpty()) {
                    Block.popResource(level, pos, remaining.copy());
                }
            }
            Block.popResource(level, pos, new ItemStack(Items.HAY_BLOCK));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
```

- [ ] **Step 2: Build**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

If the `useItemOn` signature or `ItemInteractionResult` type differs in vanilla Fabric mappings: adapt to match. The logic stays the same.

- [ ] **Step 3: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add fabric/src/main/java/com/hayfeeder/fabric/HayFeederBlock.java
git commit -m "feat(fabric): wire HayFeederBlock to BlockEntity for feeding logic"
```

---

### Task 13: Fabric recipe + lang updates

**Files:**
- Create: `fabric/src/main/resources/data/hay_feeder/recipe/hay_feeder.json`
- Modify: `fabric/src/main/resources/assets/hay_feeder/lang/en_us.json`

- [ ] **Step 1: Create the recipe JSON (identical to NeoForge)**

```bash
mkdir -p ~/Dev/mods/minecraft-revamp/hay_feeder/fabric/src/main/resources/data/hay_feeder/recipe
```

Create `fabric/src/main/resources/data/hay_feeder/recipe/hay_feeder.json`:

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    "minecraft:hay_block"
  ],
  "result": {
    "id": "hay_feeder:hay_feeder",
    "count": 1
  }
}
```

- [ ] **Step 2: Update Fabric lang**

Replace `fabric/src/main/resources/assets/hay_feeder/lang/en_us.json` content with:

```json
{
  "itemGroup.hay_feeder.main": "Hay Feeder",
  "block.hay_feeder.hay_feeder": "Hay Feeder",
  "block.hay_feeder.hay_feeder.empty": "Empty — right-click with food to fill"
}
```

- [ ] **Step 3: Build**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git add fabric/src/main/resources/data/hay_feeder/recipe/hay_feeder.json \
        fabric/src/main/resources/assets/hay_feeder/lang/en_us.json
git commit -m "feat(fabric): add hay_feeder recipe and empty-state translation key"
```

---

### Task 14: Fabric manual integration test

Repeat Task 8's manual test scenarios on Fabric:

- [ ] **Step 1: Launch dev client**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
export JAVA_HOME=$HOME/.local/jdks/current25 PATH=$JAVA_HOME/bin:$PATH
./gradlew runClient
```

- [ ] **Step 2: Re-run the same test scenarios from Task 8 steps 2-7**

Specifically:
- Creative tab visible, item retrievable
- Recipe `hay_block → hay_feeder` listed in recipe book
- Block places with `feeds_left=0`
- Right-click 16 wheat → `feeds_left=8`, hand has 8 wheat
- Right-click 16 carrots → no-op (different food on full feeder)
- Sneak-break → drops 1 hay_block + 8 wheat
- `/gamerule randomTickSpeed 100`, summon cows, observe feed events

- [ ] **Step 3: Cross-loader sanity check**

Stop the Fabric client. Check that the JAR builds cleanly from a cold cache:

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder/fabric
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL`. JAR present at `build/libs/hay_feeder-fabric-0.1.0.jar`.

- [ ] **Step 4: Mark the milestone**

```bash
cd ~/Dev/mods/minecraft-revamp/hay_feeder
git tag v0.1.0-feeding-logic
```

This tag marks the completion of the feeding-logic milestone. Push when ready.

---

## Done criteria

The implementation is complete when ALL of the following hold:

- [ ] All 17 unit tests pass on both NeoForge and Fabric (9 AcceptedFoods + 8 ComputeAbsorb per loader)
- [ ] `./gradlew build` is green on both loaders from a clean cache
- [ ] In-game on NeoForge: place feeder, fill with wheat, summon cows nearby, with `randomTickSpeed=100` → cows enter love mode within seconds, particles + sound fire, `feeds_left` decrements, eventually empties to 0
- [ ] In-game on Fabric: same scenario as above
- [ ] Sneak-breaking a feeder drops 1 hay_block + the remaining contents on both loaders
- [ ] Right-clicking with golden_carrot / cookie / diamond → no absorption, vanilla right-click falls through

When all boxes are checked, update `hay_feeder/CLAUDE.md` to reflect Q1/Q3/Q4 as `✅ DECIDED`, drop the "open design questions" warning at the top, and bump the per-mod README's Status from `Scaffold` to `Released-candidate` (or whatever next milestone you target).
