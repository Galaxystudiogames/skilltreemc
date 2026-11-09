package org.langebe.skilltreemc.mechanics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.langebe.skilltreemc.SkillTreeConfig;
import org.langebe.skilltreemc.core.Cooldowns;
import org.langebe.skilltreemc.data.PlayerState;
import org.langebe.skilltreemc.data.SkillData;
import org.langebe.skilltreemc.spell.Spell;
import org.langebe.skilltreemc.spell.SpellRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sidebar scoreboard that lists every skill/spell of a player that is on cooldown,
 * with the remaining time on the right ({@code Demon Shockwave   1:12}).
 *
 * <p>The scoreboard is <b>per player</b> and only exists on that player's client: it is
 * built from raw scoreboard packets and never added to the server scoreboard. While
 * nothing is on cooldown it is removed, and a sidebar the server itself shows (e.g. from
 * {@code /scoreboard objectives setdisplay sidebar}) is shown again.
 *
 * <p>Can be turned off globally ({@code hud.cooldown_sidebar 0}) or per player
 * ({@code /st cooldowns}).
 *
 * <p>New cooldown keys get their display name in {@link #label}; unknown keys are shown
 * with a prettified key, so nothing breaks if one is forgotten.
 */
public final class CooldownHud {

    private static final String OBJECTIVE_NAME = "skilltreemc_cd";
    private static final int UPDATE_INTERVAL = 10; // ticks
    /** A dummy objective only used to build the packets; never added to a real scoreboard. */
    private static final Objective OBJECTIVE = new Objective(new Scoreboard(), OBJECTIVE_NAME,
            ObjectiveCriteria.DUMMY,
            Component.literal("⌛ Cooldowns").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);

    /** Display names of fixed cooldown keys. Spells ({@code spell.*}) are looked up. */
    private static final Map<String, Label> NAMES = Map.ofEntries(
            Map.entry("ability.rage", new Label("Rage", ChatFormatting.RED)),
            Map.entry("ability.shield", new Label("Shield", ChatFormatting.RED)),
            Map.entry("stun", new Label("Stun", ChatFormatting.RED)),
            Map.entry("living_immortal", new Label("Living Immortal", ChatFormatting.GOLD)),
            Map.entry("grapple", new Label("Grapple", ChatFormatting.AQUA)),
            Map.entry("phase_dash", new Label("Phase Dash", ChatFormatting.AQUA)),
            Map.entry("smoke_screen", new Label("Smoke Screen", ChatFormatting.AQUA)),
            Map.entry("eagle_eye", new Label("Eagle Eye", ChatFormatting.AQUA)),
            Map.entry("tracker", new Label("Tracker", ChatFormatting.AQUA)),
            Map.entry("ghost_walk_invis", new Label("Ghost Walk", ChatFormatting.AQUA)),
            Map.entry("auto_fortify", new Label("Auto-Fortify", ChatFormatting.AQUA)),
            Map.entry("potion_refund", new Label("Potion Mastery", ChatFormatting.AQUA)));

    /** What each player's client currently shows: score owner → rendered line. */
    private static final Map<UUID, Map<String, Line>> SHOWN = new HashMap<>();

    private CooldownHud() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % UPDATE_INTERVAL == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    update(player);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SHOWN.remove(handler.player.getUUID()));
    }

    /** Turns the sidebar on/off for one player. Returns the new state ({@code true} = shown). */
    public static boolean toggle(ServerPlayer player) {
        boolean hide = !SkillData.getState(player).hideCooldowns();
        SkillData.modifyState(player, state -> state.withHideCooldowns(hide));
        update(player);
        return !hide;
    }

    /** Rebuilds the sidebar of one player and sends only what changed. */
    public static void update(ServerPlayer player) {
        PlayerState state = SkillData.getState(player);
        if (!SkillTreeConfig.HUD_COOLDOWN_SIDEBAR.isOn() || state.hideCooldowns()) {
            hide(player);
            return;
        }
        long now = Cooldowns.now(player);
        Map<String, Line> lines = new HashMap<>();
        for (Map.Entry<String, Long> entry : state.cooldowns().entrySet()) {
            long ticks = entry.getValue() - now;
            if (ticks > 0) {
                int seconds = (int) ((ticks + 19) / 20);
                lines.put(entry.getKey(), new Line(label(entry.getKey()), seconds));
            }
        }
        if (lines.isEmpty()) {
            hide(player);
            return;
        }

        Map<String, Line> shown = SHOWN.get(player.getUUID());
        if (shown == null) {
            shown = new HashMap<>();
            SHOWN.put(player.getUUID(), shown);
            player.connection.send(new ClientboundSetObjectivePacket(OBJECTIVE, ClientboundSetObjectivePacket.METHOD_ADD));
        }
        // Re-sent every update: a server sidebar change would otherwise replace ours for good.
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, OBJECTIVE));

        for (String owner : new ArrayList<>(shown.keySet())) {
            if (!lines.containsKey(owner)) {
                shown.remove(owner);
                player.connection.send(new ClientboundResetScorePacket(owner, OBJECTIVE_NAME));
            }
        }
        for (Map.Entry<String, Line> entry : lines.entrySet()) {
            Line line = entry.getValue();
            if (!line.equals(shown.get(entry.getKey()))) {
                shown.put(entry.getKey(), line);
                // Score = seconds left, so the longest cooldown is on top.
                player.connection.send(new ClientboundSetScorePacket(entry.getKey(), OBJECTIVE_NAME, line.seconds,
                        Optional.of(line.label), Optional.of(new FixedFormat(time(line.seconds)))));
            }
        }
    }

    /** Removes our sidebar and shows the server's own sidebar objective again, if any. */
    private static void hide(ServerPlayer player) {
        if (SHOWN.remove(player.getUUID()) == null) {
            return;
        }
        player.connection.send(new ClientboundSetObjectivePacket(OBJECTIVE, ClientboundSetObjectivePacket.METHOD_REMOVE));
        Objective serverSidebar = player.level().getServer().getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (serverSidebar != null) {
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, serverSidebar));
        }
    }

    /** "12s", "1:05" or "1:02:00"; the last 3 seconds are green. */
    private static Component time(int seconds) {
        String text;
        if (seconds < 60) {
            text = seconds + "s";
        } else if (seconds < 3600) {
            text = String.format("%d:%02d", seconds / 60, seconds % 60);
        } else {
            text = String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
        return Component.literal(text).withStyle(seconds <= 3 ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    /** Display name of a cooldown key, e.g. {@code spell.hex} → "Hex of Corruption". */
    private static Component label(String key) {
        if (key.startsWith("spell.")) {
            Spell spell = SpellRegistry.get(key.substring("spell.".length()));
            String name = spell != null ? spell.getName() : prettify(key.substring("spell.".length()));
            return Component.literal(name).withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        // Charges: "grapple.0", "grapple.1" → "Grapple", "Grapple #2"
        String base = key;
        String suffix = "";
        int dot = key.lastIndexOf('.');
        if (dot > 0 && key.substring(dot + 1).chars().allMatch(Character::isDigit) && dot < key.length() - 1) {
            base = key.substring(0, dot);
            int charge = Integer.parseInt(key.substring(dot + 1));
            suffix = charge > 0 ? " #" + (charge + 1) : "";
        }
        Label label = NAMES.get(base);
        if (label == null) {
            return Component.literal(prettify(base) + suffix).withStyle(ChatFormatting.WHITE);
        }
        return Component.literal(label.name + suffix).withStyle(label.color);
    }

    /** "ghost_walk_invis" → "Ghost Walk Invis". */
    private static String prettify(String key) {
        StringBuilder out = new StringBuilder();
        for (String word : key.replace('.', '_').split("_")) {
            if (!word.isEmpty()) {
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    private record Label(String name, ChatFormatting color) {
    }

    private record Line(Component label, int seconds) {
    }
}
