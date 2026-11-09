package org.langebe.skilltreemc.core;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.langebe.skilltreemc.SkillTreeConfig;
import org.langebe.skilltreemc.SkillTreeMC;
import org.langebe.skilltreemc.data.PlayerSkills;
import org.langebe.skilltreemc.data.SkillData;
import org.langebe.skilltreemc.data.SkillPath;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Game logic of the skill tree: unlocking (incl. paying XP), switching skills on/off,
 * reading stats and calling the skill hooks at the right time.
 *
 * <p>Use this class from menus, commands and mechanics. It keeps the stored data
 * ({@link SkillData}) and the actual effects in sync.
 */
public final class SkillManager {

    /** Result of an unlock attempt. */
    public enum UnlockResult {
        SUCCESS,
        ALREADY_UNLOCKED,
        MISSING_REQUIREMENTS,
        /** Another skill of the same exclusive group (other branch / choice) is unlocked. */
        EXCLUDED,
        NOT_ENOUGH_XP,
        PATH_LIMIT_REACHED
    }

    /** Health read from the player file, until the join event restored it (see {@link #registerEvents}). */
    private static final Map<UUID, Float> SAVED_HEALTH = new HashMap<>();

    private SkillManager() {
    }

    /** Called by {@code LivingEntityMixin} when a player's save data is loaded. */
    public static void rememberSavedHealth(ServerPlayer player, float health) {
        if (health > 0) {
            SAVED_HEALTH.put(player.getUUID(), health);
        }
    }

