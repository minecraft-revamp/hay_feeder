# Hay Feeder — repo guide for AI iteration

**Local path:** `<repo>/` (sibling of `buckets_update/` and other mods in the [Minecraft Revamp collective](../CLAUDE.md)).

**Status: feature-complete on both loaders, targeting Minecraft 26.3.** The `hay_feeder` block is a connected, block-entity-backed trough: adjacent feeders share one inventory (one 64-item slot per cell), the stored food is rendered live by a block-entity renderer and tinted per food type, and the block emits redstone proportional to fill (comparator and direct signal). Feeding runs in two halves — `FollowFeederGoal`, injected into every `Animal`, walks animals to the trough, and `HayFeederBlockEntity#tickFeeding` on the block's random tick consumes exactly one item per animal that arrives. A data-driven `hay_feeder:rancher` villager profession (PoI + five trade levels) makes the feeder a workstation. Fabric mirrors the NeoForge feature set; 30 language files ship on both loaders.

> Most cross-cutting context (MC 26.3 migration notes, NeoForge patches absent in vanilla Fabric, build/run command shapes, user environment) lives in [`../buckets_update/CLAUDE.md`](../buckets_update/CLAUDE.md) and [`../CLAUDE.md`](../CLAUDE.md). This file only covers what's specific to hay_feeder — read those first.

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
- `items/hay_feeder.json` — MC 26.x item-definition pointing at the item model
- `lang/en_us.json` — `itemGroup.hay_feeder.main` + `block.hay_feeder.hay_feeder`

When real per-stage textures are ready: split `models/block/hay_feeder.json` into 9 model files (or fewer if some stages share a texture) and update `blockstates/hay_feeder.json` to point each `feeds_left=N` variant at the appropriate model.

To add (when Q1 + Q3 are decided):
- `feature/feeder/FeedingTick.java` — random-tick handler that picks nearby wheat-eaters and decrements `feeds_left`
- `data/hay_feeder/tags/entity_type/wheat_eaters.json` — tag-driven targeting (if Q1 → tag-driven)

## Build & run

Same shape as the rest of the collective:

| Command | Where | Java |
|---|---|---|
| `gradlew.bat build` (Windows shell) | `neoforge/` | **21** (NeoGradle auto-fetches the 25 toolchain) |
| `gradlew.bat runClient` (Windows shell) | `neoforge/` | **21** |
| `gradlew.bat build` (Windows shell) | `fabric/` | **25** (Loom is strict) |
| `gradlew.bat runClient` (Windows shell) | `fabric/` | **25** |

**Run both loaders natively on Windows, not from WSL.** The repo lives under `/mnt/c`; WSL2's 9p bridge makes NeoGradle's tens of thousands of small file operations pathologically slow (an identical build took ~1h49 under WSL vs <6 min as a native Windows process). From WSL, invoke the Windows shell explicitly:

```bash
cmd.exe /c "cd /d <path-to-checkout>\\neoforge && gradlew.bat --no-daemon check build"
```

JDK toolchains (Linux side): `~/.local/jdks/current` (21) and `~/.local/jdks/current25` (25); the Windows side runs Temurin 25 from `JAVA_HOME`.

JAR outputs:
- `neoforge/build/libs/hay_feeder-0.1.1+mc26.3.jar`
- `fabric/build/libs/hay_feeder-fabric-0.1.1+mc26.3.jar`

## MC 26.3 migration notes

Target is **Minecraft 26.3** on both loaders. The port needed **exactly one Java change**: `Items.WHITE_WOOL` no longer exists in 26.3 (the 16 wool items are grouped as `Items.WOOL`, a `ColorCollection<Item>`) — replaced with `Items.WOOL.white()` in `RancherTrades.java` (both loaders). Every other vanilla / NeoForge / Fabric API symbol this mod touches (`Block#useItemOn`/`useWithoutItem`/`updateShape`, `BlockEntity#loadAdditional`/`saveAdditional`, the `SubmitNodeCollector` / `BlockEntityRenderState` / `SpriteGetter` render path, `LightCoordsUtil#pack`, `DeferredRegister`, `GameData#getBlockStatePointOfInterestTypeMap`, `RegisterMenuScreensEvent`, `ServerEntityEvents.ENTITY_LOAD`, …) is signature-identical between 26.1.2 and 26.3. What changed is the toolchain, the pack format, and one data-driven schema:

