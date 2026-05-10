# Hay Feeder

A wood-and-iron feeding trough that feeds nearby wheat-eaters, carrot-pigs, seed-chickens and more — passively, on its own schedule. Place it, fill it once, walk away.

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-62B132?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2.41--beta-D7742F)](https://neoforged.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.148.0%2B26.1.2-DBD0B4)](https://fabricmc.net/)

> ⚠️ Targets **Minecraft 26.1.2**, the first post-deobfuscation snapshot. Won't load on earlier versions.

## Status

- **NeoForge:** feature-complete on `main` (block, GUI, feeding logic, redstone output, custom rendering).
- **Fabric:** scaffold only — JAR builds and loads, but the feature logic still has to be ported. See the roadmap.

## What it does

Drop the feeder, open the GUI, fill its slot with any animal-friendly food, and any compatible animal that wanders within ~6 blocks gets fed automatically: heals, breeds, ages up babies — the same reaction as if you'd hand-fed them. Each "feed" consumes one item from the trough; once the slot is empty, animals ignore it.

A single feeder is a 1×1 block. **Connected feeders share inventory** — place several side by side and they merge into one wide trough with one slot per cell, openable from any of them. Useful for big herds without standing there clicking 64 carrots.

Visually, the contents pile in the trough scales **continuously** with the actual item count, tinted by the food type — orange for carrots, red for berries, pink for salmon, untinted yellow for plain hay. Fills smoothly as you add more, drops as animals eat.

The block also **emits a redstone signal proportional to its fill** — both directly (adjacent dust/lamps light up) and via a comparator. Empty → 0, any content → 1, full → 15.

## The block

|  |  |
|---|---|
| **Placement** | One per world cell. Place adjacent feeders to chain them into a shared trough. |
| **Walls** | Composter-style wood for the four sides. |
| **Base** | Iron-block band around the bottom 2 px of the perimeter. |
| **Corner posts** | Iron-bars columns at each corner with the central post + 3 horizontal fittings, like vanilla iron bars. |
| **Connected look** | Walls and corner posts hide between adjacent feeders so chained feeders read as one continuous trough; an L-shape closes its corner with a diagonal-aware iron post. |
| **Contents** | Rendered live by a block-entity renderer — height = `ceil(count × 12 / 64)` pixels, tinted per food. |
| **Sound type** | Wood (matches the dominant material — same as composter). |

When broken, the feeder drops itself as an item plus whatever food was inside.

## Feedable foods & who eats them

The slot only accepts items from this whitelist (no planks, no random junk). Each food still uses vanilla `Animal#isFood`, so an animal will only actually eat what its species cares about — a chicken won't touch the carrots, a cow won't touch the seeds. Mix-and-match feeders (a row of 3, one full of wheat, one full of seeds, one full of carrots) cover a mixed farm.

| Slot accepts | Eaten by |
|---|---|
| 🌾 Wheat | Cow, Sheep, Mooshroom, Goat, Horse, Donkey, Mule, Llama |
| 🥕 Carrot | Pig, Rabbit (also wolves indirectly via golden carrot, etc.) |
| 🥔 Potato | Pig |
| 🟥 Beetroot | Pig |
| 🌱 Wheat / Melon / Pumpkin / Beetroot seeds | Chicken, Parrot |
| 🐟 Cod / Salmon / Tropical fish | Cat, Ocelot, Dolphin, Axolotl |
| 🍓 Sweet berries · 🌟 Glow berries | Fox |
| 🍎 Apple | Horse |

(Vanilla coverage — refer to the wiki if Mojang adds new species. The whitelist is `AcceptedFoods.java` if you want to extend it.)

## Connected feeders & the GUI

Right-click any feeder in a connected group to open the shared menu. The menu has **N food slots** (one per feeder in the group) lined up at the top, with the player inventory below. Slots accept only foods from the whitelist; mismatched items are rejected client-side instantly.

Each slot caps at 64 items. Adding to a slot:

- with the slot **empty** → first item lands, full hay-bale ish pile starts at 1 px
- with the slot **non-empty** → tops up; pile height grows live in the world
- a fresh `composter_fill_success` blip and item particles fire on **every** insert (not just empty → non-empty)

Pulling items back out (shift-click) shrinks the pile in real time too.

## Auto-feeding mechanic

Each feeder ticks roughly once per random tick — the same cadence as crops growing. On each tick:

1. Find every alive `Animal` within a 6-block cube around the feeder.
2. Filter to those for which `animal.isFood(stackInSlot)` is true.
3. For each match, age up babies and trigger breeding hearts on adults.
4. Consume **one** item from the slot, emit happy-villager particles + a soft `grass_break` blip.

So a full 64-item slot feeds 64 individual events. With one feeder per food type and a mixed herd, expect every animal that cares about a food to slowly cycle through.

> Animals don't have to be touching the block — the radius is generous enough to cover a small pen. They also don't queue on it; one feeder can feed a whole herd over time.

## Redstone

The feeder behaves like a power source whose strength = fill ratio:

- Adjacent **redstone dust, lamps, doors, repeaters** light up directly with strength **0..15** (0 when empty, 1 at any content, 15 at full).
- A **comparator** placed against any face reads the same 0..15 (vanilla `getRedstoneSignalFromContainer` curve — same as a chest).
- The signal updates **immediately** on insert / animal-feed / extract — no waiting for a tick.

Useful patterns: a lamp that lights up when a pen's feeder is empty (invert with a torch), a hopper line that auto-tops feeders below a threshold, a dispenser that fires a particle effect at full.

## Recipe

> **Legend** · 🪵 Wooden slab · ⬜ Iron ingot

|  |  |  |
|:-:|:-:|:-:|
| 🪵 |    | 🪵 |
| 🪵 |    | 🪵 |
| ⬜ | 🪵 | ⬜ |

Shaped: **5 wooden slabs + 2 iron ingots**. Any wooden slab works (`#minecraft:wooden_slabs` tag). The slabs build the trough (walls + base); the two iron ingots at the bottom corners materialise as the iron corner posts on the placed block. The feeder is empty when crafted — fill it with the food of your choice in-game.

## Install

1. Install the launcher of your choice (recommended: [Prism Launcher](https://prismlauncher.org/))
2. Create a Minecraft **26.1.2** instance with **NeoForge** `26.1.2.41-beta` (Fabric port still in progress).
3. Drop the JAR from [releases](../../releases) into the instance's `mods/` folder:
   - `hay_feeder-0.1.0.jar` for NeoForge
   - `hay_feeder-fabric-0.1.0.jar` for Fabric *(coming)*

## Build from source

Two self-contained Gradle projects, one per loader.

```bash
# NeoForge — needs Java 21 (auto-fetches Java 25 toolchain)
cd neoforge
JAVA_HOME=$HOME/.local/jdks/current ./gradlew build
# → neoforge/build/libs/hay_feeder-0.1.0.jar

# Fabric — needs Java 25 (Loom is strict)
cd fabric
JAVA_HOME=$HOME/.local/jdks/current25 ./gradlew build
# → fabric/build/libs/hay_feeder-fabric-0.1.0.jar
```

`./gradlew runClient` from either subdir to launch a dev client.

## Repository layout

```
hay_feeder/
├── neoforge/    NeoGradle 7 project, NeoForge 26.1.2.41-beta — feature-complete
├── fabric/      Loom 1.16.1 project, Fabric 0.148.0+26.1.2 — scaffold
├── CLAUDE.md    Per-mod design notes and iteration log
└── README.md    You are here
```

Two-loader-side-by-side, no Architectury — same convention as the rest of the [Minecraft Revamp collective](../README.md). See [`../buckets_update/CLAUDE.md`](../buckets_update/CLAUDE.md) for cross-loader API differences and MC 26.1 post-deobfuscation gotchas.

## Roadmap

- **Fabric port** — mirror the NeoForge implementation; key adjustments are container/menu plumbing and the BlockEntityRenderer registration path.
- **i18n** — only `en_us` is in the tree. Mirror the 30-language coverage that `buckets_update` ships with.
- **Group-aware redstone** *(maybe)* — currently each member feeder reports its own slot. A connected group could report total fill across all members; nice for hopper-driven auto-top-ups feeding a whole row.
- **CI** — add the build-and-test workflow that `buckets_update` already has.

## License

All rights reserved by the author. Reach out before forking or redistributing.

---

Made by [@JessicaMalle](https://github.com/JessicaMalle) with assistance from Claude. Part of the [Minecraft Revamp](../README.md) collective.
