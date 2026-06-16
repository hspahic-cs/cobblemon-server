#!/usr/bin/env python3
"""Generate high-level RCTmod wild trainers + retune the over-leveled boss pool.

Problem: above party level ~60 the RCTmod wild pool collapses to jar-default *boss* trainers
(gym leaders, Elite Four, champions, rivals, battleground, team admins) — competitive teams that
are over-leveled (rivals/champs/e4 are "key trainers" that bypass the level-diff scaling). There are
almost no plain "normal" trainers up there.

This script writes one datapack, `server-trainer-spawns`, with three parts:

  A. REMOVE gyms + Elite Four from wild  — every jar mob with type in {leader, e4} → spawnWeightFactor 0.
  B. LEVEL-GATE + thin the other bosses  — champ/rival/battleground/ligh_of_ruin (and high-level team
     admins) → retyped to "normal" (so they respect the scaled maxLevelDiff and stop spawning 20+
     levels above the player) and spawnWeightFactor cut to 0.08 so they're drowned out.
  C. ADD ~80 themed mid-tier trainers at L55-100 — 8 archetypes (Bird Keeper, Bug Catcher, Hiker,
     Fisherman, Black Belt, Psychic, Hex Maniac, Cooltrainer), 2-4 fully-evolved Pokémon each, valid
     abilities pulled from Cobblemon species data, NO movesets (Cobblemon auto-assigns natural
     level-up moves — non-competitive), empty EVs, moderate IVs. spawnWeightFactor 0.35.

Reads the rctmod + cobblemon jars locally; deterministic (fixed RNG seed).
"""
import zipfile, json, random, re
from pathlib import Path

RCT_JAR = next(Path("/Users/almutwakel/Documents/Projects/minecraft/cobblemon-server/mods").glob("rctmod-*.jar"))
COB_JAR = next(Path("/Users/almutwakel/Documents/Projects/minecraft/cobblemon-server/mods").glob("Cobblemon-*.jar"))
OUT = Path("modpack/server-overrides/datapacks/server-trainer-spawns/data/rctmod")
SERIES = ["bdsp", "radicalred", "unbound"]           # series the existing wild pool uses (spawns for our players)
NATURES = ["hardy","lonely","brave","adamant","naughty","bold","docile","relaxed","impish","lax",
           "timid","hasty","serious","jolly","naive","modest","mild","quiet","bashful","rash",
           "calm","gentle","sassy","careful","quirky"]

# ---- Cobblemon: species -> first non-hidden ability (validates species existence) ----
cob = zipfile.ZipFile(COB_JAR)
species_ability = {}
for n in cob.namelist():
    m = re.search(r"data/cobblemon/species/[^/]+/([a-z0-9_]+)\.json$", n)
    if not m:
        continue
    try:
        d = json.loads(cob.read(n))
    except Exception:
        continue
    abils = [a for a in d.get("abilities", []) if not str(a).startswith("h:")]
    if abils:
        species_ability[m.group(1)] = abils[0]

# ---- Archetypes: (class id, display, type-skin, species pool, name pool) ----
NAMES = ["alex","blake","casey","drew","evan","finn","gabe","hank","ivan","jonah","kyle","liam",
         "mason","noah","owen","pete","quinn","reed","seth","troy","vince","wade","zane","cory",
         "dana","elle","faye","gwen","hope","iris","jade","kira","lena","mira","nora","opal","piper",
         "rosa","tessa","uma","vera","wren"]
ARCH = {
 "bird_keeper": ("Bird Keeper","type_flying",
    ["pidgeot","fearow","noctowl","swellow","staraptor","braviary","talonflame","honchkrow",
     "unfezant","crobat","altaria","mandibuzz","pelipper","dodrio","xatu","skarmory"]),
 "bug_catcher": ("Bug Catcher","type_bug",
    ["butterfree","beedrill","scizor","heracross","yanmega","vespiquen","galvantula","leavanny",
     "volcarona","durant","ariados","crustle","ribombee","orbeetle","escavalier","accelgor"]),
 "hiker": ("Hiker","type_rock",
    ["golem","rhyperior","gigalith","aggron","tyranitar","steelix","donphan","probopass",
     "sudowoodo","marowak","golurk","crustle","carbink","gliscor"]),
 "fisherman": ("Fisherman","type_water",
    ["gyarados","milotic","lapras","kingdra","sharpedo","crawdaunt","floatzel","seaking",
     "octillery","whiscash","cloyster","qwilfish","politoed","ludicolo","poliwrath","starmie"]),
 "black_belt": ("Black Belt","type_fighting",
    ["machamp","hariyama","conkeldurr","lucario","hitmonlee","hitmonchan","gallade","toxicroak",
     "throh","sawk","medicham","primeape","pangoro","mienshao"]),
 "psychic": ("Psychic","type_psychic",
    ["alakazam","gardevoir","slowbro","metagross","reuniclus","musharna","gothitelle","xatu",
     "claydol","bronzong","hypno","girafarig","beheeyem","exeggutor"]),
 "hex_maniac": ("Hex Maniac","type_ghost",
    ["gengar","mismagius","dusknoir","chandelure","cofagrigus","banette","drifblim","jellicent",
     "spiritomb","golurk","sableye","froslass","trevenant","dhelmise"]),
 "cooltrainer": ("Cooltrainer","type_normal",
    ["snorlax","arcanine","dragonite","gyarados","lapras","tyranitar","salamence","garchomp",
     "hydreigon","flygon","electivire","magmortar","ampharos","kangaskhan","aggron","gardevoir"]),
}
BRACKETS = [55, 65, 75, 85, 95]
PER = 2  # trainers per (archetype, bracket) -> 8*5*2 = 80