| Component | Was (26.1.2) | Now (26.3) |
|---|---|---|
| Minecraft | 26.1.2 | 26.3 |
| NeoForge | 26.1.2.41-beta | 26.3.0.4-beta |
| NeoGradle (`net.neoforged.gradle.userdev`) | 7.1.26 | **7.1.39** — required, not optional: 7.1.38 fails to build against 26.3 (stale bundled access transformer on `HolderSet$1.contents()`) |
| Fabric Loader | 0.18.4 | 0.19.5 |
| Fabric API | 0.148.0+26.1.2 | 0.161.0+26.3 |
| Fabric Loom | 1.16.1 | 1.17.11 — the 26.3 porting guide requires ≥1.17 |
| Fabric Gradle wrapper | 9.4.0 | 9.6.0 (the NeoForge wrapper stays 9.2.1) |
| data pack format | `min_format [101,1]` / `max_format 101` | `[121,0]` / `121` — read from the real `version.json` inside the 26.3 `minecraft-client.jar`; the resource pack format is separately numbered (97.1) and this repo's `pack.mcmeta` has always tracked the data format |

### 26.3 data-driven gotcha: villager trade number providers

In 26.3, `VillagerTrade`'s `max_uses`/`xp`, `TradeCost`'s `count` and `TradeSet`'s `amount` changed from `NumberProvider` to `Holder<ContextIntProvider>` (and `reputation_discount` to `Holder<ContextFloatProvider>`). Vanilla datagen now emits **ints** (`2`, `12`) where 26.1.2 emitted floats (`2.0`, `12.0`). Our `villager_trade` / `trade_set` JSONs were normalised to the 26.3 int shape (all keys otherwise unchanged) and `tests/validate.py` **L1.6** now enforces it. `reputation_discount` stays a float (e.g. `0.05`).

### 26.3 registry gotcha: colored items became `ColorCollection`s

`Items.WHITE_WOOL` (and the other 15 wool constants) are gone. 26.3 groups them as `Items.WOOL`, a `net.minecraft.world.level.block.ColorCollection<Item>` with per-colour accessors — `Items.WOOL.white()`, or `Items.WOOL.pick(DyeColor.WHITE)`. Same shape as `Blocks.WOOL` / `WOOL_STAIRS` / `WOOL_SLAB` and the copper weathering collections. Registry **IDs** are unchanged (`minecraft:white_wool`), so the datapack JSON needed no edit — only the Java constant.

> The 26.3 `recipe_crafted` advancement trigger rename (`recipe_id` → `recipes`) documented in `../buckets_update/CLAUDE.md` does **not** apply here: hay_feeder ships no advancements.

## Tooling note

`tests/validate.py` is this repo's own resource validator, wired onto `./gradlew check` by the `validateResources` task in **both** `fabric/build.gradle` and `neoforge/build.gradle` (each passes its own loader root, so the two trees are linted separately). It runs in <1s and covers: JSON well-formedness, lang-key consistency against `en_us`, `pack.mcmeta` shape, model → texture/parent references, recipe shape, and the MC 26.3 villager-trade numeric shape (**L1.6**). The module docstring lists every check.

## Iteration pointers

- **Before any non-trivial code:** invoke `superpowers:brainstorming` on the open design questions above. Don't lock decisions in by writing code first.
- **Implement on NeoForge first.** Richer event API, easier to prototype. Port to Fabric once the design has stabilised.
- **Use vanilla wheat-feed code as reference.** `Animal.isFood(ItemStack)` and the eating logic in `TemptGoal` / `BreedGoal` are the vanilla equivalents — don't reimplement, look them up via `./gradlew neoFormDecompile` in `neoforge/`.
- **First non-scaffold commit lands on a feature branch**, not `main`. The collective convention is "scaffold on main, logic on feature branch".
