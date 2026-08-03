package com.cobblemonpokerogue.bridge.journal;

import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemonpokerogue.bridge.presentation.DreamLang;
import com.cobblemonpokerogue.bridge.presentation.RogueSpecies;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * Renders a player's dream history as a written book and opens it client-side without giving
 * an item: the held slot is overwritten by packet, the book screen is opened, and the real
 * inventory is resynced immediately after (the client keeps the screen open).
 */
public final class JournalBook {

    private static final int ENTRIES_PER_PAGE = 3;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private JournalBook() {}

    public static void open(ServerPlayer player, DreamLang lang,
                            List<DreamJournal.Entry> entries, long deepestWave) {
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(Filterable.passThrough(coverPage(player, lang, entries, deepestWave)));
        for (int i = 0; i < entries.size(); i += ENTRIES_PER_PAGE) {
            MutableComponent page = Component.empty();
            for (DreamJournal.Entry e : entries.subList(i, Math.min(entries.size(), i + ENTRIES_PER_PAGE))) {
                page.append(entryLines(e));
            }
            pages.add(Filterable.passThrough(page));
        }

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(lang.format("pokerogue.journal.title")),
                player.getGameProfile().getName(), 0, pages, true));

        // -2 targets the player inventory directly; the selected hotbar index is its own slot id
        // there. The resync below restores the truth — the client keeps the book screen open.
        player.connection.send(new ClientboundContainerSetSlotPacket(
                -2, 0, player.getInventory().selected, book));
        player.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
        player.inventoryMenu.sendAllDataToRemote();
    }

    private static Component coverPage(ServerPlayer player, DreamLang lang,
                                       List<DreamJournal.Entry> entries, long deepestWave) {
        long victories = entries.stream().filter(DreamJournal.Entry::victory).count();
        MutableComponent page = Component.literal(lang.format("pokerogue.journal.header") + "\n\n")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        page.append(Component.literal(player.getGameProfile().getName() + "\n\n")
                .withStyle(ChatFormatting.DARK_GRAY));
        page.append(Component.literal(lang.format("pokerogue.journal.dreams", entries.size()) + "\n")
                .withStyle(ChatFormatting.BLUE));
        page.append(Component.literal(lang.format("pokerogue.journal.victories", victories) + "\n")
                .withStyle(ChatFormatting.GOLD));
        if (deepestWave > 0) {
            page.append(Component.literal(lang.format("pokerogue.journal.deepest", deepestWave))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        return page;
    }

    private static Component entryLines(DreamJournal.Entry e) {
        MutableComponent block = Component.literal(
                        DATE.format(Instant.ofEpochMilli(e.endedAtMs()).atZone(ZoneId.systemDefault())) + "\n")
                .withStyle(ChatFormatting.DARK_GRAY);
        String waveLine = e.victory()
                ? "★ " + waveText(e.wave()) + " — victory"
                : waveText(e.wave());
        block.append(Component.literal(waveLine + "\n")
                .withStyle(e.victory() ? ChatFormatting.GOLD : ChatFormatting.BLUE));
        String team = teamNames(e);
        if (!team.isEmpty()) {
            block.append(Component.literal(team + "\n").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!e.gameMode().isEmpty() && !"classic".equals(e.gameMode())) {
            block.append(Component.literal(e.gameMode() + "\n")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        block.append(Component.literal("\n"));
        return block;
    }

    /** The whole recorded lineup by name, falling back to just the lead for old entries. */
    private static String teamNames(DreamJournal.Entry e) {
        List<String> names = new ArrayList<>();
        for (String id : e.party().split(",")) {
            Species s = RogueSpecies.resolve(id.trim());
            if (s != null) {
                names.add(s.getName());
            }
        }
        if (names.isEmpty()) {
            Species lead = RogueSpecies.resolve(e.leadSpecies());
            if (lead != null) {
                names.add(lead.getName());
            }
        }
        return String.join(", ", names);
    }

    private static String waveText(int wave) {
        return wave >= 0 ? "Wave " + wave : "Wave unknown";
    }
}
