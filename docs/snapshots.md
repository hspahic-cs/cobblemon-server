# Snapshots & dev resets

Point-in-time snapshots of a cobblemon environment's world + cobblemon mod
configs, kept under `/opt/snapshots/` on the VM. Prod is snapshotted weekly on
a timer; dev is snapshotted on demand.

Snapshots live on their own disk mounted at `/opt/snapshots` — they do not
compete for space with the servers themselves (which live on `/srv/cobblemon`,
reachable via the `/opt/cobblemon-{dev,prod}` symlinks).

## What's snapshotted

From `/opt/cobblemon-<env>/`:

- `world/`, all dimensions, player .dat files, Cobblemon party + PC store
- `config/cobblemon-*` and `config/cobblemon_*`, bridge / gacha / market /
  ranked / npc state directories

NOT snapshotted: `server.properties`, `ops.json`, `whitelist.json`, mods, jvm
args, anything outside the two paths above. Those are per-instance.

## When

Prod: weekly via `prod-snapshot.timer` at **Wed 05:00** (when no one's playing).
Dev: no timer — run it by hand when you want a restore point.

Keeps the most recent 5 snapshots **per environment** and prunes older ones.
Override with `SNAPSHOT_KEEP=<n>`.

Snapshot copy is incremental: `rsync --link-dest` hardlinks unchanged files
against the previous snapshot so the on-disk cost is roughly "one full + diffs."
Because of that hardlinking, `du` on a single snapshot directory over-reports
what deleting it would actually reclaim — measure `/opt/snapshots` as a whole.

Snapshots are safe to take while the server is **up**: the script pauses world
saves and flushes via RCON, copies, then resumes. `wilderness-maintenance.sh`
relies on this and uses a snapshot as its pre-wipe rollback point.

## Files on the VM

| Path | Purpose |
|---|---|
| `/usr/local/bin/world-snapshot.sh` | The snapshot script — takes `dev` or `prod` |
| `/opt/snapshots/prod-snapshot.sh` | Thin wrapper: `exec world-snapshot.sh prod` |
| `/opt/snapshots/<env>-YYYY-MM-DD/` | A snapshot |
| `/etc/systemd/system/prod-snapshot.service` | systemd unit (prod only) |
| `/etc/systemd/system/prod-snapshot.timer` | systemd timer (prod only) |

The wrapper exists only because `prod-snapshot.service` points at the original
path. Don't delete it without also repointing the unit — doing so previously
broke prod snapshots for three weeks with `status=203/EXEC`.

Repo source of truth for all of these: [`ops/snapshots/`](https://github.com/hspahic-cs/cobblemon-server/tree/main/ops/snapshots).

## Usage

### See available snapshots

```sh
ls /opt/snapshots/
```

### Run an ad-hoc snapshot now

Prod, via the unit (this is what the timer runs):

```sh
sudo systemctl start prod-snapshot.service
journalctl -u prod-snapshot.service -n 30
```

Either environment, directly:

```sh
sudo /usr/local/bin/world-snapshot.sh dev
sudo /usr/local/bin/world-snapshot.sh prod
```

The script exits non-zero on any failure, so a caller can abort when no fresh
backup exists.

### Reset cobblemon-dev to a prod snapshot

!!! warning "`dev-reset.sh` is not currently installed on the VM"

    The script is kept in the repo at `ops/snapshots/dev-reset.sh` but is not
    present under `/opt/snapshots/`. Install it before use:

    ```sh
    scp ops/snapshots/dev-reset.sh "$COBBLEMON_SSH:/tmp/"
    ssh "$COBBLEMON_SSH" 'sudo install -m 0755 -o sysadmin -g sysadmin \
      /tmp/dev-reset.sh /opt/snapshots/dev-reset.sh'
    ```

Once installed:

```sh
sudo /opt/snapshots/dev-reset.sh                    # latest snapshot
sudo /opt/snapshots/dev-reset.sh prod-2026-07-29    # a specific one
```

The script asks to confirm, stops dev, backs up the current world to
`world.before-reset-<timestamp>`, restores from the snapshot, and starts dev.

### Roll back a reset

If you change your mind right after running reset:

```sh
sudo systemctl stop cobblemon-dev
sudo rm -rf /opt/cobblemon-dev/world
sudo mv /opt/cobblemon-dev/world.before-reset-<ts> /opt/cobblemon-dev/world
sudo systemctl start cobblemon-dev
```

## Notes

- The script reads the prod RCON password from `server.properties` to
  pause/resume saves, but doesn't store it. Snapshot files are owned
  `sysadmin:sysadmin 0644`, accessible only to operators.
- Reset clears `/opt/cobblemon-dev/.deployed_version` so the next CI deploy is
  not skipped as "already deployed". The deploy will reapply the right mods on
  top of the snapshotted world.
- Reset removes existing `config/cobblemon-*` dirs on dev before restoring
  from snapshot, so leftover dev-only state (e.g. test admin overrides) is
  wiped along with the world.
