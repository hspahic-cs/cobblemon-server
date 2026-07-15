#!/bin/bash
# ops/snapshots/prod-snapshot.sh — back-compat wrapper.
#
# The snapshot logic now lives in the env-parameterized world-snapshot.sh; this wrapper preserves
# the exact prior behavior (weekly `prod-YYYY-MM-DD` snapshots, keep 5) for the existing
# prod-snapshot.timer. New callers should invoke `world-snapshot.sh <env>` directly.
#
# NOTE (reset v2): the daily maintenance pipeline (wilderness-maintenance.sh) now takes a world
# snapshot every day as the pre-wipe rollback point, so this weekly job is redundant. Once the
# pipeline is deployed, disable prod-snapshot.timer to avoid double-snapshots (see ops/systemd/README).
set -euo pipefail
exec "$(dirname "$(readlink -f "$0")")/world-snapshot.sh" prod
