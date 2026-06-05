#!/usr/bin/env bash
# Pull battle logs (one JSONL per battle, one line per turn) from both bridge
# pods into ./battle-logs/. Battles bounce between pods, so the same battle id
# can exist on both — lines are self-contained; concatenate and sort by ts:
#
#   ./ops/fetch_battle_logs.sh
#   cat battle-logs/*/<battle_id>.jsonl | jq -s 'sort_by(.ts)' > merged.json
#
# Replay any logged turn offline (see ops/poke-engine-bridge/replay.py):
#   PYTHONPATH=reference/foul-play reference/foul-play/.venv/bin/python \
#     ops/poke-engine-bridge/replay.py battle-logs/pod-0/<battle_id>.jsonl --list
set -euo pipefail

CONTROL_PLANE=${CONTROL_PLANE:-sysops@192.168.1.101}
NS=poke-engine-bridge
OUT=${1:-battle-logs}

for pod in poke-engine-bridge-0 poke-engine-bridge-1; do
  dest="$OUT/${pod/poke-engine-bridge-/pod-}"
  mkdir -p "$dest"
  ssh "$CONTROL_PLANE" \
    "kubectl exec -n $NS $pod -- sh -c 'cd /battle-logs && tar cf - *.jsonl 2>/dev/null || true'" \
    | tar xf - -C "$dest" 2>/dev/null || true
  count=$(ls "$dest" 2>/dev/null | wc -l | tr -d ' ')
  echo "$pod -> $dest ($count battles)"
done
