#!/usr/bin/env bash
# Re-harvest the item registry from the mod jars on the server.
# The tier list seeds its universe from this file, so items that nothing in our
# configs references yet still get a tier. Run after a modpack bump.
#
#   COBBLEMON_SSH=user@host ops/loot-tiers/refresh_registry.sh
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
