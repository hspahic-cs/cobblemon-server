# Server VM setup

The Minecraft server VM lives at `192.168.1.20` on the home LAN. Two
NeoForge servers run side-by-side: `cobblemon-prod` (live) and
`cobblemon-dev` (testing). Both are supervised by systemd, each wrapped in
a `screen` session so you can attach a console.

## Layout

```
/opt/cobblemon-prod/        Production server (port 25565, RCON 25575)
/opt/cobblemon-dev/         Dev server         (port 25566, RCON 25576)
  mods/                     Live mod dir — must be a real directory, NOT a symlink
                            (Sinytra Connector's services break under symlinks)
  mods.vX.Y.Z/              Per-release archives at install root; deploy hardlink-
                            copies one of these into mods/ (cp -al → no extra disk)
  staging/                  rsync target for CI; deployer-owned
  world/                    World data (dev starts fresh; prod is the existing world)
  config/, libraries/, ...  Standard NeoForge layout
/etc/systemd/system/cobblemon-prod.service
/etc/systemd/system/cobblemon-dev.service
/home/sysadmin/.cobblemon-rcon   RCON passwords (mode 600)
```

The old `~/minecraft/` (Fabric/Pixelmon) and the screen-loop wrapper
`start.sh` are no longer used. They remain on disk as a safety net for
the original world data.

## Common operations

```sh
ssh -i ~/.ssh/id_ed25519 sysadmin@192.168.1.20

# Service control
sudo systemctl status cobblemon-prod
sudo systemctl restart cobblemon-dev
sudo journalctl -u cobblemon-prod -f

# Console attach (server runs inside screen, detachable)
screen -r cobblemon-prod
# detach with Ctrl-A then D

# RCON (passwords in ~/.cobblemon-rcon, mode 600)
source ~/.cobblemon-rcon
mcrcon -H 127.0.0.1 -P "$PROD_RCON_PORT" -p "$PROD_RCON_PASSWORD" 'list'
```

## Users

| User | Purpose | Auth |
|------|---------|------|
| `sysadmin` | Human admin. Passwordless sudo. | Personal SSH key |
| `deployer` | CI/CD only. SSH-key-only, no shell password. | `~/.ssh/cobblemon-deployer` (local) |

`deployer`'s sudo is scoped (see `/etc/sudoers.d/deployer`) to:
- `systemctl {restart,start,stop,is-active,status} cobblemon-{prod,dev}`
- `cat /home/sysadmin/.cobblemon-rcon` as `sysadmin` (so CI can read RCON
  passwords without them leaving the VM in plaintext)

`deployer` has an ACL grant of `rwx` on `/opt/cobblemon-{prod,dev}` so it
can rsync into `staging/`, create new `mods.vX.Y.Z/` directories, and swap
the `mods/` directory without sudo. World data is owned by `sysadmin` and
not touched by deploys.

## Deploy model (how CI uses this layout)

1. Runner builds the modpack, computes the new `mods/` tree.
2. `rsync` it into `/opt/cobblemon-{env}/staging/mods.vX.Y.Z/`.
3. `mv staging/mods.vX.Y.Z ..` to seat the new version archive at install
   root.
4. `cp -al mods.vX.Y.Z mods.swap-new` — hardlink copy, costs no jar bytes
   but produces a *real directory* (NOT a symlink — Sinytra Connector's
   `IModFileCandidateLocator` services don't get picked up via NeoForge's
   `ServiceLoader` when `mods/` is a symlink).
5. `mv mods mods.swap-old && mv mods.swap-new mods` — atomic swap, then
   `rm -rf mods.swap-old`.
6. `sudo systemctl restart cobblemon-{env}`.
7. Old `mods.vX.Y.Z` archives prune to the most recent N (e.g. 5).

Rollback: `cp -al mods.vPREVIOUS mods.swap-new`, then the same atomic
swap-and-restart. The `mods.vX.Y.Z` archives are kept around precisely so
this is cheap.

## Caveats / TODO

- Both VMs have a pending kernel update (deferred from `apt install rsync acl`).
  Reboot during the next maintenance window: `sudo reboot`.
- Old `~/minecraft/` (1.7G) preserved as a safety net. Delete with
  `sudo rm -rf ~/minecraft` once you're confident the new layout is stable.
- Disabled `minecraft.service` is still in `/etc/systemd/system/` (not
  deleted, just disabled). Remove with `sudo rm /etc/systemd/system/minecraft.service`
  when you're sure.
- mcrcon is not in apt; CI will ship its own binary. For ad-hoc local use,
  build from source: `https://github.com/Tiiffi/mcrcon`.
