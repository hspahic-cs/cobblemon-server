package com.cobblemonpokerogue.bridge.econ;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reflection bridge to NeoEssentials Economy — the server's only real currency (same pattern
 * as cobblemon-market's EconomyBridge; no compile-time NeoEssentials artifact exists).
 *
 * <p>Failure semantics verified against neoessentials-1.0.2.5+build.1074 bytecode
 * ({@code EconomyManager.subtractBalance}): the subtraction runs atomically inside
 * {@code ConcurrentHashMap.compute}; when {@code allowNegativeBalances} is false (the
 * default — the key is absent from our economy.json) an insufficient balance leaves the
 * stored value UNCHANGED and the method returns {@code false}. It also returns {@code false},
 * without mutating, during manager shutdown. So {@code withdraw} is a genuine
 * check-and-deduct: false always means "not charged". The manager is thread-safe
 * (ConcurrentHashMap + async save queue), so calls are legal from any thread.
 *
 * <p>Failures degrade to no-ops with a single warning so the bridge keeps running when
 * NeoEssentials is absent (a dev instance without the full pack); callers must treat
 * {@link #available()} == false as "cannot charge", never as "free".
 */
public final class NeoEssentialsEconomy {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");
    private static final String ECONOMY_CLASS = "com.zerog.neoessentials.economy.managers.EconomyManager";

    private static volatile Object manager;
    private static volatile Method getBalance;
    private static volatile Method addBalance;
    private static volatile Method subtractBalance;
    private static final AtomicBoolean warnedOnce = new AtomicBoolean(false);

    private NeoEssentialsEconomy() {}

    private static Object manager() {
        Object m = manager;
        if (m != null) return m;
        try {
            Class<?> cls = Class.forName(ECONOMY_CLASS);
            m = cls.getMethod("getInstance").invoke(null);
            getBalance = m.getClass().getMethod("getBalance", UUID.class);
            addBalance = m.getClass().getMethod("addBalance", UUID.class, BigDecimal.class);
            subtractBalance = m.getClass().getMethod("subtractBalance", UUID.class, BigDecimal.class);
            manager = m;
            return m;
        } catch (ClassNotFoundException e) {
            warnOnce("NeoEssentials Economy not loaded — /dream enter cannot charge and is disabled");
            return null;
        } catch (Throwable t) {
            warnOnce("NeoEssentials Economy reflection failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    public static boolean available() {
        return manager() != null;
    }

    /** Current balance, floored to int; 0 when the economy is unavailable. */
    public static int balance(UUID player) {
        try {
            Object m = manager();
            if (m == null) return 0;
            return ((BigDecimal) getBalance.invoke(m, player)).intValue();
        } catch (Throwable t) {
            LOGGER.error("NeoEssentialsEconomy.balance failed", t);
            return 0;
        }
    }

    /**
     * Atomic check-and-deduct. @return true only when the full amount was actually taken;
     * false means nothing was charged (insufficient funds, shutdown, or no economy).
     */
    public static boolean withdraw(UUID player, int amount) {
        if (amount <= 0) return true;
        try {
            Object m = manager();
            if (m == null) return false;
            return (Boolean) subtractBalance.invoke(m, player, BigDecimal.valueOf(amount));
        } catch (Throwable t) {
            LOGGER.error("NeoEssentialsEconomy.withdraw failed", t);
            return false;
        }
    }

    /** Refund path for a charge whose follow-up failed. @return false if the deposit did not happen. */
    public static boolean deposit(UUID player, int amount) {
        if (amount <= 0) return true;
        try {
            Object m = manager();
            if (m == null) return false;
            return (Boolean) addBalance.invoke(m, player, BigDecimal.valueOf(amount));
        } catch (Throwable t) {
            LOGGER.error("NeoEssentialsEconomy.deposit failed", t);
            return false;
        }
    }

    private static void warnOnce(String msg) {
        if (warnedOnce.compareAndSet(false, true)) LOGGER.warn(msg);
    }
}
