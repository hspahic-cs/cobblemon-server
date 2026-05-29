# Snapshots & dev resets

Weekly snapshots of `cobblemon-prod`'s world + cobblemon mod configs are taken
on the VM, kept under `/opt/snapshots/`, and used to refresh `cobblemon-dev`
to a prod-like state on demand.

## What's snapshotted

From `/opt/cobblemon-prod/`:

- `world/` — all dimensions, player .dat files, Cobblemon party + PC store
- `config/cobblemon-*` and `config/cobblemon_*` — bridge / gacha / market /
  ranked / npc state directories

NOT snapshotted: `server.properties`, `ops.json`, `whitelist.json`, mods, jvm
args, anything outside the two paths above. Those are per-instance.

## When

Weekly via `prod-snapshot.timer` at **Wed 05:00** (when no one's playing).
Keeps the most recent 5 snapshots, prunes older.

Snapshot copy is incremental: `rsync --link-dest` hardlinks unchanged files
against the previous snapshot so the on-disk cost is roughly "one full + diffs."

## Files on the VM

| Path | Purpose |
|---|---|
| `/opt/snapshots/prod-snapshot.sh` | The snapshot script |
| `/opt/snapshots/dev-reset.sh`     | Manual dev-reset script |
| `/opt/snapshots/prod-YYYY-MM-DD/` | A snapshot |
| `/etc/systemd/system/prod-snapshot.service` | systemd unit |
| `/etc/systemd/system/prod-snapshot.timer`   | systemd timer |

## Usage

### See available snapshots

```sh
ls /opt/snapshots/
```

### Run an ad-hoc snapshot now

```sh
sudo systemctl start prod-snapshot.service
journalctl -u prod-snapshot.service -n 30
```

### Reset cobblemon-dev to the latest snapshot

```sh
sudo /opt/snapshots/dev-reset.sh
```

The script asks to confirm, stops dev, backs up the current world to
`world.before-reset-<timestamp>`, restores from the snapshot, and starts dev.

### Reset to a specific snapshot

```sh
sudo /opt/snapshots/dev-reset.sh prod-2026-05-21
```

### Roll back a reset

If you change your mind right after running reset:

```sh
sudo systemctl stop cobblemon-dev
sudo rm -rf /opt/cobblemon-dev/world
sudo mv /opt/cobblemon-dev/world.before-reset-<ts> /opt/cobblemon-dev/world
sudo systemctl start cobblemon-dev
```

## Notes

- Snapshots include the prod RCON password indirectly — the script reads it
  from `server.properties` to pause/resume saves, but doesn't store it. Files
  are owned `sysadmin:sysadmin 0644`, accessible only to operators.
- Reset clears `/opt/cobblemon-dev/.deployed_version` so the next CI deploy is
  not skipped as "already deployed". The deploy will reapply the right mods on
  top of the snapshotted world.
- Reset removes existing `config/cobblemon-*` dirs on dev before restoring
  from snapshot, so leftover dev-only state (e.g. test admin overrides) is
  wiped along with the world.
