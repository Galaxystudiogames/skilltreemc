package org.langebe.skilltreemc.mechanics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.langebe.skilltreemc.SkillTreeConfig;
import org.langebe.skilltreemc.SkillTreeMC;
import org.langebe.skilltreemc.core.Skill;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.core.SkillRegistry;
import org.langebe.skilltreemc.data.SkillData;
import org.langebe.skilltreemc.data.SkillPath;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Purely cosmetic extras. Each one can be switched off in the config ({@code cosmetics.*}).
 *
 * <ul>
 *   <li><b>Unlock title</b>: the skill name as a big title, path and level as subtitle.</li>
 *   <li><b>Path mastery</b>: unlocking the highest level of a path shows a golden title,
 *       a firework-like burst and (optionally) tells the whole server.</li>
 *   <li><b>Tab list badges</b>: {@code Steve ⚔15★ ✦8} – level per chosen path, ★ = mastered.</li>
 *   <li><b>Mastery aura</b>: slowly circling particles in the colour of every mastered
 *       path. Hidden while invisible (Ghost Walk!) or in spectator mode.</li>
 *   <li><b>Welcome message</b> on join with version and a clickable {@code /st help}.</li>
 * </ul>
 */
public final class Cosmetics {

    /** Last tab name sent per player, so the tab list is only updated when it changed. */
    private static final Map<UUID, Component> TAB_CACHE = new HashMap<>();
    /** Highest level of each path (computed once, the registry is fixed after start). */
    private static final Map<SkillPath, Integer> MAX_LEVEL = new EnumMap<>(SkillPath.class);

    private Cosmetics() {
    }

    public static void register() {
        for (SkillPath path : SkillPath.values()) {
            MAX_LEVEL.put(path, SkillRegistry.byPath(path).stream().mapToInt(Skill::getLevel).max().orElse(0));
        }
        ServerTickEvents.END_SERVER_TICK.register(Cosmetics::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> welcome(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TAB_CACHE.remove(handler.player.getUUID()));
    }

    // ---------------------------------------------------------------------------------
    // Path levels
    // ---------------------------------------------------------------------------------

    /** Highest unlocked level in a path (0 = nothing unlocked). */
    public static int pathLevel(ServerPlayer player, SkillPath path) {
        int level = 0;
        for (String id : SkillData.get(player).unlockedSkills()) {
            Skill skill = SkillRegistry.get(id);
            if (skill != null && skill.getPath() == path) {
                level = Math.max(level, skill.getLevel());
            }
        }
        return level;
    }

    public static boolean isMastered(ServerPlayer player, SkillPath path) {
        int max = MAX_LEVEL.getOrDefault(path, 0);
        return max > 0 && pathLevel(player, path) >= max;
    }

    // ---------------------------------------------------------------------------------
    // Unlock title + mastery
    // ---------------------------------------------------------------------------------

    /** Called after a skill was unlocked through the menu. */
    public static void onUnlock(ServerPlayer player, Skill skill) {
        SkillPath path = skill.getPath();
        boolean mastered = skill.getLevel() >= MAX_LEVEL.getOrDefault(path, Integer.MAX_VALUE);
        if (mastered) {
            celebrateMastery(player, path);
        } else if (SkillTreeConfig.COSMETIC_UNLOCK_TITLE.isOn()) {
            title(player,
                    Component.literal(skill.getName()).withStyle(path.color(), ChatFormatting.BOLD),
                    Component.literal(path.symbol() + " " + path.displayName() + " · Level " + skill.getLevel()
                            + " " + path.symbol()).withStyle(ChatFormatting.GRAY),
                    5, 40, 15);
        }
        refreshTab(player.level().getServer());
    }

    private static void celebrateMastery(ServerPlayer player, SkillPath path) {
        if (SkillTreeConfig.COSMETIC_UNLOCK_TITLE.isOn()) {
            title(player,
                    Component.literal("★ MASTERED ★").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal(path.symbol() + " " + path.displayName() + " " + path.symbol())
                            .withStyle(path.color(), ChatFormatting.BOLD),
                    10, 70, 20);
        }
        ServerLevel level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        // Three firework-like bursts in gold and the path colour.
        DustParticleOptions pathDust = Fx.dust(path.particleColor(), 1.6f);
        DustParticleOptions gold = Fx.dust(0xFFD54F, 1.6f);
        Scheduler.repeat(8, 3, burst -> {
            if (player.isRemoved()) {
                return;
            }
            double y = player.getY() + 2.5 + burst * 0.6;
            level.sendParticles(ParticleTypes.FIREWORK, player.getX(), y, player.getZ(), 40, 0.1, 0.1, 0.1, 0.25);
            level.sendParticles(burst % 2 == 0 ? pathDust : gold, player.getX(), y, player.getZ(), 30, 0.9, 0.9, 0.9, 0);
            level.playSound(null, player.getX(), y, player.getZ(),
                    SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.5f, 0.9f + burst * 0.1f);
        });
        if (SkillTreeConfig.COSMETIC_MASTERY_BROADCAST.isOn()) {
            MutableComponent message = Component.literal("★ ").withStyle(ChatFormatting.GOLD)
                    .append(player.getDisplayName().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal(" has mastered the ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(path.symbol() + " " + path.displayName()).withStyle(path.color(), ChatFormatting.BOLD))
                    .append(Component.literal(" path! ★").withStyle(ChatFormatting.GOLD));
            level.getServer().getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    private static void title(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    // ---------------------------------------------------------------------------------
    // Tab list
    // ---------------------------------------------------------------------------------

    /**
     * Tab list name with path badges, or {@code null} for the vanilla name (badges off or
     * no path chosen). Called from {@code mixin/ServerPlayerMixin}.
     */
    public static Component tabListName(ServerPlayer player) {
        if (!SkillTreeConfig.COSMETIC_TAB_BADGES.isOn()) {
            return null;
        }
        MutableComponent badges = Component.empty();
        boolean any = false;
        for (SkillPath path : SkillPath.values()) {
            if (!SkillManager.isPathChosen(player, path)) {
                continue;
            }
            badges.append(Component.literal(" " + path.symbol() + pathLevel(player, path)).withStyle(path.color()));
            if (isMastered(player, path)) {
                badges.append(Component.literal("★").withStyle(ChatFormatting.GOLD));
            }
            any = true;
        }
        if (!any) {
            return null;
        }
        return player.getDisplayName().copy().append(badges);
    }

    /** Sends changed tab names to everyone. Cheap: only players whose name changed. */
    private static void refreshTab(MinecraftServer server) {
        List<ServerPlayer> changed = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Component name = tabListName(player);
            if (!Objects.equals(name, TAB_CACHE.get(player.getUUID())) || !TAB_CACHE.containsKey(player.getUUID())) {
                TAB_CACHE.put(player.getUUID(), name);
                changed.add(player);
            }
        }
        if (!changed.isEmpty()) {
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                    java.util.EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), changed));
        }
    }

    // ---------------------------------------------------------------------------------
    // Mastery aura
    // ---------------------------------------------------------------------------------

    /** Turns the mastery aura on/off for one player. Returns the new state ({@code true} = shown). */
    public static boolean toggleAura(ServerPlayer player) {
        boolean hide = !SkillData.getState(player).hideAura();
        SkillData.modifyState(player, state -> state.withHideAura(hide));
        return !hide;
    }

    private static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        // Tab list: catches admin unlock/reset, config changes, path choices.
        if (tick % 40 == 0) {
            refreshTab(server);
        }
        if (tick % 4 != 0 || !SkillTreeConfig.COSMETIC_MASTERY_AURA.isOn()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || player.isInvisible()
                    || SkillData.getState(player).hideAura()) {
                continue;
            }
            int index = 0;
            for (SkillPath path : SkillPath.values()) {
                if (isMastered(player, path)) {
                    aura(player, path, tick, index++);
                }
            }
        }
    }

