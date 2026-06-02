# Connect to the server

## You'll need

- The modpack [installed in Prism Launcher](install.md).
- The server IP, **ask Harris on Discord**. We don't post it publicly to
  keep random scanners off the box.

## Joining

1. Launch the modpack instance from Prism Launcher.
2. At the title screen: **Multiplayer → Add Server**.
3. **Server Name**, anything you like (e.g. `Cobblemon`).
4. **Server Address**, paste the IP Harris gave you, including the port.
5. **Done → Join Server**.

The first connection downloads server-side resource overrides; expect
30–60 seconds. Subsequent connects are instant.

## First login

You'll spawn into the world with a starter kit. Find Harris in-game (or
on Discord) for a tour and to get pointed at your first gym.

## Trouble?

!!! warning "Outdated server / outdated client"
    Your modpack version doesn't match the server. Grab the latest
    `.mrpack` and reinstall, see [Updating the pack](install.md#updating-the-pack).

!!! warning "Connection refused / timeout"
    Double-check the IP and port. If everything looks right, ping Harris;
    the server is a home VM and occasionally goes down for upgrades.

!!! warning "Mod-load conflicts"
    If the connect attempt errors with mismatched mod IDs, do a clean
    reinstall of the `.mrpack`, leftover jars from older pack versions
    sometimes hang around.
