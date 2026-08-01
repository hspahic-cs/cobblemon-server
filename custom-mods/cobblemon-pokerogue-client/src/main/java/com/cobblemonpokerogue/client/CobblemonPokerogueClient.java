package com.cobblemonpokerogue.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Feasibility spike: open the PokeRogue web game (a Phaser/WebGL app) inside Minecraft
 * via MCEF (Chromium embedded). Client-dist only — the modpack ships every jar to
 * clients, and the dedicated server must never construct this mod (repo rule: paired /
 * client mods declare their dist explicitly).
 *
 * MCEF is an OPTIONAL dependency: every touch of an MCEF class lives in
 * {@link PokerogueScreen}, which is only classloaded behind a ModList.isLoaded("mcef")
 * guard — same pattern as roguelite's Accessories compat.
 */
@Mod(value = CobblemonPokerogueClient.MOD_ID, dist = Dist.CLIENT)
public final class CobblemonPokerogueClient {
    public static final String MOD_ID = "cobblemon_pokerogue_client";

    /**
     * Fallback target. The real URL (the self-hosted instance) comes from
     * {@code config/cobblemon-pokerogue-client.properties}, which is written with this
     * default on first launch and never committed — the hosted address is per-install.
     */
    public static final String POKEROGUE_URL = "https://pokerogue.net";
    private static final String CONFIG_FILE = "cobblemon-pokerogue-client.properties";

    public static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.cobblemon_pokerogue_client.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.misc"
    );

    /**
     * Client commands execute while the ChatScreen is still open; vanilla closes chat
     * AFTER the command runs, which clobbers any screen the command set. So the command
     * only raises this flag, and the tick handler opens the screen one frame later.
     */
    private boolean pendingOpen = false;

    public CobblemonPokerogueClient(IEventBus modBus) {
        modBus.addListener(this::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_KEY);
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("pokerogueui")
                        .executes(ctx -> {
                            pendingOpen = true;
                            return Command.SINGLE_SUCCESS;
                        })
        );
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            pendingOpen = true;
        }
        if (!pendingOpen) {
            return;
        }
        // Only open from a "free" state (no screen, or the chat screen just closed);
        // never stack on top of an existing PokerogueScreen.
        if (minecraft.screen != null) {
            return;
        }
        pendingOpen = false;
        openBrowser(minecraft);
    }

    private void openBrowser(Minecraft minecraft) {
        if (!ModList.get().isLoaded("mcef")) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(
                        "PokeRogue browser unavailable: the MCEF mod is not installed on this client."), false);
            }
            return;
        }
        // MCEF classes are only touched from here on (inside PokerogueScreen).
        PokerogueScreen.open(minecraft, loadUrl());
    }

    /** Read the target URL from the client config, seeding the file on first use. */
    private static String loadUrl() {
        java.nio.file.Path file = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        java.util.Properties props = new java.util.Properties();
        try {
            if (java.nio.file.Files.notExists(file)) {
                props.setProperty("url", POKEROGUE_URL);
                try (var out = java.nio.file.Files.newOutputStream(file)) {
                    props.store(out, "PokeRogue browser target (set to the self-hosted instance)");
                }
                return POKEROGUE_URL;
            }
            try (var in = java.nio.file.Files.newInputStream(file)) {
                props.load(in);
            }
            String url = props.getProperty("url", POKEROGUE_URL).trim();
            return url.isEmpty() ? POKEROGUE_URL : url;
        } catch (java.io.IOException e) {
            return POKEROGUE_URL;
        }
    }
}
