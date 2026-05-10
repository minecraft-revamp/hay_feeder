"""Resource validator for Hay Feeder — Level 1 of the test pyramid.

Runs purely-Python checks across both loader trees (neoforge/, fabric/).
Designed to fail fast and produce a clear summary so a non-Java reader can
see exactly what's wrong.

Checks
------
L1.1  All JSON files under src/main/resources/ parse cleanly
L1.2  Language files have consistent keys (vs en_us)
L1.3  pack.mcmeta has the MC 26.1 format (min_format[2], max_format)
L1.4  Model JSONs reference existing texture / parent files (mod refs only —
      vanilla `minecraft:` refs are skipped, not failed)
L1.5  Recipes have valid shape / keys / result

Exit code 0 = all pass, 1 = any fail.

Usage:
    python3 tests/validate.py [<loader_root>]

Without args, walks both `neoforge/` and `fabric/` from the project root.
With one arg, validates only that loader directory (used by Gradle so each
build.gradle scopes the check to its own module).
"""
from __future__ import annotations
import json
import sys
from pathlib import Path
from typing import List

# Allow running directly via `python3 tests/validate.py` from anywhere
sys.path.insert(0, str(Path(__file__).parent.parent))

ROOT = Path(__file__).parent.parent

failures: List[str] = []
warnings: List[str] = []


def fail(msg: str) -> None:
    failures.append(msg)
    print(f"  FAIL  {msg}")


def warn(msg: str) -> None:
    warnings.append(msg)
    print(f"  WARN  {msg}")


# ----- L1.1: JSON well-formed ------------------------------------------------

