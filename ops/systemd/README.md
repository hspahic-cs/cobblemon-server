# Wilderness rolling-reset timer (05:30 America/New_York)

Activation layer for the `cobblemon-wilderness` rolling reset. A systemd **timer** fires a **oneshot
service** once a day at **05:30 America/New_York**, which runs `ops/wilderness-arm-restart.sh <env>`:
it arms the prune (`/wildreset now` into the server's `screen` session) and restarts
`cobblemon-<env>`. The mod prunes once at boot, then consumes the arm flag.

Templated by env — install `@dev` and/or `@prod` instances independently.

> These files are **not installed on the VM** by this change — they are landed here for review.
> Follow the steps below to install.

## Why a timer (not cron)

- **DST-correct.** `OnCalendar=*-*-* 05:30:00 America/New_York` pins the timezone in the expression,
  so it tracks EST↔EDT automatically — always 05:30 *local*, never a hardcoded UTC offset that drifts
  an hour across a DST change. (Requires systemd ≥ v240; the VM's is far newer.)
- **Inert by construction.** Only this job arms the prune. Deploys, apt restarts, and any other boot
  stay inert (mod guarantee — `state.forceNextBoot` gates the prune). No interval clock of our own.
- **`Persistent=false`** — a missed 05:30 is *not* caught up later (that would restart mid-day and
  break the low-pop-window intent). Missing a day is harmless: the prune is idle-gated and idempotent.

## Prerequisites

- The server runs as `sysadmin` under `screen -DmS cobblemon-<env>` (existing setup).
- `sysadmin` already has NOPASSWD for `systemctl restart cobblemon-<env>` (same right
  `ops/wilderness-reset.sh` relies on).
- The **first** supervised prune should be done by hand first (`ops/wilderness-reset.sh <env> confirm`,
  optionally after `/wildreset now force` for the breaker override) so `enabled=true`/`dryRun=false`
  are set and the initial backlog is verified. This timer only re-arms the steady-state prune daily.

## Install

```bash
# 1. Driver script (path must match ExecStart in the .service):
sudo install -o root -g root -m 0755 \
  ops/wilderness-arm-restart.sh /usr/local/bin/wilderness-arm-restart.sh

# 2. Unit templates:
sudo install -o root -g root -m 0644 \
  ops/systemd/cobblemon-wilderness-reset@.service /etc/systemd/system/
sudo install -o root -g root -m 0644 \
  ops/systemd/cobblemon-wilderness-reset@.timer   /etc/systemd/system/

sudo systemctl daemon-reload

# 3. Enable the timer per env (dev first, then prod):
sudo systemctl enable --now cobblemon-wilderness-reset@dev.timer
sudo systemctl enable --now cobblemon-wilderness-reset@prod.timer
```

## Verify

```bash
# Next fire time + that the calendar resolves to 05:30 local (EST or EDT):
systemctl list-timers 'cobblemon-wilderness-reset@*'
systemd-analyze calendar '*-*-* 05:30:00 America/New_York'

# Dry-run the whole flow now, without waiting for 05:30 (arms + restarts immediately):
sudo systemctl start cobblemon-wilderness-reset@dev.service
journalctl -u cobblemon-wilderness-reset@dev.service -n 30 --no-pager
# then confirm the prune ran once at boot:
grep -iE 'cobblemon_wilderness' /opt/cobblemon-dev/logs/latest.log | grep -iE 'deleted|baseline|abort' | tail
```

A midday reboot between fires does nothing (unarmed boot → inert), which is the design.
