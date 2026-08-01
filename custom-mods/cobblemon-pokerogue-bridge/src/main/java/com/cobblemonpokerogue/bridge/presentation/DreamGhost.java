package com.cobblemonpokerogue.bridge.presentation;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.cobblemonpokerogue.bridge.api.RunEndSummary;
import com.cobblemonpokerogue.bridge.api.RunSnapshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dream ghosts at the shrine: while a run is active, a static, uncatchable, no-AI
 * {@link PokemonEntity} of the runner's current lead stands near the configured shrine with a
 * nameplate ("X's dream — wave N"). The species swaps when the lead changes, the nameplate
 * updates each wave, and the ghost is discarded on run end and on server stop.
 *
 * <p>Ghosts must never survive on disk: every ghost carries a command tag, and any tagged entity
 * that joins a level {@code loadedFromDisk} (a crash left it behind) is discarded on sight. That
 * sweeper runs even when the feature is disabled, so leftovers from a previously-enabled config
 * still get cleaned up.
 *
 * <p>Registered on the NeoForge event bus.
 */
public final class DreamGhost {
    private static final Logger LOG = LoggerFactory.getLogger("pokerogue-bridge");
    private static final String GHOST_TAG = "pokerogue_dream_ghost";

    private final PresentationConfig config;
    private final DreamLang lang;
    /** player UUID -> live ghost; only touched on the server main thread (contract guarantee). */
    private final Map<UUID, GhostRef> ghosts = new HashMap<>();

    private record GhostRef(UUID entityUuid, String speciesId) {}

    DreamGhost(PresentationConfig config, DreamLang lang) {
        this.config = config;
        this.lang = lang;
    }

    private boolean active() {
        return config.dreamGhostEnabled() && config.shrinePos() != null;
    }

    void onRunStartedOrProgress(RunSnapshot s) {
        if (!active()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server != null ? shrineLevel(server) : null;
        if (level == null) {
            return;
        }
        String speciesId = normalizeSpeciesId(s.leadSpecies());
        String nameplate = lang.format("pokerogue.presentation.ghost.nameplate",
                DreamAnnouncer.playerName(server, s), s.wave());

        GhostRef ref = ghosts.get(s.mcPlayerId());
        if (ref != null) {
            Entity existing = level.getEntity(ref.entityUuid());
            if (existing != null && ref.speciesId().equals(speciesId)) {
                existing.setCustomName(Component.literal(nameplate));
                return;
            }
            if (existing != null) {
                existing.discard(); // lead changed: swap species
            }
            ghosts.remove(s.mcPlayerId());
        }
        PokemonEntity ghost = spawn(level, s, speciesId, nameplate);
        if (ghost != null) {
            ghosts.put(s.mcPlayerId(), new GhostRef(ghost.getUUID(), speciesId));
        }
    }

    void onRunEnded(RunSnapshot s, RunEndSummary summary) {
        GhostRef ref = ghosts.remove(s.mcPlayerId());
        if (ref == null) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server != null ? shrineLevel(server) : null;
        if (level != null) {
            Entity existing = level.getEntity(ref.entityUuid());
            if (existing != null) {
                existing.discard();
            }
        }
    }

    @Nullable
    private PokemonEntity spawn(ServerLevel level, RunSnapshot s, String speciesId, String nameplate) {
        Species species = PokemonSpecies.getByName(speciesId);
        if (species == null) {
            LOG.warn("dream ghost: unknown species '{}' (from lead '{}'), skipping ghost for {}",
                    speciesId, s.leadSpecies(), s.pokerogueUsername());
            return null;
        }
        try {
            Pokemon pokemon = new Pokemon();
            pokemon.setSpecies(species);
            PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
            UncatchableProperty.INSTANCE.uncatchable().apply(entity);
            entity.setNoAi(true);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setPersistenceRequired(); // no natural despawn
            entity.addTag(GHOST_TAG);        // disk-load sweeper marker
            entity.setCustomName(Component.literal(nameplate));
            entity.setCustomNameVisible(true);

            // Ring the ghosts around the shrine so simultaneous dreamers do not overlap;
            // the angle is stable per player, and each ghost faces the shrine.
            PresentationConfig.ShrinePos shrine = config.shrinePos();
            double angle = ((s.mcPlayerId().hashCode() & 0xFFFF) / 65536.0) * Math.PI * 2.0;
            double x = shrine.x() + Math.cos(angle) * 2.0;
            double z = shrine.z() + Math.sin(angle) * 2.0;
            float yaw = (float) (Math.toDegrees(angle) - 90.0);
            entity.moveTo(x, shrine.y(), z, yaw, 0.0f);

            if (!level.addFreshEntity(entity)) {
                LOG.warn("dream ghost: level rejected the ghost entity for {}", s.pokerogueUsername());
                return null;
            }
            return entity;
        } catch (RuntimeException e) {
            LOG.warn("dream ghost: failed to spawn '{}' for {}", speciesId, s.pokerogueUsername(), e);
            return null;
        }
    }

    /** Ghosts are ephemeral: anything tagged that comes back from disk is a leftover — discard it. */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(GHOST_TAG)) {
            event.setCanceled(true);
            event.getEntity().discard();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ServerLevel level = shrineLevel(event.getServer());
        if (level != null) {
            for (GhostRef ref : ghosts.values()) {
                Entity existing = level.getEntity(ref.entityUuid());
                if (existing != null) {
                    existing.discard();
                }
            }
        }
        ghosts.clear();
    }

    @Nullable
    private ServerLevel shrineLevel(MinecraftServer server) {
        PresentationConfig.ShrinePos shrine = config.shrinePos();
        if (shrine == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(shrine.dimension());
        if (id == null) {
            LOG.warn("dream ghost: bad shrine dimension id '{}'", shrine.dimension());
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            LOG.warn("dream ghost: shrine dimension '{}' is not loaded", shrine.dimension());
        }
        return level;
    }

    /** PokeRogue species names ("Mr. Mime", "PIKACHU") to Cobblemon showdown-style ids ("mrmime"). */
    private static String normalizeSpeciesId(String leadSpecies) {
        return leadSpecies == null ? "" : leadSpecies.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // Kept for API clarity: PokemonProperties is the documented alternate spawn path should the
    // direct-constructor route regress in a Cobblemon update. See module report.
    @SuppressWarnings("unused")
    private static PokemonEntity spawnViaProperties(ServerLevel level, String speciesId) {
        return PokemonProperties.Companion.parse(speciesId).createEntity(level);
    }
}