rng = random.Random(20260616)
team_dir = OUT / "trainers"
mob_dir = OUT / "mobs/trainers/single"
team_dir.mkdir(parents=True, exist_ok=True)
mob_dir.mkdir(parents=True, exist_ok=True)

def write(d, path):
    with open(path, "w") as f:
        json.dump(d, f, indent=2); f.write("\n")

added = 0
skipped = set()
for cls, (disp, skin, pool) in ARCH.items():
    valid = [s for s in pool if s in species_ability]
    skipped |= (set(pool) - set(valid))
    for br in BRACKETS:
        for i in range(PER):
            name_id = rng.choice(NAMES)
            tid = f"hl_{cls}_{br}_{name_id}_{i}"
            tex = f"rctmod:textures/trainers/single/{skin}.png"
            size = rng.choice([2, 3, 3, 4])
            picks = rng.sample(valid, min(size, len(valid)))
            team = []
            for sp in picks:
                team.append({
                    "species": sp,
                    "level": br + rng.randint(-3, 3),
                    "gender": "RANDOM",
                    "nature": rng.choice(NATURES),
                    "ability": species_ability[sp],
                    "ivs": {k: 12 for k in ("hp","atk","def","spa","spd","spe")},
                    "evs": {},
                    # moveset intentionally omitted -> Cobblemon assigns natural level-up moves
                })
            write({
                "ai": {"type": "rb", "data": {}},
                "name": f"{disp} {name_id.capitalize()}",
                "textureResource": tex,
                "team": team,
            }, team_dir / f"{tid}.json")
            write({
                "type": "normal",
                "spawnWeightFactor": 0.35,
                "battleCooldownTicks": 240,
                "maxTrainerDefeats": 1,
                "maxTrainerWins": -1,
                "optional": True,
                "series": SERIES,
                "requiredDefeats": [],
                "biomeTagWhitelist": [],
                "biomeTagBlacklist": [],
                "textureResource": tex,
            }, mob_dir / f"{tid}.json")
            added += 1

print(f"C: wrote {added} new high-level trainers ({len(species_ability)} cobblemon species indexed)")
if skipped:
    print(f"   (skipped unknown species: {sorted(skipped)})")

# ---- A + B: override jar boss mobs ----
rct = zipfile.ZipFile(RCT_JAR)
team_lvl = {}
for n in rct.namelist():
    m = re.search(r"data/rctmod/trainers/([^/]+)\.json$", n)
    if m:
        try:
            t = json.loads(rct.read(n)); team_lvl[m.group(1)] = max([p.get("level", 0) for p in t.get("team", [])] or [0])
        except Exception:
            pass

REMOVE = {"leader", "e4"}
GATE = {"champ", "rival", "battleground", "ligh_of_ruin"}
TEAM_TYPES = {"team_rocket", "team_galactic", "team_shadow"}
a_cnt = b_cnt = 0
for n in rct.namelist():
    m = re.search(r"data/rctmod/mobs/trainers/single/([^/]+)\.json$", n)
    if not m:
        continue
    tid = m.group(1)
    try:
        d = json.loads(rct.read(n))
    except Exception:
        continue
    if d.get("spawnWeightFactor", 0) <= 0:
        continue
    typ = d.get("type")
    if typ in REMOVE:
        d["spawnWeightFactor"] = 0.0
        d["_comment"] = "wild spawn disabled (gym leader / Elite Four) — server-trainer-spawns"
        a_cnt += 1
    elif typ in GATE or (typ in TEAM_TYPES and team_lvl.get(tid, 0) >= 55):
        d["type"] = "normal"               # respect scaled maxLevelDiff (stop over-leveled spawns)
        d["spawnWeightFactor"] = 0.08       # drowned out by the new normal trainers
        d["_comment"] = f"boss retyped normal + thinned (was {typ}) — server-trainer-spawns"
        b_cnt += 1
    else:
        continue
    write(d, mob_dir / f"{tid}.json")

print(f"A: disabled wild spawn for {a_cnt} gym-leader/E4 mobs")
print(f"B: level-gated + thinned {b_cnt} boss mobs (retyped normal, weight 0.08)")
