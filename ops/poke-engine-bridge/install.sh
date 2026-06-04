#!/usr/bin/env bash
# One-time bridge install on the VM. Run as sysadmin (needs sudo for useradd,
# /opt creation, systemd unit registration, sudoers extension).
#
#   scp -i ~/.ssh/id_ed25519 -r ops/poke-engine-bridge sysadmin@192.168.1.20:/tmp/
#   ssh -i ~/.ssh/id_ed25519 sysadmin@192.168.1.20 'sudo bash /tmp/poke-engine-bridge/install.sh'
#
# After this, deploys are handled by the deploy-bridge.yml workflow (rsync
# + restart). This script is idempotent — safe to re-run on the same VM.
set -euo pipefail

INSTALL_DIR=/opt/poke-engine-bridge
SVC_USER=cobblemon-bridge
SVC_NAME=poke-engine-bridge

# Repo paths (relative to this script's location after rsync to VM).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ $EUID -ne 0 ]]; then
  echo "must run as root (use: sudo bash install.sh)" >&2
  exit 1
fi

echo "==> ensuring service user '$SVC_USER' exists"
if ! id -u "$SVC_USER" >/dev/null 2>&1; then
  useradd --system --home-dir "$INSTALL_DIR" --shell /usr/sbin/nologin "$SVC_USER"
fi

echo "==> creating $INSTALL_DIR layout"
mkdir -p "$INSTALL_DIR"/{app,foul-play,venv,smogon-cache}
chown -R "$SVC_USER:$SVC_USER" "$INSTALL_DIR"

echo "==> installing system deps (python3-venv, build tools for poke-engine wheel)"
apt-get update -qq
apt-get install -y --no-install-recommends \
  python3.12 python3.12-venv python3.12-dev \
  build-essential pkg-config rsync curl ca-certificates \
  >/dev/null

echo "==> ensuring rustup toolchain (poke-engine builds via maturin)"
RUSTUP_HOME=/opt/rustup
CARGO_HOME=/opt/cargo
mkdir -p "$RUSTUP_HOME" "$CARGO_HOME"
chown -R "$SVC_USER:$SVC_USER" "$RUSTUP_HOME" "$CARGO_HOME"
if ! sudo -u "$SVC_USER" RUSTUP_HOME="$RUSTUP_HOME" CARGO_HOME="$CARGO_HOME" \
     "$CARGO_HOME/bin/cargo" --version >/dev/null 2>&1; then
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs > /tmp/rustup-init.sh
  sudo -u "$SVC_USER" RUSTUP_HOME="$RUSTUP_HOME" CARGO_HOME="$CARGO_HOME" \
    sh /tmp/rustup-init.sh -y --no-modify-path --default-toolchain stable --profile minimal
  rm /tmp/rustup-init.sh
fi

echo "==> bootstrapping venv at $INSTALL_DIR/venv"
sudo -u "$SVC_USER" python3.12 -m venv "$INSTALL_DIR/venv"
# poke-engine builds with these features at install time — match the foul-play recipe.
POKE_ENGINE_PIN='poke-engine==0.0.46 --config-settings=build-args=--features poke-engine/terastallization --no-default-features'

echo "==> installing bridge runtime deps into venv"
sudo -u "$SVC_USER" \
  RUSTUP_HOME="$RUSTUP_HOME" CARGO_HOME="$CARGO_HOME" \
  PATH="$CARGO_HOME/bin:$PATH" \
  "$INSTALL_DIR/venv/bin/pip" install --upgrade pip wheel >/dev/null

# Bridge-specific deps from requirements.txt + foul-play deps + poke-engine.
sudo -u "$SVC_USER" \
  RUSTUP_HOME="$RUSTUP_HOME" CARGO_HOME="$CARGO_HOME" \
  PATH="$CARGO_HOME/bin:$PATH" \
  "$INSTALL_DIR/venv/bin/pip" install \
    -r "$SCRIPT_DIR/requirements.txt"

# foul-play's own deps: requests, websockets, dateutil, poke-engine.
# We DON'T need foul-play's `websockets` for our use case but it's in their
# requirements.txt and harmless. poke-engine MUST install with the build flag.
sudo -u "$SVC_USER" \
  RUSTUP_HOME="$RUSTUP_HOME" CARGO_HOME="$CARGO_HOME" \
  PATH="$CARGO_HOME/bin:$PATH" \
  "$INSTALL_DIR/venv/bin/pip" install \
    requests==2.33.0 'python-dateutil==2.8.0' websockets==14.1

echo "==> installing poke-engine 0.0.46 (Rust build via maturin — slow first time)"
sudo -u "$SVC_USER" \
  RUSTUP_HOME="$RUSTUP_HOME" CARGO_HOME="$CARGO_HOME" \
  PATH="$CARGO_HOME/bin:$PATH" \
  "$INSTALL_DIR/venv/bin/pip" install \
    'poke-engine==0.0.46' \
    --config-settings='build-args=--features poke-engine/terastallization --no-default-features' \
    --no-binary poke-engine

echo "==> installing systemd unit"
install -m 0644 "$SCRIPT_DIR/poke-engine-bridge.service" \
  /etc/systemd/system/${SVC_NAME}.service

if [[ ! -f /etc/default/${SVC_NAME} ]]; then
  install -m 0644 "$SCRIPT_DIR/poke-engine-bridge.env.example" \
    /etc/default/${SVC_NAME}
fi

echo "==> extending deployer sudoers for bridge service control"
SUDOERS_FILE=/etc/sudoers.d/poke-engine-bridge
cat > "$SUDOERS_FILE" <<EOF
# Allow CI/deployer to manage the bridge service (rsync + restart deploys).
deployer ALL=(root) NOPASSWD: /usr/bin/systemctl restart ${SVC_NAME}
deployer ALL=(root) NOPASSWD: /usr/bin/systemctl start ${SVC_NAME}
deployer ALL=(root) NOPASSWD: /usr/bin/systemctl stop ${SVC_NAME}
deployer ALL=(root) NOPASSWD: /usr/bin/systemctl is-active ${SVC_NAME}
deployer ALL=(root) NOPASSWD: /usr/bin/systemctl status ${SVC_NAME}
EOF
chmod 0440 "$SUDOERS_FILE"
visudo -c -f "$SUDOERS_FILE" >/dev/null

echo "==> ACL grant: deployer rwx on app/ + foul-play/ (both rsynced on each deploy)"
for d in "$INSTALL_DIR/app" "$INSTALL_DIR/foul-play"; do
  setfacl -R -m u:deployer:rwx "$d"
  setfacl -R -d -m u:deployer:rwx "$d"
done

systemctl daemon-reload
systemctl enable ${SVC_NAME}.service

echo
echo "Install complete. Next:"
echo "  1) Drop the bridge code into $INSTALL_DIR/app (CI deploy-bridge handles this)"
echo "  2) Drop the foul-play package source into $INSTALL_DIR/foul-play"
echo "  3) systemctl start ${SVC_NAME}"
echo "  4) curl http://127.0.0.1:8642/healthz"
