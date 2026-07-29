# Snapshot ops

Source-of-truth copies of the snapshot/reset infra installed at
`/opt/snapshots/` and `/etc/systemd/system/` on the cobblemon VM.

See [`docs/snapshots.md`](../../docs/snapshots.md) for what these do and how
to use them.

## Layout when installed

| Repo path | VM path |
|---|---|
| `world-snapshot.sh` | `/usr/local/bin/world-snapshot.sh` (mode 0755, owner `root`) |
| `prod-snapshot.sh` | `/opt/snapshots/prod-snapshot.sh` (mode 0755, owner `sysadmin`) |
| `dev-reset.sh`     | `/opt/snapshots/dev-reset.sh` (mode 0755, owner `sysadmin`) |
| `prod-snapshot.service` | `/etc/systemd/system/prod-snapshot.service` (mode 0644) |
| `prod-snapshot.timer`   | `/etc/systemd/system/prod-snapshot.timer` (mode 0644) |

`world-snapshot.sh <dev|prod>` holds the actual logic. `prod-snapshot.sh` is a
thin wrapper that execs `world-snapshot.sh prod`, kept only because
`prod-snapshot.service` points at that original path.

!!! note "Current drift"

    `dev-reset.sh` is **not installed** on the VM right now — only
    `prod-snapshot.sh` is present under `/opt/snapshots/`. The reinstall below
    puts it back.

## Reinstall

Set `COBBLEMON_SSH` to the VM's `user@host` first.

```sh
scp ops/snapshots/* "$COBBLEMON_SSH:/tmp/"
ssh "$COBBLEMON_SSH" 'set -e
  sudo install -m 0755 -o root -g root         /tmp/world-snapshot.sh /usr/local/bin/world-snapshot.sh
  sudo install -m 0755 -o sysadmin -g sysadmin /tmp/prod-snapshot.sh  /opt/snapshots/prod-snapshot.sh
  sudo install -m 0755 -o sysadmin -g sysadmin /tmp/dev-reset.sh      /opt/snapshots/dev-reset.sh
  sudo install -m 0644 /tmp/prod-snapshot.service /etc/systemd/system/prod-snapshot.service
  sudo install -m 0644 /tmp/prod-snapshot.timer   /etc/systemd/system/prod-snapshot.timer
  sudo systemctl daemon-reload
  sudo systemctl enable --now prod-snapshot.timer
'
```
