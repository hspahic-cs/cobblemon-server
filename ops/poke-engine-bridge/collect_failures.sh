#!/usr/bin/env bash
# Pull pick_failures.jsonl from every bridge pod into one local file and print a
# fingerprint histogram for batch-fixing.
#
# Each pod has its own RWO PVC (RWO can't be co-mounted), so the failure backlog
# is split per replica. This collects all of them by pod name, concatenates, and
# groups by the `fingerprint` field (exc type + deepest frame) so identical bugs
# collapse to one bucket — that's the batch to fix.
#
# Usage:
#   ./collect_failures.sh [OUT_FILE]
# Env overrides:
#   NS        namespace            (default: poke-engine-bridge)
#   SELECTOR  pod label selector   (default: app=poke-engine-bridge)
#   LOG_DIR   path inside the pod  (default: /battle-logs)
set -euo pipefail

NS="${NS:-poke-engine-bridge}"
SELECTOR="${SELECTOR:-app=poke-engine-bridge}"
LOG_DIR="${LOG_DIR:-/battle-logs}"
OUT="${1:-pick_failures.merged.jsonl}"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

pods="$(kubectl get pods -n "$NS" -l "$SELECTOR" -o jsonpath='{.items[*].metadata.name}')"
if [ -z "$pods" ]; then
  echo "no pods in ns=$NS matching $SELECTOR" >&2
  exit 1
fi

: > "$OUT"
for pod in $pods; do
  # Copy the live log plus any rolled-over .1 generation. Either may be absent
  # (a pod with no failures yet) — that's fine, skip on failure.
  for name in pick_failures.jsonl pick_failures.jsonl.1; do
    dst="$tmp/$pod-$name"
    # kubectl cp doesn't propagate the remote tar's exit code (a missing file
    # still "succeeds"), so don't trust its status — check the local copy is
    # actually non-empty instead.
    kubectl cp -n "$NS" "$pod:$LOG_DIR/$name" "$dst" >/dev/null 2>&1 || true
    if [ -s "$dst" ]; then
      cat "$dst" >> "$OUT"
      echo "  + $pod:$name ($(wc -l < "$dst" | tr -d ' ') lines)" >&2
    fi
  done
done

total="$(wc -l < "$OUT" | tr -d ' ')"
echo "merged $total failure records -> $OUT" >&2

if [ "$total" -gt 0 ] && command -v jq >/dev/null 2>&1; then
  echo >&2
  echo "count  fingerprint   phase   sample error" >&2
  echo "-----  ------------  ------  ------------" >&2
  # One row per bug bucket: how many times it hit, plus a representative
  # phase + error so you know what you're fixing without opening the file.
  jq -rs '
    group_by(.fingerprint)
    | map({fp: .[0].fingerprint, phase: .[0].phase, err: .[0].error, n: length})
    | sort_by(-.n)[]
    | "\(.n)\t\(.fp)\t\(.phase)\t\(.err)"' "$OUT" \
    | awk -F'\t' '{printf "%5d  %-12s  %-6s  %s\n", $1, $2, $3, $4}' >&2
fi
