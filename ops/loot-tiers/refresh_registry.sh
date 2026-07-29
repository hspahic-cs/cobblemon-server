#!/usr/bin/env bash
# Re-harvest the data files the tier list is built from, off the mod jars.
# Run after a modpack bump.
#
#   COBBLEMON_SSH=user@host ops/loot-tiers/refresh_registry.sh
#
#   item-registry.json  every real item id + display name + tooltip. Seeds the
#                       universe, so items nothing references yet still get tiered.
#   mod-recipes.json    recipes shipped by the mods, keyed by output item. Without
#                       it every craftable item reads as sourceless.
#   mod-loot.json       loot tables shipped BY THE MODS. Regenerate this with the
#                       companion harvest (see mod-loot.json's own _comment) --
#                       without it, evidence covers only tables we override and
#                       items sourced from ruins/archaeology read as "not granted
#                       anywhere", which is how 9 type gems were wrongly reported
#                       as sourceless.
set -euo pipefail
: "${COBBLEMON_SSH:?set COBBLEMON_SSH to the VM's user@host}"
MODS=${MODS_DIR:-/srv/cobblemon/cobblemon-prod/mods}
OUT=$(dirname "$0")/item-registry.json

ssh "$COBBLEMON_SSH" "MODS=$MODS python3 - <<'PY'
import zipfile,glob,os,json,re
out={}
for j in sorted(glob.glob(os.environ['MODS']+'/*.jar')):
    try: z=zipfile.ZipFile(j)
    except Exception: continue
    for n in z.namelist():
        if not n.endswith('lang/en_us.json'): continue
        try: d=json.loads(z.read(n).decode('utf8','replace'))
        except Exception: continue
        for k,v in d.items():
            m=re.fullmatch(r'item\.([a-z0-9_]+)\.([a-z0-9_]+)',k)
            if m and m.group(1) in ('cobblemon','mega_showdown','legendarymonuments'):
                out[m.group(1)+':'+m.group(2)]=v
print(json.dumps(out))
PY" | python3 -c "
import json,sys
r=json.load(sys.stdin)
json.dump({'_comment':'Item registry harvested from the mod jars on the server (item.<ns>.<name> lang keys). Seeds the tier list universe so items nothing references yet still get tiered. Refresh with: ops/loot-tiers/refresh_registry.sh','_source':'harvested from the live mods dir','items':dict(sorted(r.items()))}, open('$OUT','w'), indent=1, ensure_ascii=False)
print(f'wrote {len(r)} items to $OUT')
"
