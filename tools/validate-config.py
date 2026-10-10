#!/usr/bin/env python3
"""Offline check of NexusRime's config.yml against what RimeConfig actually reads.

  python3 tools/validate-config.py [path/to/config.yml]

Fails if config.yml is missing a key the code reads (so a typo can never silently fall back to a
default), has a key the code never reads, has a value that differs from the built-in default of the
same setting (so the shipped file and the code agree), or names a material that does not exist.
Optional: set MCITEMS to a minecraft-data items.json to verify material names.
Needs: pip install pyyaml
"""
import json, os, re, sys
import yaml

here = os.path.dirname(os.path.abspath(__file__))
path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "..", "src", "main", "resources", "config.yml")
src = open(os.path.join(here, "..", "src", "main", "java", "com", "nexuscraft", "nexusrime", "RimeConfig.java"), encoding="utf-8").read()
cfg = yaml.safe_load(open(path, encoding="utf-8"))
errors = []

def flat(d, prefix=""):
    out = {}
    for k, v in d.items():
        p = prefix + str(k)
        if isinstance(v, dict) and not p.endswith("ingredients"):
            out.update(flat(v, p + "."))
        else:
            out[p] = v
    return out

have = flat(cfg)
GEAR = {"dragonglass": False, "weapon": True, "dagger": True, "spear": True, "arrow": True}  # section -> has potency
read = set(re.findall(r'c\.get(?:String|Int|Double|Boolean|StringList|IntegerList)\("([a-z\-\.]+)"', src))
for sec, pot in GEAR.items():
    for k in ("material", "recipe-enabled", "recipe.amount", "recipe.ingredients") + (("potency", "recipe.shape") if pot else ()):
        read.add(f"{sec}.{k}")
read |= set(re.findall(r'c\.getConfigurationSection\("([a-z\-\.]+)"\)', src))
for k in sorted(read):
    if k not in have and not any(h.startswith(k + ".") for h in have):
        errors.append(f"code reads '{k}' but config.yml does not define it")
for k in sorted(have):
    if k not in read and not any(r.startswith(k + ".") or k.startswith(r + ".") for r in read):
        errors.append(f"config.yml defines '{k}' but the code never reads it")

# Shipped values must equal the built-in defaults (scalars only).
decl = {}
for m in re.finditer(r'^\s{4}(?:String|int|double|boolean)\s+(\w+)\s*=\s*([^;]+);', src, re.M):
    decl[m.group(1)] = m.group(2).strip()
def lit(v):
    if v in ("true", "false"):
        return v == "true"
    if v.startswith('"') and v.endswith('"'):
        return v[1:-1]
    try:
        return int(v) if re.fullmatch(r'-?\d+', v) else float(v)
    except ValueError:
        return None
for m in re.finditer(r'c\.get(String|Int|Double|Boolean)\("([a-z\-\.]+)",\s*(\w+)', src):
    kind, key, field = m.groups()
    if key not in have or field not in decl:
        continue
    default = lit(decl[field])
    if default is None:
        continue
    shipped = have[key]
    if isinstance(default, (int, float)) and not isinstance(default, bool):
        same = isinstance(shipped, (int, float)) and not isinstance(shipped, bool) and abs(float(shipped) - float(default)) < 1e-9
    else:
        same = shipped == default
    if not same:
        errors.append(f"'{key}': config.yml says {shipped!r} but the built-in default is {default!r}")

# Gear defaults in code (RimeConfig.resetGear) must match the shipped file.
SECTION = {"shard": "dragonglass", "blade": "weapon", "dagger": "dagger", "spear": "spear", "arrow": "arrow"}
for m in re.finditer(r'gear\.put\("(\w+)", new Gear\("\w+", "(\w+)", ([\d.]+), RecipeSpec\.(shaped|shapeless)\((.*?)\)\)\);', src, re.S):
    gid, mat, pot, kind, body = m.groups()
    sec = SECTION[gid]
    if have[f"{sec}.material"] != mat:
        errors.append(f"{sec}.material: config.yml says {have[f'{sec}.material']} but the built-in default is {mat}")
    if gid != "shard" and abs(float(have[f"{sec}.potency"]) - float(pot)) > 1e-9:
        errors.append(f"{sec}.potency: config.yml says {have[f'{sec}.potency']} but the built-in default is {pot}")
    amount = int(re.findall(r'(\d+)\s*$', body.strip())[-1])
    if have[f"{sec}.recipe.amount"] != amount:
        errors.append(f"{sec}.recipe.amount: config.yml says {have[f'{sec}.recipe.amount']} but the built-in default is {amount}")
    if kind == "shaped":
        shape = re.findall(r'"([^"]*)"', body.split("Map.of")[0])
        if have[f"{sec}.recipe.shape"] != shape:
            errors.append(f"{sec}.recipe.shape: config.yml differs from the built-in default {shape}")
        pairs = dict(re.findall(r"'(.)', ([A-Za-z_.\"]+)", body.split("Map.of")[1]))
        want = {k: (v.split(".")[-1].strip('"') if v.startswith("RecipeSpec") else v.strip('"')) for k, v in pairs.items()}
        want = {k: ("DRAGONGLASS" if v == "SHARD" else v) for k, v in want.items()}
        if have[f"{sec}.recipe.ingredients"] != want:
            errors.append(f"{sec}.recipe.ingredients: config.yml differs from the built-in default {want}")
    else:
        loose = re.findall(r'"([A-Z_]+)"', body)
        if have[f"{sec}.recipe.ingredients"] != loose:
            errors.append(f"{sec}.recipe.ingredients: config.yml differs from the built-in default {loose}")