def check_json_parses(loader_root: Path) -> None:
    print(f"  L1.1  JSON files parse cleanly")
    res_root = loader_root / "src/main/resources"
    if not res_root.exists():
        return
    for json_path in res_root.rglob("*.json"):
        try:
            json.loads(json_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            fail(f"invalid JSON: {json_path.relative_to(loader_root)} — {e}")
    for mcmeta in res_root.rglob("*.mcmeta"):
        try:
            json.loads(mcmeta.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            fail(f"invalid mcmeta: {mcmeta.relative_to(loader_root)} — {e}")


# ----- L1.2: lang key consistency -------------------------------------------

def check_lang_consistency(loader_root: Path) -> None:
    print(f"  L1.2  Language files key consistency")
    lang_dir = loader_root / "src/main/resources/assets/hay_feeder/lang"
    en_us_path = lang_dir / "en_us.json"
    if not en_us_path.exists():
        fail(f"missing {en_us_path.relative_to(loader_root)}")
        return
    expected = set(json.loads(en_us_path.read_text(encoding="utf-8")).keys())
    for lang_file in sorted(lang_dir.glob("*.json")):
        if lang_file.name == "en_us.json":
            continue
        keys = set(json.loads(lang_file.read_text(encoding="utf-8")).keys())
        unknown = keys - expected
        missing = expected - keys
        if unknown:
            fail(f"{lang_file.name} has unknown keys: {sorted(unknown)}")
        if missing:
            warn(f"{lang_file.name} missing keys (will fall back to en_us): {sorted(missing)}")


# ----- L1.3: pack.mcmeta format ---------------------------------------------

def check_pack_mcmeta(loader_root: Path) -> None:
    print(f"  L1.3  pack.mcmeta MC 26.1 format")
    mcmeta = loader_root / "src/main/resources/pack.mcmeta"
    if not mcmeta.exists():
        fail("missing pack.mcmeta")
        return
    pack = json.loads(mcmeta.read_text(encoding="utf-8")).get("pack", {})
    mn = pack.get("min_format")
    mx = pack.get("max_format")
    if not (isinstance(mn, list) and len(mn) == 2 and all(isinstance(x, int) for x in mn)):
        fail(f"pack.mcmeta: min_format must be [int, int], got {mn!r}")
    if not isinstance(mx, int):
        fail(f"pack.mcmeta: max_format must be int, got {mx!r}")
    if "pack_format" in pack:
        fail("pack.mcmeta: legacy 'pack_format' key present — MC 26.1 requires min_format/max_format only")


# ----- L1.4: model → texture references --------------------------------------

def _collect_mod_refs(node, out: list) -> None:
    """Recursively collect every string value beginning with `hay_feeder:`."""
    if isinstance(node, dict):
        for v in node.values():
            _collect_mod_refs(v, out)
    elif isinstance(node, list):
        for v in node:
            _collect_mod_refs(v, out)
    elif isinstance(node, str):
        if node.startswith("hay_feeder:"):
            out.append(node)


def check_model_refs(loader_root: Path) -> None:
    print(f"  L1.4  Model JSONs reference existing textures / parents")
    assets_root = loader_root / "src/main/resources/assets/hay_feeder"
    models_root = assets_root / "models"
    textures_root = assets_root / "textures"
    if not models_root.exists():
        return
    for model in models_root.rglob("*.json"):
        try:
            data = json.loads(model.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue  # already flagged in L1.1
        rel = model.relative_to(loader_root)
        refs: list = []
        # `parent` is a top-level scalar; texture refs live under `textures`.
        # Walking the whole tree catches both plus any future namespaced
        # references (e.g. animation controllers) without bespoke per-key code.
        _collect_mod_refs(data, refs)
        for ref in refs:
            # `hay_feeder:block/foo` -> models/block/foo.json
            # `hay_feeder:item/foo`  -> models/item/foo.json
            # `hay_feeder:gui/foo`   -> textures/gui/foo.png
            # `hay_feeder:block/<tex>` referenced from a model's textures map
            # also resolves to a PNG under textures/, but hay_feeder ships no
            # block/item textures — so we resolve to a model first, then to a
            # texture file, and only fail if both miss.
            _, path = ref.split(":", 1)
            model_target = models_root / f"{path}.json"
            texture_target = textures_root / f"{path}.png"
            if not model_target.exists() and not texture_target.exists():
                fail(f"{rel}: reference '{ref}' resolves to neither {model_target.relative_to(loader_root)} "
                     f"nor {texture_target.relative_to(loader_root)}")


# ----- L1.5: recipe shape ----------------------------------------------------

def check_recipes(loader_root: Path) -> None:
    print(f"  L1.5  Recipe shape / keys / result")
    recipe_dir = loader_root / "src/main/resources/data"
    if not recipe_dir.exists():
        return
    for recipe in recipe_dir.rglob("recipe/*.json"):
        try:
            data = json.loads(recipe.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue  # already flagged in L1.1
        rel = recipe.relative_to(loader_root)
        if "type" not in data:
            fail(f"{rel}: missing 'type'")
            continue
        if data["type"] == "minecraft:crafting_shaped":
            pattern = data.get("pattern", [])
            keys = data.get("key", {})
            if not pattern or len(pattern) > 3 or any(len(row) > 3 for row in pattern):
                fail(f"{rel}: pattern out of bounds (max 3x3)")
            used = set(c for row in pattern for c in row if c != " ")
            unknown_keys = used - set(keys.keys())
            if unknown_keys:
                fail(f"{rel}: pattern uses keys not in 'key' map: {sorted(unknown_keys)}")
            unused_keys = set(keys.keys()) - used
            if unused_keys:
                warn(f"{rel}: 'key' has unused entries: {sorted(unused_keys)}")
        if "result" not in data:
            fail(f"{rel}: missing 'result'")
        elif "id" not in data["result"]:
            fail(f"{rel}: result missing 'id'")


# ----- driver ----------------------------------------------------------------

CHECKS = [check_json_parses, check_lang_consistency, check_pack_mcmeta,
          check_model_refs, check_recipes]


def main(argv: List[str]) -> int:
    targets: List[Path]
    if len(argv) > 1:
        targets = [Path(argv[1]).resolve()]
    else:
        targets = [ROOT / "neoforge", ROOT / "fabric"]

    for loader_root in targets:
        if not loader_root.exists():
            print(f"=== skip: {loader_root} not found ===")
            continue
        print(f"\n=== {loader_root.name} ===")
        for check in CHECKS:
            check(loader_root)

    print()
    if failures:
        print(f"❌ {len(failures)} failure(s), {len(warnings)} warning(s)")
        return 1
    if warnings:
        print(f"✓ All checks passed ({len(warnings)} warning(s))")
    else:
        print("✓ All checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
