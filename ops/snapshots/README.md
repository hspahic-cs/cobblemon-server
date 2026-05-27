# Snapshot ops

Source-of-truth copies of the snapshot/reset infra installed at
`/opt/snapshots/` and `/etc/systemd/system/` on the cobblemon VM.

See [`docs/snapshots.md`](../../docs/snapshots.md) for what these do and how
to use them.

## Layout when installed

| Repo path | VM path |
|---|---|
| `prod-snapshot.sh` | `/opt/snapshots/prod-snapshot.sh` (mode 0755, owner `sysadmin`) |
| `dev-reset.sh`     | `/opt/snapshots/dev-reset.sh` (mode 0755, owner `sysadmin`) |
| `prod-snapshot.service` | `/etc/systemd/system/prod-snapshot.service` (mode 0644) |
| `prod-snapshot.timer`   | `/etc/systemd/system/prod-snapshot.timer` (mode 0644) |

## Reinstall

```sh
scp ops/snapshots/* sysadmin@192.168.1.20:/tmp/
ssh sysadmin@192.168.1.20 'set -e
  sudo install -m 0755 -o sysadmin -g sysadmin /tmp/prod-snapshot.sh /opt/snapshots/prod-snapshot.sh
  sudo install -m 0755 -o sysadmin -g sysadmin /tmp/dev-reset.sh    /opt/snapshots/dev-reset.sh
  sudo install -m 0644 /tmp/prod-snapshot.service /etc/systemd/system/prod-snapshot.service
  sudo install -m 0644 /tmp/prod-snapshot.timer   /etc/systemd/system/prod-snapshot.timer
  sudo systemctl daemon-reload
  sudo systemctl enable --now prod-snapshot.timer
'
```