for k, name in (("names.shard", "shardName"), ("names.dagger", "daggerName"), ("names.spear", "spearName"), ("names.arrow", "arrowName"), ("names.weapon", "weaponName")):
    pass  # names are checked by the generic scalar comparison above

# Structure sanity
for need in ("dragonglass.recipe.ingredients",):
    if not isinstance(have.get(need), list) or not have[need]:
        errors.append(f"{need} must be a list of materials (the shard recipe is shapeless)")
if isinstance(have.get("dragonglass.recipe.ingredients"), list) and len(have["dragonglass.recipe.ingredients"]) > 9:
    errors.append("the shard recipe has more than nine ingredients")
for sec in ("weapon", "dagger", "spear", "arrow"):
    shape = have.get(f"{sec}.recipe.shape", [])
    ing = have.get(f"{sec}.recipe.ingredients", {})
    if not isinstance(shape, list) or not 1 <= len(shape) <= 3 or any(not isinstance(r, str) or not 1 <= len(r) <= 3 for r in shape) \
            or len({len(r) for r in shape if isinstance(r, str)}) > 1:
        errors.append(f"{sec}.recipe.shape must be 1-3 rows of the same length (1-3 characters)")
        continue
    used = set("".join(shape)) - {" "}
    if not isinstance(ing, dict):
        errors.append(f"{sec}.recipe.ingredients must be a letter -> material map")
        continue
    if used - set(ing):
        errors.append(f"{sec} recipe uses {sorted(used - set(ing))} with no ingredient")
    if set(ing) - used:
        errors.append(f"{sec} recipe ingredients {sorted(set(ing) - used)} are never used in the shape")
    if "DRAGONGLASS" not in ing.values():
        errors.append(f"{sec} recipe never uses DRAGONGLASS")
    if not 0.1 <= have[f"{sec}.potency"] <= 3.0:
        errors.append(f"{sec}.potency must be 0.1-3.0")
for sec in GEAR:
    if not 1 <= have[f"{sec}.recipe.amount"] <= 64:
        errors.append(f"{sec}.recipe.amount must be 1-64")
# Dragonglass arrows must not be mistakable for the vanilla arrow recipe (a vertical flint/stick/feather column).
if have["arrow.recipe.shape"] == ["F", "T", "S"] or len(have["arrow.recipe.shape"]) == 3 and all(len(r) == 1 for r in have["arrow.recipe.shape"]):
    errors.append("arrow recipe is a vertical column like the vanilla arrow recipe")
if not have.get("events.worlds"):
    errors.append("events.worlds must list at least one world")
for k in ("events.chance-percent", "knight.chance-percent", "obsidian-drop.chance-percent"):
    if not 0 <= have[k] <= 100:
        errors.append(f"{k} must be 0-100")
if have["obsidian-drop.min"] > have["obsidian-drop.max"]:
    errors.append("obsidian-drop.min is above obsidian-drop.max")
if have["knight.breach.crew"] > have["knight.breach.max-contributors"] * 4:
    errors.append("knight.breach.crew is far above max-contributors")
if have["knight.bridge.material"] in ("WATER", "LAVA", "AIR"):
    errors.append("knight.bridge.material must be a solid block")
if have["wights.start-max"] > have["wights.max-alive"]:
    errors.append("wights.start-max is above wights.max-alive")
msgs = [k for k in have if k.startswith("messages.")]
for k in msgs:
    for ph in re.findall(r'\{(\w+)\}', have[k]):
        if ph not in ("walker", "wight", "weapon", "shard", "event", "target", "world", "knight", "liquid"):
            errors.append(f"{k}: unknown placeholder {{{ph}}}")

# Materials
mats = None
mc = os.environ.get("MCITEMS")
if mc and os.path.exists(mc):
    mats = {i["name"].upper() for i in json.load(open(mc))}
    blocks = os.path.join(os.path.dirname(mc), "blocks.json")
    if os.path.exists(blocks):
        mats |= {i["name"].upper() for i in json.load(open(blocks))}
if mats is not None:
    wanted = list(have["cold.warmth-blocks"]) + list(have["dragonglass.recipe.ingredients"])
    for sec in GEAR:
        wanted.append(have[f"{sec}.material"])
        if sec != "dragonglass":
            wanted += list(have[f"{sec}.recipe.ingredients"].values())
    wanted = [w for w in wanted if w.upper() != "DRAGONGLASS"]
    wanted += [d.split(":")[0] for d in have["rewards.drops"] if d.split(":")[0].upper() != "DRAGONGLASS"]
    for m in wanted:
        if m.upper() not in mats:
            errors.append(f"unknown material {m}")
for d in have["rewards.drops"]:
    parts = d.split(":")
    if len(parts) > 2 or (len(parts) == 2 and not parts[1].isdigit()):
        errors.append(f"bad drop '{d}' (use MATERIAL or MATERIAL:amount)")

if errors:
    print("config problems:")
    for e in errors:
        print("  -", e)
    sys.exit(1)
print(f"config.yml OK ({len(have)} settings checked)")
