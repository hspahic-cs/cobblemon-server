#!/usr/bin/env bash
# One-shot (and idempotent re-run) setup for the self-hosted PokéRogue stack on the VM.
#
# Installs MariaDB + nginx, creates the rogueserver DB/user (password generated once,
# kept only in /etc/pokerogue/rogueserver.env), promotes the artifacts staged by
# build-and-stage.sh from ~sysadmin/pokerogue-staging/, and installs+starts the
# rogueserver systemd service and the nginx static site.
#
# Ports: 8000 = frontend (nginx static), 8001 = rogueserver API.
# The frontend origin doubles as rogueserver's CORS pin (gameurl), so $1 must be the
# exact origin players' browsers will use — scheme://host:8000, no trailing slash.
#
# Usage (from your machine):
#   ssh cobblemon sudo bash -s -- 'http://<host>:8000' < ops/pokerogue/setup-vm.sh
#
# Re-run any time after re-staging to promote a new build; DB and password survive.
set -euo pipefail

GAME_ORIGIN="${1:?usage: setup-vm.sh <frontend-origin, e.g. http://host:8000>}"
STAGING=/home/sysadmin/pokerogue-staging
ENVFILE=/etc/pokerogue/rogueserver.env

[[ $EUID -eq 0 ]] || { echo "must run as root" >&2; exit 1; }
[[ -x $STAGING/rogueserver && -d $STAGING/frontend ]] || {
  echo "staged artifacts missing under $STAGING — run build-and-stage.sh first" >&2; exit 1; }

export DEBIAN_FRONTEND=noninteractive
apt-get install -y -qq mariadb-server nginx-light >/dev/null
systemctl enable --now mariadb >/dev/null

# --- DB + credentials (password minted once, then reused from the env file) ---
if [[ -f $ENVFILE ]]; then
  DBPASS=$(grep '^dbpass=' "$ENVFILE" | cut -d= -f2-)
else
  DBPASS=$(openssl rand -hex 24)
fi
mysql <<SQL
CREATE DATABASE IF NOT EXISTS pokeroguedb;
CREATE USER IF NOT EXISTS 'pokerogue'@'localhost' IDENTIFIED BY '$DBPASS';
ALTER USER 'pokerogue'@'localhost' IDENTIFIED BY '$DBPASS';
GRANT ALL PRIVILEGES ON pokeroguedb.* TO 'pokerogue'@'localhost';
FLUSH PRIVILEGES;
SQL

# Read-only DB account for the MC reward-bridge mod (SELECT only). Password lives
# sysadmin-readable because the MC server process runs as sysadmin and copies it
# into config/cobblemon-pokerogue-bridge/config.json.
BRIDGE_CRED=/home/sysadmin/pokerogue-bridge-db.txt
if [[ -f $BRIDGE_CRED ]]; then
  BPASS=$(cut -d: -f2 < "$BRIDGE_CRED")
else
  BPASS=$(openssl rand -hex 24)
fi
mysql <<SQL
CREATE USER IF NOT EXISTS 'pokerogue_bridge'@'localhost' IDENTIFIED BY '$BPASS';
ALTER USER 'pokerogue_bridge'@'localhost' IDENTIFIED BY '$BPASS';
GRANT SELECT ON pokeroguedb.* TO 'pokerogue_bridge'@'localhost';
-- §2.45 run-gate: the one table the bridge may write — armed-run credits.
-- Created here (rogueserver's db.Init also creates it, but create-before-grant
-- means the grant never races the table on a fresh VM).
CREATE TABLE IF NOT EXISTS pokeroguedb.bridgeRunArming (
  uuid BINARY(16) NOT NULL PRIMARY KEY,
  credits INT NOT NULL DEFAULT 0,
  updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
GRANT SELECT, INSERT, UPDATE, DELETE ON pokeroguedb.bridgeRunArming TO 'pokerogue_bridge'@'localhost';
FLUSH PRIVILEGES;
SQL
printf 'pokerogue_bridge:%s\n' "$BPASS" > "$BRIDGE_CRED"
chown sysadmin:sysadmin "$BRIDGE_CRED"
chmod 600 "$BRIDGE_CRED"

id -u pokerogue &>/dev/null || useradd -r -s /usr/sbin/nologin -d /opt/pokerogue pokerogue

# --- promote staged artifacts ---
mkdir -p /opt/pokerogue/bin /opt/pokerogue/data /etc/pokerogue
install -m 755 "$STAGING/rogueserver" /opt/pokerogue/bin/rogueserver
rsync -a --delete "$STAGING/frontend/" /opt/pokerogue/frontend/
chown -R root:root /opt/pokerogue
# rogueserver writes runtime state (secret.key) to its CWD — that dir alone is
# writable, and -R because the blanket root chown above just took its contents too
chown -R pokerogue:pokerogue /opt/pokerogue/data
# sysadmin owns the static frontend so build-and-stage.sh can push updates sudo-free
chown -R sysadmin:sysadmin /opt/pokerogue/frontend
chmod -R a+rX /opt/pokerogue/frontend

cat > "$ENVFILE" <<EOF
addr=0.0.0.0:8001
dbuser=pokerogue
dbpass=$DBPASS
dbproto=tcp
dbaddr=localhost
dbname=pokeroguedb
gameurl=$GAME_ORIGIN
EOF
chmod 600 "$ENVFILE"

# --- systemd service ---
cat > /etc/systemd/system/rogueserver.service <<'EOF'
[Unit]
Description=PokeRogue API server (rogueserver)
After=network.target mariadb.service
Wants=mariadb.service

[Service]
User=pokerogue
WorkingDirectory=/opt/pokerogue/data
EnvironmentFile=/etc/pokerogue/rogueserver.env
ExecStart=/opt/pokerogue/bin/rogueserver
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable rogueserver >/dev/null
systemctl restart rogueserver

# --- nginx static site on :8000 ---
cat > /etc/nginx/sites-available/pokerogue <<'EOF'
server {
    listen 8000;
    listen [::]:8000;
    root /opt/pokerogue/frontend;
    index index.html;
    gzip on;
    gzip_types application/javascript application/json text/css image/svg+xml;
    # Same-origin API: kills CORS (the frontend's PKR-Client-Version header fails
    # rogueserver's preflight otherwise) and leaves 8000 as the only public port.
    location /api/ {
        proxy_pass http://127.0.0.1:8001/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        client_max_body_size 16m;
    }
    # Never cache the entry point — hashed asset names handle versioning, but a
    # cached index.html pins users to a stale bundle across deploys.
    location = /index.html {
        add_header Cache-Control "no-store";
    }
    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-store";
    }
    location ~ \.(js|css|png|webp|m4a|ogg|mp3|wav|json|atlas|ttf|woff2?|wasm)$ {
        expires 1h;
        add_header Cache-Control "public";
        try_files $uri =404;
    }
}
EOF
ln -sf /etc/nginx/sites-available/pokerogue /etc/nginx/sites-enabled/pokerogue
nginx -t
systemctl reload nginx

sleep 2
systemctl is-active rogueserver || { journalctl -u rogueserver -n 5 --no-pager; exit 1; }
curl -s -o /dev/null -w 'frontend :8000      -> %{http_code}\n' http://localhost:8000/
curl -s -o /dev/null -w 'api      :8000/api/ -> %{http_code}\n' http://localhost:8000/api/account/info
echo "done — api 401 on /api/account/info is expected (unauthenticated)"
