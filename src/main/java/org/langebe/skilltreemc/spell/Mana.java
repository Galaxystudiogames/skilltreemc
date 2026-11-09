package org.langebe.skilltreemc.spell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.core.Stat;
import org.langebe.skilltreemc.data.SkillData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mana system of the Support path.
 *
 * <ul>
 *   <li>Max mana and regeneration come from the stats {@link Stat#MAX_MANA} and
 *       {@link Stat#MANA_REGEN} (per second).</li>
 *   <li>The current mana is saved with the player.</li>
 *   <li>A blue boss bar shows the mana to players that have a mana pool.</li>
 * </ul>
 */
public final class Mana {

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private Mana() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Mana::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerBossEvent bar = BARS.remove(handler.player.getUUID());
            if (bar != null) {
                bar.removeAllPlayers();
            }
        });
    }

    public static float get(ServerPlayer player) {
        return SkillData.getState(player).mana();
    }

    public static int max(ServerPlayer player) {
        return (int) SkillManager.getStat(player, Stat.MAX_MANA);
    }

    /** Sets mana (clamped to 0..max) and updates the boss bar. */
    public static void set(ServerPlayer player, float mana) {
        float clamped = Math.max(0, Math.min(mana, max(player)));
        SkillData.modifyState(player, state -> state.withMana(clamped));
        updateBar(player);
    }

    public static boolean tryConsume(ServerPlayer player, int amount) {
        if (get(player) < amount) {
            return false;
        }
        set(player, get(player) - amount);
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int max = max(player);
            if (max <= 0) {
                updateBar(player);
                continue;
            }
            float regen = (float) SkillManager.getStat(player, Stat.MANA_REGEN);
            set(player, get(player) + regen);
        }
    }

    /** Shows, updates or hides the mana boss bar. */
    public static void updateBar(ServerPlayer player) {
        int max = max(player);
        ServerBossEvent bar = BARS.get(player.getUUID());
        if (max <= 0) {
            if (bar != null) {
                bar.removeAllPlayers();
                BARS.remove(player.getUUID());
            }
            return;
        }
        if (bar == null) {
            bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(),
                    BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
            BARS.put(player.getUUID(), bar);
        }
        if (!bar.getPlayers().contains(player)) {
            // After respawn the player object is new -> re-add.
            bar.removeAllPlayers();
            bar.addPlayer(player);
        }
        float mana = get(player);
        bar.setName(Component.literal("Mana " + (int) mana + " / " + max));
        bar.setProgress(Math.max(0f, Math.min(1f, mana / max)));
    }
}
