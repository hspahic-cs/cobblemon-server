#!/usr/bin/env bash
# Bump the repo version. One version covers both the modpack and the
# custom mod (cobblemon-npc). Run from any directory.
#
# Usage: scripts/bump-version.sh <new-version>
# Example: scripts/bump-version.sh 0.3.0

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <new-version>  (e.g. 0.3.0)" >&2
  exit 1
fi

NEW="$1"

if [[ ! "$NEW" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "error: version must be semver MAJOR.MINOR.PATCH (got: $NEW)" >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACK_TOML="$REPO_ROOT/modpack/pack.toml"
MOD_PROPS="$REPO_ROOT/custom-mods/cobblemon-npc/gradle.properties"

CURRENT="$(awk -F\" '/^version = /{print $2; exit}' "$PACK_TOML")"
echo "modpack/pack.toml:               $CURRENT -> $NEW"
echo "custom-mods/cobblemon-npc:       $(awk -F= '/^mod_version=/{print $2; exit}' "$MOD_PROPS") -> $NEW"

# BSD/GNU sed compatibility: write to a temp file and move.
sed "s/^version = \".*\"/version = \"$NEW\"/" "$PACK_TOML" > "$PACK_TOML.tmp" && mv "$PACK_TOML.tmp" "$PACK_TOML"
sed "s/^mod_version=.*/mod_version=$NEW/" "$MOD_PROPS" > "$MOD_PROPS.tmp" && mv "$MOD_PROPS.tmp" "$MOD_PROPS"

cat <<EOF

Done. Next steps:
  1. Edit CHANGELOG.md — move items from [Unreleased] into a new [$NEW] section.
  2. git add CHANGELOG.md modpack/pack.toml custom-mods/cobblemon-npc/gradle.properties
  3. git commit -m "release: $NEW"
  4. git tag v$NEW
  5. git push origin main --tags
EOF