    /** Two particles circling the feet; one per mastered path, evenly offset. */
    private static void aura(ServerPlayer player, SkillPath path, int tick, int index) {
        ServerLevel level = player.level();
        DustParticleOptions dust = Fx.dust(path.particleColor(), 0.9f);
        double base = tick * 0.12 + index * (2 * Math.PI / 3);
        for (int i = 0; i < 2; i++) {
            double angle = base + i * Math.PI;
            double y = player.getY() + 0.15 + 0.1 * Math.sin(tick * 0.1 + index);
            level.sendParticles(dust, player.getX() + Math.cos(angle) * 0.7, y,
                    player.getZ() + Math.sin(angle) * 0.7, 1, 0, 0, 0, 0);
        }
        if (player.getRandom().nextInt(6) == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(),
                    1, 0.3, 0.1, 0.3, 0.01);
        }
    }

    // ---------------------------------------------------------------------------------
    // Welcome message
    // ---------------------------------------------------------------------------------

    private static void welcome(ServerPlayer player) {
        if (!SkillTreeConfig.COSMETIC_WELCOME.isOn()) {
            return;
        }
        Component help = Component.literal("[/st help]").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/st help"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Show all commands"))));
        Component open = Component.literal("[/st open]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/st open"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open your skill tree"))));
        player.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal("  ✦ Skill Tree MC ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("v" + SkillTreeMC.version()).withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal("  ")
                .append(Component.literal("⚔ Combat  ").withStyle(SkillPath.COMBAT.color()))
                .append(Component.literal("➹ Utils  ").withStyle(SkillPath.UTILITY.color()))
                .append(Component.literal("✦ Support").withStyle(SkillPath.SUPPORT.color())));
        player.sendSystemMessage(Component.literal("  XP levels are your skill points. ").withStyle(ChatFormatting.GRAY)
                .append(open).append(Component.literal(" ")).append(help));
        player.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
    }
}
