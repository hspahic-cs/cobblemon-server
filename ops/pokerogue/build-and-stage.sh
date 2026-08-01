#!/usr/bin/env bash
# Build the PokéRogue stack locally and stage it to the VM (no root needed).
# setup-vm.sh then promotes the staged artifacts into /opt/pokerogue.
#
# Upstream checkouts live in ~/Repos/vendor/{pokerogue,rogueserver} (shallow clones,
# submodules initialized for pokerogue). This script does NOT pull upstream — update
# the checkouts deliberately, then rebuild.
#
# The API URL is baked into the frontend at build time. The API is reverse-proxied
# same-origin under /api (see setup-vm.sh's nginx site) so the URL players' browsers
# reach is: <frontend-origin>/api, no trailing slash. Same-origin is load-bearing:
# the frontend's PKR-Client-Version header fails rogueserver's CORS preflight.
#
# Usage:
#   ops/pokerogue/build-and-stage.sh all      'http://<host>:8000/api'
#   ops/pokerogue/build-and-stage.sh frontend 'http://<host>:8000/api'
#   ops/pokerogue/build-and-stage.sh server
set -euo pipefail

WHAT="${1:?usage: build-and-stage.sh <all|frontend|server> [api-url]}"
VENDOR=~/Repos/vendor
STAGING=cobblemon:pokerogue-staging
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ $WHAT == all || $WHAT == server ]]; then
  ( cd "$VENDOR/rogueserver"
    # cobblemon-server patches: bridgeRunState mirror + §2.45 run-gate. Applied
    # in sorted filename order — each patch is generated on top of the previous
    # one, so the order is load-bearing. Restore-then-apply keeps this idempotent.
    git checkout -- .
    for p in "$SELF_DIR"/patches/rogueserver-*.patch; do
      git apply "$p"
    done
    GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -tags=devsetup -o /tmp/rogueserver-linux-amd64 . )
  ssh cobblemon 'mkdir -p ~/pokerogue-staging'
  scp -q /tmp/rogueserver-linux-amd64 "$STAGING/rogueserver"
  echo "staged: rogueserver binary"
fi

if [[ $WHAT == all || $WHAT == frontend ]]; then
  API_URL="${2:?frontend build needs the api url, e.g. http://host:8000/api}"
  ( cd "$VENDOR/pokerogue"
    # Upstream marks the session cookie Secure (+Domain=host) — browsers drop such
    # cookies over plain HTTP, so login silently bounces back to the title screen.
    # Restore-then-sed keeps the patch idempotent across builds. Revisit if we ever
    # front the site with TLS.
    git checkout -- src/utils/cookies.ts
    sed -i '' -e 's/;Secure;/;/g' -e 's/Domain=\${window.location.hostname};//' src/utils/cookies.ts
    # §2.45 run-gate: friendly message when the server rejects an unpaid new
    # classic run (402 "run-not-armed"). Restore-then-apply, and ordered before
    # the reskin so the two never fight over files (this patch stays out of
    # locales/, which the reskin applier restores).
    git checkout -- src/api/savedata-api.ts src/api/session-savedata-api.ts src/system/game-data.ts
    git apply "$SELF_DIR/patches/pokerogue-run-not-armed.patch"
    # Server reskin (title/splashes/trainer names) — restore-then-merge, same
    # idempotence contract as the cookie patch. Overlays: ops/pokerogue/reskin/.
    python3 "$SELF_DIR/apply-reskin.py" .
    cat > .env.production.local <<EOF
VITE_BYPASS_LOGIN=0
VITE_BYPASS_TUTORIAL=0
VITE_SERVER_URL=$API_URL
VITE_DISCORD_CLIENT_ID=0
VITE_GOOGLE_CLIENT_ID=0
VITE_I18N_DEBUG=0
EOF
    pnpm install --frozen-lockfile
    pnpm build )
  ssh cobblemon 'mkdir -p ~/pokerogue-staging'
  rsync -a --delete "$VENDOR/pokerogue/dist/" "$STAGING/frontend/"
  echo "staged: frontend (VITE_SERVER_URL=$API_URL)"
fi
