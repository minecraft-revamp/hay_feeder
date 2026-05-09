# Hay Feeder

Drop a hay bale near wheat-eating animals — cows, sheep, horses — and they auto-feed from it. The bale depletes over time. No HUD, no menu: place block, walk away, animals fatten.

> ⚠️ Targets **Minecraft 26.1.2** on **NeoForge** and **Fabric**. Currently scaffolded only — no feature logic yet.

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-62B132?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2.41--beta-D7742F)](https://neoforged.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.148.0%2B26.1.2-DBD0B4)](https://fabricmc.net/)

## Status

**Scaffold.** The two-loader project structure is in place, the mod loads without crashing, but it doesn't *do* anything yet. See [`CLAUDE.md`](./CLAUDE.md) for the design intent and open questions to resolve before implementation.

## Why this mod

Vanilla wheat-eating animals can be fed manually with wheat (right-click). That's fine for two animals — tedious for a herd. The vanilla `hay_block` already exists as a 9× wheat compaction; making it a passive feeder for nearby animals is the obvious mechanical extension.

## Planned behaviour

- **Targets:** `minecraft:cow`, `minecraft:sheep`, `minecraft:horse` (and possibly `donkey`, `mule`, `goat` — TBD).
- **Detection:** any wheat-eater within a small radius (TBD: 4-6 blocks?) of a placed `hay_block` will periodically feed from it.
- **Effect on animals:** identical to manual wheat feeding — heals, enables breeding, ages up babies.
- **Bale depletion:** TBD — durability-based block state (e.g. 9 charges = 9 wheat-equivalent) or feed-count counter via block entity.
- **No new items.** This mod adds *behaviour* to an existing vanilla block.

## Build from source

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
├── neoforge/    NeoGradle 7 project, NeoForge 26.1.2.41-beta
├── fabric/      Loom 1.16.1 project, Fabric 0.148.0+26.1.2
├── CLAUDE.md    Per-mod design notes and open questions
└── README.md    You are here
```

Two-loader-side-by-side, no Architectury — same convention as the rest of the [Minecraft Revamp collective](../README.md). See [`../buckets_update/CLAUDE.md`](../buckets_update/CLAUDE.md) for cross-loader API differences and MC 26.1 post-deobfuscation gotchas.

## License

All rights reserved by the author. Reach out before forking or redistributing.

---

Made by [@JessicaMalle](https://github.com/JessicaMalle) with assistance from Claude. Part of the [Minecraft Revamp](../README.md) collective.
