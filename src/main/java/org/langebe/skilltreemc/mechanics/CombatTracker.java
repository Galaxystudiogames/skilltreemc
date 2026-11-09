package org.langebe.skilltreemc.mechanics;

import net.minecraft.server.level.ServerPlayer;
import org.langebe.skilltreemc.SkillTreeConfig;
import org.langebe.skilltreemc.core.Cooldowns;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers when a player last dealt or took damage from a living attacker, so other
 * mechanics can ask "is this player in combat?" (used by Swift Feet).
 */
public final class CombatTracker {

    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();

    private CombatTracker() {
    }

    public static void tag(ServerPlayer player) {
        LAST_COMBAT.put(player.getUUID(), Cooldowns.now(player));
    }

    public static boolean inCombat(ServerPlayer player) {
        Long last = LAST_COMBAT.get(player.getUUID());
        return last != null && Cooldowns.now(player) - last < Math.round(SkillTreeConfig.COMBAT_TAG_SECONDS.get() * 20);
    }

    public static void forget(ServerPlayer player) {
        LAST_COMBAT.remove(player.getUUID());
    }
}
