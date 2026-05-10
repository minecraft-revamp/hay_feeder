# Hay Feeder — connected texture + shared inventory

**Date:** 2026-05-10
**Status:** Approved — ready for implementation
**Mod:** `hay_feeder`
**Builds on:** [`2026-05-10-hay-feeder-gui-design.md`](2026-05-10-hay-feeder-gui-design.md)

## Summary

When two or more `hay_feeder` blocks are placed adjacent (horizontally), they merge **visually** (shared walls disappear, creating a unified "trough" silhouette) and **functionally** (opening any of them shows a single pooled inventory across the group; the slot's whitelist constraint is enforced group-wide so all connected members must share the same food type). The implementation follows the vanilla double-chest pattern — federated storage, no shared state — to keep persistence robust against save/load and chunk-unload.

## Locked decisions

| Question | Decision |
|---|---|
| Storage model | **Federated** (each BE keeps its own `ItemStack`, never shared state). Group is computed lazily via BFS at menu-open / slot-validate / GUI-display time. |
| Adjacency scope | Horizontal only (N/S/E/W). Vertical stacking does **not** form a group. |
| Group size cap | None. Practical performance: BFS is bounded by world block adjacency; the constant factor (`level.getBlockState`, `level.getBlockEntity`) is cheap and only fires on user interaction. |
| GUI view | **Single virtual slot** displaying the pooled stack (count = sum of members' counts). Slot capacity = `members.size() × 64`. |
| Drag-in distribution | Fill members in BFS order until the input stack is exhausted. Each member caps at 64. |
| Drag-out withdraw | Take from members in reverse BFS order; combine into one stack handed to the player. |
| Same-food invariant | The group must hold a single item type at any time. Slot's `canPlaceItem` rejects any incoming stack whose item differs from any non-empty member of the group. |
| Connected texture | Multipart blockstate driven by 4 `BooleanProperty` (`north`, `south`, `east`, `west`). When a side is `true`, that wall is omitted from the model — the two boxes visually merge. |
| Block break | Drops only this block's hay_block + its own contents. Other group members keep their state. The group naturally resolves on next BFS — possibly splitting into 2+ groups if the broken block was a "bridge". |

## Architecture

### Unit 1: Blockstate refactor

Add 4 `BooleanProperty` to `HayFeederBlock`:

```java
public static final BooleanProperty NORTH = BooleanProperty.create("north");
public static final BooleanProperty SOUTH = BooleanProperty.create("south");
public static final BooleanProperty EAST  = BooleanProperty.create("east");
public static final BooleanProperty WEST  = BooleanProperty.create("west");
```

Default state: all false (no connection). The existing `FILL_STAGE` enum stays.

Override `getStateForPlacement(BlockPlaceContext)` to compute flags at placement time:

```java
@Override
public BlockState getStateForPlacement(BlockPlaceContext ctx) {
    return defaultBlockState()
        .setValue(NORTH, isFeeder(ctx.getLevel(), ctx.getClickedPos().north()))
        .setValue(SOUTH, isFeeder(ctx.getLevel(), ctx.getClickedPos().south()))
        .setValue(EAST,  isFeeder(ctx.getLevel(), ctx.getClickedPos().east()))
        .setValue(WEST,  isFeeder(ctx.getLevel(), ctx.getClickedPos().west()));
}

private static boolean isFeeder(BlockGetter level, BlockPos pos) {
    return level.getBlockState(pos).is(ModBlocks.HAY_FEEDER.get());
}
```

Override `updateShape(...)` (the MC hook for neighbor-state changes) to update the appropriate flag:

```java
@Override
protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess,
                                 BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
                                 RandomSource random) {
    if (direction.getAxis().isHorizontal()) {
        BooleanProperty prop = switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST  -> EAST;
            case WEST  -> WEST;
            default -> null;
        };
        if (prop != null) {
            return state.setValue(prop, neighborState.is(this));
        }
    }
    return state;
}
```

> The `updateShape` signature in MC 26.1 is the exact open question — implementation should verify against decompiled vanilla `FenceBlock.java` (the canonical multi-flag connection block). The signature varies across versions.

### Unit 2: Multipart blockstate JSON

Replace the current `assets/hay_feeder/blockstates/hay_feeder.json`:

```json
{
  "multipart": [
    { "apply": { "model": "hay_feeder:block/hay_feeder_floor" } },
    { "when": { "north": "false" }, "apply": { "model": "hay_feeder:block/hay_feeder_wall_north" } },
    { "when": { "south": "false" }, "apply": { "model": "hay_feeder:block/hay_feeder_wall_south" } },
    { "when": { "east":  "false" }, "apply": { "model": "hay_feeder:block/hay_feeder_wall_east"  } },
    { "when": { "west":  "false" }, "apply": { "model": "hay_feeder:block/hay_feeder_wall_west"  } },
    { "when": { "fill_stage": "half" }, "apply": { "model": "hay_feeder:block/hay_feeder_contents_half" } },
    { "when": { "fill_stage": "full" }, "apply": { "model": "hay_feeder:block/hay_feeder_contents_full" } }
  ]
}
```

### Unit 3: New per-part block models

Replace the existing `hay_feeder_empty.json`, `hay_feeder_half.json`, `hay_feeder_full.json` with a new set of separated models:

| File | Element |
|---|---|
| `block/hay_feeder_floor.json` | Single floor element `[0,0,0]→[16,2,16]`, `up=wood_bottom` (fix from earlier bug), `down=wood_bottom`, sides=wood_side |
| `block/hay_feeder_wall_north.json` | Single wall element `[0,2,0]→[16,16,2]`, `up=wood_top` (rim), all faces wood-textured |
| `block/hay_feeder_wall_south.json` | Single wall element `[0,2,14]→[16,16,16]`, `up=wood_top`, ... |
| `block/hay_feeder_wall_east.json`  | Single wall element `[14,2,2]→[16,16,14]`, `up=wood_top`, ... |
| `block/hay_feeder_wall_west.json`  | Single wall element `[0,2,2]→[2,16,14]`, `up=wood_top`, ... |
| `block/hay_feeder_contents_half.json` | Single contents element `[2,2,2]→[14,8,14]`, all 6 faces with `tintindex: 0` |
| `block/hay_feeder_contents_full.json` | Single contents element `[2,2,2]→[14,14,14]`, all 6 faces with `tintindex: 0` |
| `block/hay_feeder_inventory.json` | Bundled model for the inventory icon — floor + 4 walls + contents-full, used by `models/item/hay_feeder.json` |

The `models/item/hay_feeder.json` updates its parent to `hay_feeder:block/hay_feeder_inventory`.

### Unit 4: `FeederGroup` helper (new)

```java
package com.hayfeeder.feature.feeder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;

import java.util.*;

public final class FeederGroup {
    private FeederGroup() {}

    public static List<HayFeederBlockEntity> findMembers(LevelAccessor level, BlockPos start, Block block) {
        List<HayFeederBlockEntity> members = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!level.getBlockState(pos).is(block)) continue;
            if (!(level.getBlockEntity(pos) instanceof HayFeederBlockEntity be)) continue;
            members.add(be);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(d);
                if (visited.add(next)) queue.add(next);
            }
        }
        return members;
    }
}
```

### Unit 5: `GroupContainer` (new)

A virtual `Container` aggregating N member containers as a **single 1-slot view**.

```java
package com.hayfeeder.feature.feeder;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class GroupContainer implements Container {
    private final List<HayFeederBlockEntity> members;

    public GroupContainer(List<HayFeederBlockEntity> members) {
        this.members = members;
    }

    @Override
    public int getContainerSize() { return 1; }

    @Override
    public boolean isEmpty() {
        for (HayFeederBlockEntity be : members) if (!be.getContents().isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        // Return aggregated stack: type from first non-empty member, count = sum.
        ItemStack proto = ItemStack.EMPTY;
        int totalCount = 0;
        for (HayFeederBlockEntity be : members) {
            ItemStack c = be.getContents();
            if (c.isEmpty()) continue;
            if (proto.isEmpty()) proto = c;
            totalCount += c.getCount();
        }
        if (proto.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = proto.copy();
        out.setCount(totalCount);
        return out;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack out = ItemStack.EMPTY;
        // Withdraw from members in reverse order until amount satisfied.
        for (int i = members.size() - 1; i >= 0 && amount > 0; i--) {
            HayFeederBlockEntity be = members.get(i);
            ItemStack c = be.getContents();
            if (c.isEmpty()) continue;
            int take = Math.min(amount, c.getCount());
            ItemStack taken = be.removeItem(0, take);
            if (out.isEmpty()) out = taken;
            else out.grow(taken.getCount());
            amount -= take;
        }
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack out = getItem(0);
        for (HayFeederBlockEntity be : members) be.removeItemNoUpdate(0);
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        // Distribute across members in BFS order; cap each at CAPACITY.
        int remaining = stack.getCount();
        for (HayFeederBlockEntity be : members) {
            if (remaining <= 0) {
                be.setItem(0, ItemStack.EMPTY);
                continue;
            }
            int put = Math.min(remaining, HayFeederBlockEntity.CAPACITY);
            ItemStack chunk = stack.copyWithCount(put);
            be.setItem(0, chunk);
            remaining -= put;
        }
    }

    @Override
    public int getMaxStackSize() {
        return members.size() * HayFeederBlockEntity.CAPACITY;
    }

    @Override
    public boolean stillValid(Player player) {
        return !members.isEmpty() && members.get(0).stillValid(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot != 0) return false;
        if (!AcceptedFoods.isAccepted(stack)) return false;
        // Must match any non-empty member's type.
        for (HayFeederBlockEntity be : members) {
            ItemStack c = be.getContents();
            if (!c.isEmpty() && !c.is(stack.getItem())) return false;
        }
        return true;
    }

    @Override
    public void clearContent() {
        for (HayFeederBlockEntity be : members) be.clearContent();
    }
}
```

### Unit 6: `HayFeederBlock.useItemOn` opens menu against the group

```java
@Override
protected InteractionResult useItemOn(...) {
    if (level.isClientSide()) return InteractionResult.SUCCESS;
    List<HayFeederBlockEntity> group = FeederGroup.findMembers(level, pos, this);
    if (group.isEmpty()) return InteractionResult.PASS;
    GroupContainer container = new GroupContainer(group);
    player.openMenu(new SimpleMenuProvider(
        (id, inv, p) -> new HayFeederMenu(id, inv, container),
        Component.translatable("container.hay_feeder.hay_feeder")), pos);
    return InteractionResult.SUCCESS_SERVER;
}
```

`useWithoutItem` mirrors. The BE's `MenuProvider.createMenu` (currently still references `this` as Container) is bypassed in favor of the SimpleMenuProvider built around the group container.

### Unit 7: `HayFeederMenu` slot honors group capacity

The `HayFeederFoodSlot` currently returns `HayFeederBlockEntity.CAPACITY` (= 64) as max stack size. Change to read from the underlying container's `getMaxStackSize()`:

```java
@Override
public int getMaxStackSize() { return container.getMaxStackSize(); }
```

This makes single-feeder cases stay at 64, while group cases scale to N × 64.

The `mayPlace` check delegates to `container.canPlaceItem(0, stack)` (existing) — already group-aware via the GroupContainer.

## Resource files updated

- `assets/hay_feeder/blockstates/hay_feeder.json` — replaced with multipart
- `assets/hay_feeder/models/block/` — old 3 stage files deleted; 8 new model files added
- `assets/hay_feeder/models/item/hay_feeder.json` — parent points at the new bundled inventory model

## Out of scope

- Vertical stacking
- Group-level feeding (each block still ticks independently from its own contents — the user is happy with this from earlier decision)
- Animated visual transitions when blocks connect/disconnect
- Performance optimization: BFS could grow if user places hundreds of feeders; for now we accept O(N) per interaction

## MC 26.1 API drift candidates

- `Block.getStateForPlacement(BlockPlaceContext)` — should still exist; verify `BlockPlaceContext.getLevel()` returns the right type for our `isFeeder` helper
- `Block.updateShape(...)` — signature varies across versions; in MC 26.1 it now accepts `LevelReader`, `ScheduledTickAccess`, `BlockPos`, `Direction`, `BlockPos`, `BlockState`, `RandomSource` (verify via decompiled `FenceBlock.java`)
- `BooleanProperty.create(name)` — stable
- `SimpleMenuProvider(MenuConstructor, Component)` — stable, but verify `player.openMenu(MenuProvider, BlockPos)` overload exists or use `player.openMenu(MenuProvider)` without pos
- `Container.canPlaceItem` is the slot-level constraint; vanilla `AbstractContainerMenu.Slot.mayPlace` typically forwards. Verify our `HayFeederFoodSlot.mayPlace` delegates correctly

## References

- v3 GUI design: [`2026-05-10-hay-feeder-gui-design.md`](2026-05-10-hay-feeder-gui-design.md)
- Vanilla parallel for federated storage: `CompoundContainer` (used by `ChestBlock` for double chests)
- Vanilla parallel for connected blocks: `FenceBlock`, `GlassPaneBlock` (multipart blockstate + N/S/E/W BooleanProperty + getStateForPlacement + updateShape)
