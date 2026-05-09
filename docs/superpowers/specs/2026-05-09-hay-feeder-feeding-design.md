# Hay Feeder — feeding logic design

**Date:** 2026-05-09
**Status:** Approved — ready for implementation plan
**Mod:** `hay_feeder` (part of the [Minecraft Revamp](../../../../README.md) collective)

## Summary

The `hay_feeder` block is a **universal animal feeder**. The player crafts an empty feeder from one `minecraft:hay_block`, places it, then right-clicks it with any whitelisted vanilla animal food. The feeder absorbs up to 8 charges of that food and stores it in a `BlockEntity`. While charged, random-tick events feed every eligible vanilla `Animal` in a 6-block radius using vanilla feeding semantics (`setInLove` for adults, `ageUp` for babies). When depleted, the feeder block persists empty and is refillable indefinitely. Breaking the block drops the hay block and any remaining contents.

## Locked decisions

| Question | Decision |
|---|---|
| **Q1** Targeting | Runtime `animal.isFood(contents)` check |
| **Q2** Depletion mechanism | Custom block + `BlockEntity` storing `ItemStack contents` (max 8). `feeds_left` blockstate 0-8 synced from BE for visual variants |
| **Q3a** Cadence | Vanilla `randomTick` — no `BlockEntity#tick` |
| **Q3b** Detection radius | 6 blocks (13×13×13 AABB centered on the bale) |
| **Q3c** Distribution | All eligible animals in range fed at once; one charge consumed per random-tick |
| **Q3d** Empty state | Persists empty, refillable indefinitely |
| **Q4** Feedback | Particles at the bale + discreet ambient sound on feed event |
| **Q5** Recipe | Shapeless 1×1: `minecraft:hay_block` → `hay_feeder:hay_feeder` (empty) |
| **Capacity** | 8 charges |
| **Refill type-change** | Empty = swappable, partial = same type only |
| **Whitelist** | Strict — protects parrots from cookies, excludes luxury foods that would trivialise horse breeding |

## Accepted food whitelist

The feeder absorbs only these items on right-click. Any other item passes through to vanilla right-click handling:

```
minecraft:wheat
minecraft:carrot
minecraft:beetroot
minecraft:wheat_seeds
minecraft:melon_seeds
minecraft:pumpkin_seeds
minecraft:beetroot_seeds
minecraft:cod
minecraft:salmon
minecraft:tropical_fish
minecraft:sweet_berries
minecraft:glow_berries
minecraft:apple
```

Excluded by design: `cookie` (fatal to parrots), `golden_carrot` / `golden_apple` / `enchanted_golden_apple` (would trivialise horse/donkey breeding), and any non-food.

## Architecture

Four units, each with a single responsibility. The split is mirrored across both loaders; package paths differ but content is identical.

### Unit 1: `HayFeederBlock` (extend existing)

- **NeoForge:** `com.hayfeeder.feature.feeder.HayFeederBlock`
- **Fabric:** `com.hayfeeder.fabric.HayFeederBlock`

Already extends `Block` with the `feeds_left` IntegerProperty (scaffold). The plan adds:

- Implement `EntityBlock` (NeoForge) / equivalent Fabric idiom
- Override `newBlockEntity(pos, state)` → returns a `HayFeederBlockEntity`
- Override `randomTick(state, level, pos, random)` → look up the BE, delegate to `tickFeeding`
- Override `useItemOn(stack, state, level, pos, player, hand, hit)` → delegate to `tryRefill`; if `> 0` items were absorbed, shrink the player's stack and return `InteractionResult.SUCCESS`; otherwise return `PASS`
- Override `onRemove(state, level, pos, newState, isMoving)` (NeoForge) / `Block.dropResources` hook (Fabric) → drop `contents` as items at `pos.center()` so the player doesn't lose their food

The block class holds no mutable state. All persistent state lives in the BlockEntity.

### Unit 2: `HayFeederBlockEntity` (new)

- **NeoForge:** `com.hayfeeder.feature.feeder.HayFeederBlockEntity`
- **Fabric:** `com.hayfeeder.fabric.HayFeederBlockEntity`

Extends `BlockEntity`. **No `tick` method** — feeding is driven by the block's `randomTick`, so the BE incurs no per-tick cost.

**State:**
```
private ItemStack contents = ItemStack.EMPTY;
```

**Persistence:** `loadAdditional` / `saveAdditional` serialize a single `ItemStack` field via the MC 26.1 standard `ItemStack.OPTIONAL_CODEC` against the BE's NBT.

**API:**

`tickFeeding(ServerLevel level, BlockPos pos, BlockState state)`:
1. If `contents.isEmpty()` → return.
2. Scan: `level.getEntitiesOfClass(Animal.class, AABB.ofSize(pos.getCenter(), 13, 13, 13))`.
3. Filter: `a -> a.isFood(contents)`.
4. Empty list → return. **No charge consumed when nobody is hungry** — the bale waits.
5. For each animal in the filtered list: `FeedingMechanic.feedAnimal(level, animal)`.
6. Spawn ~5 `ParticleTypes.HAPPY_VILLAGER` particles at `pos.getCenter()` (server broadcasts to clients in range).
7. Play `SoundEvents.GRASS_BREAK` at low volume (0.3) and pitch ~1.0 — gentle rustle that won't false-positive as cow noise.
8. `contents.shrink(1)`.
9. Sync blockstate: `level.setBlock(pos, state.setValue(FEEDS_LEFT, contents.getCount()), Block.UPDATE_ALL)`.
10. `setChanged()`.

