package com.cobblemonpokerogue.bridge.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for {@link RunEventListener}s. Register at any time (mod construction is fine);
 * the core fires listeners on the SERVER MAIN THREAD via {@code server.execute()}.
 */
public final class BridgeEvents {

    /** Package-private: {@link BridgeEventsInternal} iterates this on dispatch. */
    static final List<RunEventListener> LISTENERS = new CopyOnWriteArrayList<>();

    private BridgeEvents() {}

    public static void register(RunEventListener l) {
        if (l != null) LISTENERS.add(l);
    }
}
