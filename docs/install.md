# Install the modpack

Everything you need to join, NeoForge, Cobblemon, Minecolonies, performance
and QoL mods, and our in-house gym mod, ships in one `.mrpack` file.

## Prerequisites

- A legitimate Minecraft Java Edition account (Mojang/Microsoft).
- **Java 21**, required by NeoForge 1.21.1.
    - macOS: `brew install openjdk@21`
    - Windows / Linux: [Adoptium Temurin 21](https://adoptium.net/)

## 1. Install Prism Launcher

Prism Launcher is a free, open-source Minecraft launcher. It manages
multiple instances cleanly and imports `.mrpack` files natively.

Download: [prismlauncher.org/download](https://prismlauncher.org/download/)

On first launch it'll ask for:

- Your Microsoft account, sign in.
- A Java install, point it at the Java 21 you set up above.

## 2. Import the modpack

1. Grab the latest `Cobblemon Server-x.y.z.mrpack` from the
   [Dev latest release on GitHub](https://github.com/hspahic-cs/cobblemon-server/releases/tag/dev-latest).
   This release is a rolling pre-release that always tracks the version
   currently running on the server, so the `.mrpack` you download here
   matches the server you're about to connect to.
2. Drag the `.mrpack` file onto the Prism Launcher window.
3. Name the instance (e.g. `Cobblemon Server`) → **OK**.
4. Prism downloads NeoForge 1.21.1 and ~35 mods. Takes 2–5 minutes.

## 3. Launch

Double-click the instance. Minecraft starts with all mods loaded.

You're ready to [connect to the server](connect.md).

## Updating the pack

When a new `.mrpack` drops:

1. In Prism, right-click the instance → **Edit**.
2. **Version → Reinstall from zip** → pick the new `.mrpack`.

Worlds, screenshots, and settings are preserved; only mods are swapped.

## Troubleshooting

!!! warning "Java not found on launch"
    Prism → **Settings → Java** → point at your Java 21 JDK.

!!! warning "Black screen / instant crash"
    Open Prism's **Logs** tab. ~90% of launch failures are a wrong Java
    version or an outdated GPU driver.

!!! warning "Outdated server / outdated client on connect"
    Your `.mrpack` version doesn't match what the server is running.
    Grab the latest pack from the [Dev latest release](https://github.com/hspahic-cs/cobblemon-server/releases/tag/dev-latest)
    and reinstall (Prism → right-click instance → **Edit → Version → Reinstall from zip**).

!!! warning "Mods not loading in-game"
    Verify Prism actually launched the modpack: the main menu should say
    `NeoForge 21.1.227` in the bottom-left, and the **Mods** button should
    show 35+ entries. If it says `Vanilla`, you launched the wrong instance.