`tryRefill(ItemStack stack)` → `int absorbed`:
- Empty feeder + `AcceptedFoods.isAccepted(stack)` → set `contents = stack.copyWithCount(min(stack.count, 8))`. Return `contents.count`.
- Partial feeder + `contents.is(stack.getItem())` → grow `contents` by `min(stack.count, 8 - contents.count)`. Return the delta.
- Else → return 0.
- On non-zero: `setChanged()` and sync the blockstate.

### Unit 3: `FeedingMechanic` (new, static)

- **NeoForge:** `com.hayfeeder.feature.feeder.FeedingMechanic`
- **Fabric:** `com.hayfeeder.fabric.FeedingMechanic`

```java
public static void feedAnimal(ServerLevel level, Animal animal) {
    if (animal.isBaby()) {
        animal.ageUp((int)((-animal.getAge() / 20) * 0.1F), true);
    } else if (animal.canFallInLove()) {
        animal.setInLove(null);  // null player = no XP attribution to a feeder
    }
    level.broadcastEntityEvent(animal, (byte) 18);  // EntityEvent.IN_LOVE_HEARTS
}
```

The aging-up formula matches vanilla `Animal.usePlayerItem`. Caller is expected to have filtered for `isFood(stack)` — no validation here.

### Unit 4: `AcceptedFoods` (new, static)

- **NeoForge:** `com.hayfeeder.feature.feeder.AcceptedFoods`
- **Fabric:** `com.hayfeeder.fabric.AcceptedFoods`

```java
private static final Set<Item> ACCEPTED = Set.of(
    Items.WHEAT, Items.CARROT, Items.BEETROOT,
    Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS,
    Items.COD, Items.SALMON, Items.TROPICAL_FISH,
    Items.SWEET_BERRIES, Items.GLOW_BERRIES,
    Items.APPLE
);

public static boolean isAccepted(ItemStack stack) {
    return ACCEPTED.contains(stack.getItem());
}
```

If the whitelist needs to be data-driven later (datapack-extensible), promote this to a tag `#hay_feeder:accepted_foods`. Out of scope for now.

## Resource files (to add)

Mirrored on both loaders under `src/main/resources/`:

| File | Content |
|---|---|
| `data/hay_feeder/recipe/hay_feeder.json` | Shapeless 1×1: `[minecraft:hay_block]` → `hay_feeder:hay_feeder` |
| `assets/hay_feeder/lang/en_us.json` | Add `block.hay_feeder.hay_feeder.empty`: "Empty — right-click with food to fill" (used in tooltip via `appendHoverText`) |

The blockstate JSON, block model, item definition, item model already exist (scaffold). They stay; per-stage texture variants are author's TODO.

## Cadence math

With vanilla `randomTickSpeed = 3`:
- Per-block per-tick chance ≈ 0.073%
- At 20 TPS → ~1 random-tick every ~70 s per feeder
- 8 charges × 70 s ≈ **9–10 min for a saturated feeder to deplete**

If no eligible animals are within 6 blocks at random-tick time, the charge is **not** consumed. The feeder is idempotent on idle.

## Block break behavior

In the block-removal callback (NeoForge `onRemove(state, level, pos, newState, isMoving)`; Fabric `Block.onStateReplaced` or equivalent), before `super.onRemove`:

1. Look up the BE; if absent, skip.
2. If `contents` is non-empty, `Block.popResource(level, pos, contents.copy())` — drops remaining food.
3. `Block.popResource(level, pos, new ItemStack(Items.HAY_BLOCK))` — gives the player back the recipe input.

No loot-table file needed; drops are fully explicit. Player gets back exactly what they invested (1 hay_block + any unused food).

## Two-loader parity notes

- **Java content identical** between loaders, package paths only differ.
- **Registry idiom differs:**
  - NeoForge: `DeferredRegister<BlockEntityType<?>>` keyed by `Registries.BLOCK_ENTITY_TYPE`, registered alongside existing `ModBlocks` / `ModItems`.
  - Fabric: `Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, BlockEntityType.Builder.of(HayFeederBlockEntity::new, ModBlocks.HAY_FEEDER).build(null))` — note the `null` data type, standard since MC 1.21.
- **Recipe JSON identical** in both loaders, dropped at `data/hay_feeder/recipe/hay_feeder.json`.

## Out of scope (YAGNI)

- Hopper-fed auto-refill, `IItemHandler` capability, or any pipe interop
- Comparator output (`getAnalogOutputSignal`)
- GUI / inventory screen
- Particle/sound customization per food type
- Luxury food breeding (golden carrot / apple / enchanted apple)
- Llama hay-bale eating — vanilla already handles llamas eating `hay_block` directly
- Multi-food storage in one feeder (one type at a time)
- Refill via villager trade
- Per-stage textures — author will provide later; one model placeholder for all 9 stages now

## Implementation pointers

- **Existing scaffold stays as-is**: `HayFeederBlock` (with `feeds_left`), `ModBlocks` (registers it), `ModItems` (BlockItem), `ModCreativeTabs` (entry), placeholder model that inherits `minecraft:block/hay_block`. Plan adds new files + extends the block class.
- **First implementation pass on NeoForge** (richer event API for debugging), then port to Fabric. Diff-test the two `HayFeederBlock.java` files at the end.
- **Verification**: `./gradlew build` on NeoForge first; run `./gradlew runClient`, place a feeder, right-click with wheat, observe `feeds_left` blockstate decrement via F3 debug screen, observe nearby cow entering breed mode.

## References

- Per-mod CLAUDE.md: [`../../../CLAUDE.md`](../../../CLAUDE.md)
- Collective vision: [`../../../../CLAUDE.md`](../../../../CLAUDE.md)
- Brainstorm transcript: 2026-05-09 conversation with Claude