    /** Registers the events that keep skill effects running. Called once at mod init. */
    public static void registerEvents() {
        // Transient effects (attribute modifiers, ...) are not saved -> re-apply on join.
        // Vanilla loads the health before our max-health bonuses exist and caps it at 20,
        // so the saved value is restored once the bonuses are back.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            enableAll(player);
            Float saved = SAVED_HEALTH.remove(player.getUUID());
            if (saved != null && player.isAlive() && saved > player.getHealth()) {
                player.setHealth(Math.min(saved, player.getMaxHealth()));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SAVED_HEALTH.remove(handler.player.getUUID()));

        // After death a new player entity is created. The data is copied (copyOnDeath),
        // but the effects have to be applied again. The new entity got its health while the
        // bonuses were missing: full health after a death, the old health when returning from the End.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            float oldHealth = oldPlayer.getHealth();
            enableAll(newPlayer);
            newPlayer.setHealth(alive ? Math.min(oldHealth, newPlayer.getMaxHealth()) : newPlayer.getMaxHealth());
        });

        ServerTickEvents.END_SERVER_TICK.register(SkillManager::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        long second = server.getTickCount() / 20;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isAlive()) {
                forEachActive(player, skill -> skill.tick(player, second));
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------------

    public static boolean isUnlocked(ServerPlayer player, Skill skill) {
        return SkillData.get(player).unlockedSkills().contains(skill.getId());
    }

    public static boolean isActive(ServerPlayer player, Skill skill) {
        return SkillData.get(player).activeSkills().contains(skill.getId());
    }

    public static boolean isPathChosen(ServerPlayer player, SkillPath path) {
        return SkillData.get(player).activePaths().contains(path.id());
    }

    /**
     * Value of a stat for the player: the highest value among all active skills, or 0.
     */
    public static double getStat(ServerPlayer player, Stat stat) {
        double best = 0;
        for (Skill skill : activeSkills(player)) {
            Double value = skill.getStats().get(stat);
            if (value != null && value > best) {
                best = value;
            }
        }
        return best;
    }

    /** All stats of the player at once (only stats with a value > 0). */
    public static Map<Stat, Double> getStats(ServerPlayer player) {
        Map<Stat, Double> result = new EnumMap<>(Stat.class);
        for (Skill skill : activeSkills(player)) {
            skill.getStats().forEach((stat, value) -> result.merge(stat, value, Math::max));
        }
        return result;
    }

    /** All active skills that still exist in the code. */
    public static List<Skill> activeSkills(ServerPlayer player) {
        List<Skill> result = new ArrayList<>();
        for (String id : SkillData.get(player).activeSkills()) {
            Skill skill = SkillRegistry.get(id);
            if (skill != null) { // null = skill was removed from the code; ignore old data
                result.add(skill);
            }
        }
        return result;
    }

    /**
     * @return the required skills the player has not unlocked yet. For {@code requiresAny}
     * all options are returned if none of them is unlocked.
     */
    public static List<Skill> missingRequirements(ServerPlayer player, Skill skill) {
        PlayerSkills data = SkillData.get(player);
        List<Skill> missing = new ArrayList<>();
        for (String id : skill.getRequires()) {
            if (!data.unlockedSkills().contains(id)) {
                missing.add(SkillRegistry.get(id));
            }
        }
        if (!skill.getRequiresAny().isEmpty()
                && skill.getRequiresAny().stream().noneMatch(data.unlockedSkills()::contains)) {
            skill.getRequiresAny().forEach(id -> missing.add(SkillRegistry.get(id)));
        }
        return missing;
    }

    /** @return the skill of the same exclusive group that the player already took, or {@code null}. */
    public static Skill chosenRival(ServerPlayer player, Skill skill) {
        for (Skill rival : SkillRegistry.exclusiveRivals(skill)) {
            if (isUnlocked(player, rival)) {
                return rival;
            }
        }
        return null;
    }

    /** Checks whether the player could unlock the skill right now, without changing anything. */
    public static UnlockResult checkUnlock(ServerPlayer player, Skill skill) {
        if (isUnlocked(player, skill)) {
            return UnlockResult.ALREADY_UNLOCKED;
        }
        if (chosenRival(player, skill) != null) {
            return UnlockResult.EXCLUDED;
        }
        if (!missingRequirements(player, skill).isEmpty()) {
            return UnlockResult.MISSING_REQUIREMENTS;
        }
        PlayerSkills data = SkillData.get(player);
        if (!data.activePaths().contains(skill.getPath().id())
                && data.activePaths().size() >= SkillTreeConfig.MAX_ACTIVE_PATHS.getInt()) {
            return UnlockResult.PATH_LIMIT_REACHED;
        }
        if (!isFree(player) && player.experienceLevel < skill.getCost()) {
            return UnlockResult.NOT_ENOUGH_XP;
        }
        return UnlockResult.SUCCESS;
    }

    // ---------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------

    /** Unlocks the skill if allowed: pays the XP cost, chooses the path and activates the skill. */
    public static UnlockResult tryUnlock(ServerPlayer player, Skill skill) {
        UnlockResult result = checkUnlock(player, skill);
        if (result == UnlockResult.SUCCESS) {
            if (!isFree(player)) {
                player.giveExperienceLevels(-skill.getCost());
            }
            forceUnlock(player, skill);
        }
        return result;
    }

    /** Unlocks and activates the skill without any checks or costs (admin command). */
    public static void forceUnlock(ServerPlayer player, Skill skill) {
        SkillData.unlockSkill(player, skill.getId());
        SkillData.activatePath(player, skill.getPath());
        setActive(player, skill, true);
        // After activation, so onUnlock already sees the skill's stats (e.g. max mana).
        call(skill, "onUnlock", () -> skill.onUnlock(player));
    }

    /** Switches an unlocked skill on or off. Does nothing if the skill is not unlocked. */
    public static void setActive(ServerPlayer player, Skill skill, boolean active) {
        if (!isUnlocked(player, skill)) {
            return;
        }
        if (active) {
            SkillData.activateSkill(player, skill.getId());
            call(skill, "onEnable", () -> skill.enable(player));
        } else {
            SkillData.deactivateSkill(player, skill.getId());
            call(skill, "onDisable", () -> skill.disable(player));
        }
    }

    /** Disables all skills and deletes the player's progress, mana and cooldowns. XP is not refunded. */
    public static void reset(ServerPlayer player) {
        forEachActive(player, skill -> skill.disable(player));
        SkillData.reset(player);
    }

    /** Re-applies every active skill (join / respawn). */
    public static void enableAll(ServerPlayer player) {
        forEachActive(player, skill -> skill.enable(player));
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private static boolean isFree(ServerPlayer player) {
        return SkillTreeConfig.FREE_IN_CREATIVE.isOn() && player.isCreative();
    }

    private static void forEachActive(ServerPlayer player, Consumer<Skill> action) {
        for (Skill skill : activeSkills(player)) {
            call(skill, "hook", () -> action.accept(skill));
        }
    }

    /** Runs skill code and logs exceptions instead of crashing the server. */
    private static void call(Skill skill, String what, Runnable code) {
        try {
            code.run();
        } catch (RuntimeException e) {
            SkillTreeMC.LOGGER.error("Skill '{}' failed in {}", skill.getId(), what, e);
        }
    }
}
