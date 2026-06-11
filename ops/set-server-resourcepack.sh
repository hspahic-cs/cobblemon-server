#!/usr/bin/env bash
# Idempotently set the server resource-pack keys in a server.properties file.
#
# Run on the VM during deploy (piped in over ssh). Sets/replaces exactly three keys and leaves
# everything else in server.properties (rcon password, etc.) untouched. Safe to run repeatedly.
#
# Usage: set-server-resourcepack.sh <url> <sha1> <server.properties-path>
#
# Note: require-resource-pack is hardcoded to false on purpose — players who decline or can't
# reach the host fall back to playing without the textures rather than being kicked.
set -euo pipefail

url="${1:?usage: set-server-resourcepack.sh <url> <sha1> <server.properties>}"
sha1="${2:?missing sha1}"
file="${3:?missing server.properties path}"

[ -f "$file" ] || { echo "set-server-resourcepack: no server.properties at $file" >&2; exit 1; }

# Replace the line starting with "<key>=" (or append it if absent), in one portable awk pass.
# awk (not sed -i) avoids the BSD/GNU in-place divergence; cp-back preserves the file's owner/perms.
set_prop() {
  local key="$1" val="$2" tmp
  tmp=$(mktemp)
  awk -v k="${key}=" -v repl="${key}=${val}" \
    'index($0, k) == 1 { print repl; found = 1; next } { print } END { if (!found) print repl }' \
    "$file" > "$tmp"
  cp "$tmp" "$file"
  rm -f "$tmp"
}

set_prop "resource-pack" "$url"
set_prop "resource-pack-sha1" "$sha1"
set_prop "require-resource-pack" "false"
echo "set-server-resourcepack: resource-pack set (require=false, sha1=${sha1}) in ${file}"
