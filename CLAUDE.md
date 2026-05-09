# Hay Feeder — repo guide for AI iteration

**Local path:** `~/Dev/mods/minecraft-revamp/hay_feeder/` (sibling of `buckets_update/` and other mods in the [Minecraft Revamp collective](../CLAUDE.md)).

**Status: scaffold + Q2 wiring.** Two-loader project tree is in place, the `hay_feeder` block is registered with a `feeds_left` blockstate (0–8), creative tab carries it, and placeholder visuals inherit `minecraft:block/hay_block`. **No feeding logic yet** — Q1 (target entities) and Q3 (tick cadence) still need brainstorming before the mod actually does anything.

> Most cross-cutting context (MC 26.1.2 post-deobfuscation gotchas, NeoForge patches absent in vanilla Fabric, build/run command shapes, user environment) lives in [`../buckets_update/CLAUDE.md`](../buckets_update/CLAUDE.md) and [`../CLAUDE.md`](../CLAUDE.md). This file only covers what's specific to hay_feeder — read those first.

## Vision

Vanilla wheat-eating animals can already be fed manually with wheat (right-click). That doesn't scale to a herd. **Hay Feeder turns the vanilla `hay_block` into a passive auto-feeder**: place a bale near wheat-eating animals and they periodically feed from it; the bale depletes over time.

This mod must satisfy the collective's vision principles ([`../CLAUDE.md`](../CLAUDE.md)):

- **Vanilla-first feel.** Reuse `hay_block`, reuse the wheat-feed effect, reuse vanilla particles. No new items, no new textures.
- **One missing piece.** Auto-feeding is the only mechanic. Don't expand to "auto-shearing", "auto-milking", etc. — those are separate mods.
- **No HUD spam.** Nothing on screen. Place block, walk away.
- **Two-loader parity.** Both NeoForge and Fabric.

## Open design questions

**Resolve these via `superpowers:brainstorming` before writing implementation code.** They shape the mod meaningfully.

### Q1 — Which entities count as "wheat-eating"?

Vanilla wheat-feedable mobs:
- `minecraft:cow` ✅ obvious
- `minecraft:sheep` ✅ obvious
- `minecraft:horse` ✅ obvious
- `minecraft:donkey` / `minecraft:mule` — eat wheat, breed with golden carrot. Include?
- `minecraft:goat` — eats wheat, breeds with wheat. Include?
- `minecraft:llama` — eats hay (whole bale!), breeds with hay. Already eats `hay_block` directly via vanilla. **Special-cased: skip — vanilla handles it.**

**Recommendation:** target the union {cow, sheep, horse, donkey, mule, goat}. Detect via a tag (`#hay_feeder:wheat_eaters`) so it's data-driven and other mods/datapacks can extend.

### Q2 — Bale depletion model ✅ DECIDED

**Locked: Block-state counter (Model 1).** A custom block `hay_feeder:hay_feeder` with an `IntegerProperty` `feeds_left` ranging 0–8. Each feed event decrements the counter; reaching 0 either breaks the block or leaves a "depleted" visual stage. The blockstate is persistent, free (no block entity tick cost), and idiomatic — it's the same pattern vanilla uses for `cake`, `composter`, `farmland` moisture, etc.

The author will provide custom textures per stage; placeholder visuals inherit `minecraft:block/hay_block` so all 9 stages currently render identically.

### Q3 — Detection radius and tick cadence

- Radius: 4 blocks? 6? 8? Bigger = fewer bales per herd, smaller = more "feeding station" feel.
- Cadence: every N seconds? Per-animal, or per-bale? Random-tick (vanilla idiom for slow processes — runs ~once/68s per random-tick-eligible block)?

**Recommendation:** start with random-tick on the bale + 6-block radius. Random-tick is the canonical vanilla pattern for slow passive processes (crops growing, fire spreading, leaves decaying) and stays performant at scale.

### Q4 — Eating animation / particle feedback

