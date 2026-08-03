package com.cobblemonpokerogue.bridge.presentation;

import org.jetbrains.annotations.Nullable;

/**
 * Runtime-mutable presentation anchors. Seeded from config at boot; {@code /dream admin}
 * re-points them live (and persists the change back to config.json). Volatile because the
 * poller-thread event path reads what the command thread writes.
 */
public final class Anchors {

    @Nullable
    private volatile PresentationConfig.ShrinePos shrine;
    @Nullable
    private volatile PresentationConfig.BoardPos board;
    @Nullable
    private volatile PresentationConfig.BoardPos journal;

    Anchors(@Nullable PresentationConfig.ShrinePos shrine, @Nullable PresentationConfig.BoardPos board,
            @Nullable PresentationConfig.BoardPos journal) {
        this.shrine = shrine;
        this.board = board;
        this.journal = journal;
    }

    @Nullable
    public PresentationConfig.ShrinePos shrine() {
        return shrine;
    }

    @Nullable
    public PresentationConfig.BoardPos board() {
        return board;
    }

    public void setShrine(PresentationConfig.ShrinePos shrine) {
        this.shrine = shrine;
    }

    public void setBoard(PresentationConfig.BoardPos board) {
        this.board = board;
    }

    @Nullable
    public PresentationConfig.BoardPos journal() {
        return journal;
    }

    public void setJournal(PresentationConfig.BoardPos journal) {
        this.journal = journal;
    }
}
