package org.langebe.skilltreemc.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.langebe.skilltreemc.data.SkillData;

/**
 * Persistent per-player cooldowns (spells, Rage, Shield, Living Immortal, ...).
 *
 * <p>Cooldowns are stored as the overworld game time at which they end. Game time is
 * saved with the world, so cooldowns keep running correctly across relogs and restarts.
 */
public final class Cooldowns {

    private Cooldowns() {
    }

    /** Current world time in ticks (20 ticks = 1 second). */
    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static long now(ServerPlayer player) {
        return now(player.level().getServer());
    }

    public static boolean isReady(ServerPlayer player, String key) {
        return remainingTicks(player, key) <= 0;
    }

    public static long remainingTicks(ServerPlayer player, String key) {
        Long readyAt = SkillData.getState(player).cooldowns().get(key);
        return readyAt == null ? 0 : readyAt - now(player);
    }

    /** Remaining seconds, rounded up (for messages). */
    public static long remainingSeconds(ServerPlayer player, String key) {
        return (remainingTicks(player, key) + 19) / 20;
    }

    public static void start(ServerPlayer player, String key, double seconds) {
        long now = now(player);
        // Guard against broken config values (NaN / huge numbers would overflow the tick count).
        double ticks = Double.isFinite(seconds) ? Math.max(0, Math.min(seconds * 20, Integer.MAX_VALUE)) : 0;
        long readyAt = now + Math.round(ticks);
        SkillData.modifyState(player, state -> state.withCooldown(key, readyAt, now));
    }
}