Right-click feeding plays the eating particles + heart particles. Auto-feeding should at minimum play heart particles when an animal feeds (so the player notices). Whether the animal turns its head / lowers it / plays the eating animation is more involved — TBD.

## Module layout (planned)

Current layout — Q2 wiring in place, feeding logic still TBD:

```
neoforge/src/main/java/com/hayfeeder/
├── HayFeeder.java                       ← entry point: registers blocks/items/tabs
├── client/HayFeederClient.java
├── feature/feeder/HayFeederBlock.java   ← Block extends Block, FEEDS_LEFT 0-8
└── registry/
    ├── ModBlocks.java          ← DeferredRegister.Blocks, registers HAY_FEEDER
    ├── ModItems.java           ← BlockItem registration
    └── ModCreativeTabs.java

fabric/src/main/java/com/hayfeeder/fabric/
├── HayFeederFabric.java        ← entry, calls Mod*.bootstrap()
├── client/HayFeederFabricClient.java
├── HayFeederBlock.java         ← same content as NeoForge, package-flat per Fabric convention
├── ModBlocks.java              ← static init via Registry.register + Properties.setId
├── ModItems.java
└── ModCreativeTabs.java
```

Resources (mirrored both loaders, `assets/hay_feeder/`):
- `blockstates/hay_feeder.json` — 9 variants of `feeds_left`, all currently mapped to the same model (placeholder)
- `models/block/hay_feeder.json` — `parent: minecraft:block/hay_block` (visual placeholder until per-stage textures land)
- `models/item/hay_feeder.json` — `parent: hay_feeder:block/hay_feeder`
- `items/hay_feeder.json` — MC 26.1 item-definition pointing at the item model
- `lang/en_us.json` — `itemGroup.hay_feeder.main` + `block.hay_feeder.hay_feeder`

When real per-stage textures are ready: split `models/block/hay_feeder.json` into 9 model files (or fewer if some stages share a texture) and update `blockstates/hay_feeder.json` to point each `feeds_left=N` variant at the appropriate model.

To add (when Q1 + Q3 are decided):
- `feature/feeder/FeedingTick.java` — random-tick handler that picks nearby wheat-eaters and decrements `feeds_left`
- `data/hay_feeder/tags/entity_type/wheat_eaters.json` — tag-driven targeting (if Q1 → tag-driven)

## Build & run

Same shape as the rest of the collective:

| Command | Where | Java |
|---|---|---|
| `./gradlew build` | `neoforge/` | **21** |
| `./gradlew runClient` | `neoforge/` | **21** |
| `./gradlew build` | `fabric/` | **25** |
| `./gradlew runClient` | `fabric/` | **25** |

JDK toolchains: `~/.local/jdks/current` (21) and `~/.local/jdks/current25` (25).

JAR outputs:
- `neoforge/build/libs/hay_feeder-0.1.0.jar`
- `fabric/build/libs/hay_feeder-fabric-0.1.0.jar`

## Tooling note

`buckets_update/` ships a Python resource validator (`tests/validate.py`) wired into `./gradlew check`. That script is hardcoded to the `buckets_update` namespace — it was **not** vendored into hay_feeder. The `validateResources` Gradle task slot is reserved in `neoforge/build.gradle` for when a mod-agnostic linter exists. Until then, `./gradlew check` runs JUnit tests only.

## Iteration pointers

- **Before any non-trivial code:** invoke `superpowers:brainstorming` on the open design questions above. Don't lock decisions in by writing code first.
- **Implement on NeoForge first.** Richer event API, easier to prototype. Port to Fabric once the design has stabilised.
- **Use vanilla wheat-feed code as reference.** `Animal.isFood(ItemStack)` and the eating logic in `TemptGoal` / `BreedGoal` are the vanilla equivalents — don't reimplement, look them up via `./gradlew neoFormDecompile` in `neoforge/`.
- **First non-scaffold commit lands on a feature branch**, not `main`. The collective convention is "scaffold on main, logic on feature branch".
