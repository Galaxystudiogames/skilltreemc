package org.langebe.skilltreemc.mechanics;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.langebe.skilltreemc.SkillTreeConfig;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.config.Settings;
import org.langebe.skilltreemc.core.Cooldowns;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.core.Stat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The two active (toggle) abilities of the Combat path, both used with
 * <b>Sneak + Offhand key</b> while <i>not</i> holding the Spell Orb:
 *
 * <ul>
 *   <li><b>Rage</b> (DPS L15): Speed II + 100 % crit rate. Every hit extends the
 *       duration by 80 % of the time since the previous hit.</li>
 *   <li><b>Shield</b> (Tank L15): every hit taken is reduced (100 % reduction rate),
 *       reduction percent +10 %.</li>
 * </ul>
 * Toggling off ends the ability and starts the cooldown immediately; otherwise the
 * cooldown starts when the duration runs out.
 */
public final class ActiveAbilities {

    public enum Ability {
        RAGE("Rage", Stat.RAGE, SkillTreeConfig.RAGE_SECONDS, SkillTreeConfig.RAGE_COOLDOWN),
        SHIELD("Shield", Stat.SHIELD, SkillTreeConfig.SHIELD_SECONDS, SkillTreeConfig.SHIELD_COOLDOWN);

        final String displayName;
        final Stat stat;
        final Setting seconds;
        final Setting cooldown;

        Ability(String displayName, Stat stat, Setting seconds, Setting cooldown) {
            this.displayName = displayName;
            this.stat = stat;
            this.seconds = seconds;
            this.cooldown = cooldown;
        }

        String cooldownKey() {
            return "ability." + name().toLowerCase();
        }
    }

    /** Currently running abilities (not saved – they end on logout). */
    private static final Map<UUID, Running> RUNNING = new HashMap<>();

    private ActiveAbilities() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ActiveAbilities::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (RUNNING.containsKey(handler.player.getUUID())) {
                stop(handler.player, false);
            }
        });
        // Dying ends a running ability (and starts its cooldown) – it must not carry over to the respawn.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player && RUNNING.containsKey(player.getUUID())) {
                stop(player, false);
            }
        });
    }

    public static boolean isActive(ServerPlayer player, Ability ability) {
        Running running = RUNNING.get(player.getUUID());
        return running != null && running.ability == ability;
    }

    /** @return the toggle ability this player has unlocked, or {@code null}. */
    public static Ability unlockedAbility(ServerPlayer player) {
        for (Ability ability : Ability.values()) {
            if (SkillManager.getStat(player, ability.stat) > 0) {
                return ability;
            }
        }
        return null;
    }

    /**
     * Toggles the player's ability.
     *
     * @return {@code false} if the player has no toggle ability (the key press is not ours)
     */
    public static boolean toggle(ServerPlayer player) {
        Ability ability = unlockedAbility(player);
        if (ability == null) {
            return false;
        }
        if (isActive(player, ability)) {
            stop(player, true);
            return true;
        }
        if (Stun.isStunned(player)) {
            Feedback.deny(player, "You are stunned!");
            return true;
        }
        if (!Cooldowns.isReady(player, ability.cooldownKey())) {
            Feedback.deny(player, ability.displayName + " is on cooldown ("
                    + Cooldowns.remainingSeconds(player, ability.cooldownKey()) + "s)");
            return true;
        }
        long now = Cooldowns.now(player);
        RUNNING.put(player.getUUID(), new Running(ability, now + Math.round(ability.seconds.get() * 20), now));
        Feedback.actionBar(player, ability.displayName + " activated!", ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (ability == Ability.RAGE) {
            Fx.rageStart(player);
        } else {
            Fx.shieldStart(player);
        }
        return true;
    }

    /** Rage: extend the duration when the player lands a hit. */
    public static void onHit(ServerPlayer attacker) {
        Running running = RUNNING.get(attacker.getUUID());
        if (running == null || running.ability != Ability.RAGE) {
            return;
        }
        long now = Cooldowns.now(attacker);
        running.endTick += Math.round((now - running.lastHitTick) * SkillTreeConfig.RAGE_EXTENSION.get());
        running.lastHitTick = now;
    }

    private static void stop(ServerPlayer player, boolean notify) {
        Running running = RUNNING.remove(player.getUUID());
        if (running == null) {
            return;
        }
        Cooldowns.start(player, running.ability.cooldownKey(), running.ability.cooldown.get());
        if (running.ability == Ability.RAGE) {
            MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
            if (speed != null && speed.getAmplifier() == SkillTreeConfig.RAGE_SPEED_AMPLIFIER.getInt() && speed.endsWithin(40)) {
                player.removeEffect(MobEffects.SPEED);
            }
        }
        Fx.abilityEnd(player);
        if (notify) {
            Feedback.actionBar(player, running.ability.displayName + " ended – cooldown "
                    + Settings.formatNumber(running.ability.cooldown.get()) + "s", ChatFormatting.GRAY);
        }
        // Ping the player when the ability can be used again.
        UUID id = player.getUUID();
        MinecraftServer server = player.level().getServer();
        String name = running.ability.displayName;
        String key = running.ability.cooldownKey();
        Scheduler.later((int) Math.round(running.ability.cooldown.get() * 20) + 1, () -> {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null && Cooldowns.isReady(online, key) && !RUNNING.containsKey(id)) {
                Fx.ready(online);
                Feedback.actionBar(online, name + " is ready!", ChatFormatting.GREEN);
            }
        });
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        long now = Cooldowns.now(server);
        for (UUID id : new ArrayList<>(RUNNING.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            Running running = RUNNING.get(id);
            if (player == null) {
                RUNNING.remove(id);
                continue;
            }
            // The ability was lost (skill disabled / reset) or the time ran out.
            if (now >= running.endTick || SkillManager.getStat(player, running.ability.stat) <= 0) {
                stop(player, true);
                continue;
            }
            long secondsLeft = (running.endTick - now + 19) / 20;
            Feedback.status(player, running.ability.displayName + ": " + secondsLeft + "s",
                    running.ability == Ability.RAGE ? ChatFormatting.RED : ChatFormatting.AQUA);
            if (running.ability == Ability.RAGE) {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30,
                        SkillTreeConfig.RAGE_SPEED_AMPLIFIER.getInt(), true, false, true));
                Fx.rageTick(player, server.getTickCount());
            } else {
                Fx.shieldTick(player, server.getTickCount());
            }
        }
    }

    private static final class Running {
        final Ability ability;
        long endTick;
        long lastHitTick;

        Running(Ability ability, long endTick, long lastHitTick) {
            this.ability = ability;
            this.endTick = endTick;
            this.lastHitTick = lastHitTick;
        }
    }
}
