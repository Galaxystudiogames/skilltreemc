package org.langebe.skilltreemc.mechanics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.langebe.skilltreemc.SkillTreeConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Small helpers for messages and sounds that only one player gets.
 *
 * <p>Three kinds of action-bar text:
 * <ul>
 *   <li>{@link #actionBar}: shown immediately (menus, errors, important states).</li>
 *   <li>{@link #proc}: "a skill just triggered" (crit, stun, block, ...). All procs of one
 *       tick are merged into one line, e.g. {@code ✦ CRIT +50%  ⚡ STUN}, so a crit does
 *       not hide the stun that happened in the same hit.</li>
 *   <li>{@link #status}: repeating timers (Rage/Shield seconds left). They are skipped
 *       while a proc message is still fresh, so procs stay readable.</li>
 * </ul>
 */
public final class Feedback {

    /** Procs collected during the current tick, flushed at the end of the tick. */
    private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();
    /** Server tick at which the last proc message was shown, per player. */
    private static final Map<UUID, Integer> LAST_PROC = new HashMap<>();
    private static int currentTick;

    private Feedback() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Feedback::flush);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PENDING.remove(handler.player.getUUID());
            LAST_PROC.remove(handler.player.getUUID());
        });
    }

    /** Shows a short message above the hotbar. */
    public static void actionBar(ServerPlayer player, String text, ChatFormatting... formatting) {
        player.sendOverlayMessage(Component.literal(text).withStyle(formatting));
    }

    /**
     * Announces that a skill triggered. Merged with other procs of the same tick.
     * Does nothing if {@code hud.proc_messages} is 0.
     */
    public static void proc(ServerPlayer player, String text, ChatFormatting color) {
        if (!SkillTreeConfig.HUD_PROC_MESSAGES.isOn()) {
            return;
        }
        PENDING.computeIfAbsent(player.getUUID(), id -> new Pending(player))
                .parts.add(Component.literal(text).withStyle(color, ChatFormatting.BOLD));
    }

    /** Low-priority action-bar text (timers); skipped while a proc message is visible. */
    public static void status(ServerPlayer player, String text, ChatFormatting... formatting) {
        Integer last = LAST_PROC.get(player.getUUID());
        int hold = (int) Math.round(SkillTreeConfig.HUD_PROC_HOLD.get() * 20);
        if (last != null && currentTick - last < hold) {
            return;
        }
        actionBar(player, text, formatting);
    }

    private static void flush(MinecraftServer server) {
        currentTick = server.getTickCount();
        if (PENDING.isEmpty()) {
            return;
        }
        for (Pending pending : new ArrayList<>(PENDING.values())) {
            if (pending.player.isRemoved()) {
                continue;
            }
            MutableComponent line = Component.empty();
            for (int i = 0; i < pending.parts.size(); i++) {
                if (i > 0) {
                    line.append(Component.literal("   "));
                }
                line.append(pending.parts.get(i));
            }
            pending.player.sendOverlayMessage(line);
            LAST_PROC.put(pending.player.getUUID(), currentTick);
        }
        PENDING.clear();
    }

    /** Sends a chat message. */
    public static void chat(ServerPlayer player, String text, ChatFormatting... formatting) {
        player.sendSystemMessage(Component.literal(text).withStyle(formatting));
    }

    /** Plays a sound that only this player hears. */
    public static void sound(ServerPlayer player, SoundEvent sound, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.MASTER,
                player.getX(), player.getY(), player.getZ(), 1.0f, pitch, player.getRandom().nextLong()));
    }

    /** Short "denied" feedback: red action-bar text plus a sound. */
    public static void deny(ServerPlayer player, String text) {
        actionBar(player, text, ChatFormatting.RED);
        sound(player, net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1.0f);
    }

    private static final class Pending {
        final ServerPlayer player;
        final List<Component> parts = new ArrayList<>();

        Pending(ServerPlayer player) {
            this.player = player;
        }
    }
}
