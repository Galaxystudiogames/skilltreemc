package org.langebe.skilltreemc.mechanics;

import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.langebe.skilltreemc.SkillTreeConfig;
import org.langebe.skilltreemc.core.Cooldowns;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.core.Stat;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Combat mechanics: crits, damage reduction, Combat Sense, Stun and Living Immortal (Combat),
 * plus the combat parts of the Utils perks Ambush, Fortify, Ghost Walk and Grapple.
 *
 * <ul>
 *   <li>{@link #modifyDamage} is called by {@code mixin/LivingEntityMixin} before damage
 *       is applied and changes the amount (crit / reduction / Shield). Messages and other side
 *       effects of these procs wait until the hit has really landed.</li>
 *   <li>The Fabric damage/death events handle everything that happens after a hit.</li>
 * </ul>
 * Crits, Stun, Combat Sense and the Rage extension only trigger on <b>melee</b> hits.
 */
public final class CombatMechanics {

    private static final String STUN_COOLDOWN = "stun";
    private static final String IMMORTAL_COOLDOWN = "living_immortal";

    private CombatMechanics() {
    }

    /**
     * Procs rolled in {@link #modifyDamage} for a hit that is still being processed. Their side
     * effects (messages, particles, Ambush poison, auto-Fortify) only run in {@link #afterDamage},
     * i.e. once vanilla has decided the hit really lands – {@code hurtServer} may still reject it
     * (invulnerability frames, shield, invulnerable target, ...). Cleared every tick.
     */
    private static final Map<LivingEntity, PendingHit> PENDING = new IdentityHashMap<>();

    private record PendingHit(DamageSource source, ServerPlayer attacker, boolean crit, double critMultiplier,
                              boolean rage, double ambushBonus, boolean reduced, double reductionPercent,
                              boolean shield) {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(CombatMechanics::afterDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register(CombatMechanics::allowDeath);
        ServerTickEvents.END_SERVER_TICK.register(server -> PENDING.clear());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CombatTracker.forget(handler.player));
    }

    /**
     * Called from the mixin at the start of {@code hurtServer} with the raw damage amount.
     * Returns the new amount. Has no side effects – see {@link #PENDING}.
     */
    public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0 || victim.level().isClientSide() || !victim.isAlive()) {
            return amount;
        }
        float result = amount;

        ServerPlayer attacker = null;
        boolean crit = false;
        double critMultiplier = 0;
        boolean rage = false;
        double ambush = 0;

        // --- Attacker side: crit, Ambush, Fortify bonus --------------------------------
        if (isMelee(source) && source.getEntity() instanceof ServerPlayer player) {
            attacker = player;
            rage = ActiveAbilities.isActive(attacker, ActiveAbilities.Ability.RAGE);
            double critRate = rage ? 1.0 : SkillManager.getStat(attacker, Stat.CRIT_RATE);
            if (critRate > 0 && attacker.getRandom().nextDouble() < critRate) {
                crit = true;
                critMultiplier = SkillManager.getStat(attacker, Stat.CRIT_MULTIPLIER);
                result *= (float) (1 + critMultiplier);
            }
            ambush = UtilityPassives.ambushBonus(attacker, crit);
            result *= (float) (1 + ambush);
            result *= (float) (1 + UtilityPassives.fortifyDamageBonus(attacker));
        }

        // --- Victim side: reduction / Shield / Fortify ---------------------------------
        boolean reduced = false;
        double percent = 0;
        boolean shield = false;
        if (victim instanceof ServerPlayer player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            shield = ActiveAbilities.isActive(player, ActiveAbilities.Ability.SHIELD);
            double rate = shield ? 1.0 : SkillManager.getStat(player, Stat.REDUCTION_RATE);
            percent = Math.min(1.0, SkillManager.getStat(player, Stat.REDUCTION_PERCENT)
                    + (shield ? SkillTreeConfig.SHIELD_EXTRA_REDUCTION.get() : 0));
            if (rate > 0 && percent > 0 && player.getRandom().nextDouble() < rate) {
                reduced = true;
                result *= (float) (1 - percent);
            }
            result *= (float) (1 - UtilityPassives.fortifyReduction(player));
        }

        if (crit || ambush > 0 || reduced) {
            PENDING.put(victim, new PendingHit(source, attacker, crit, critMultiplier, rage, ambush,
                    reduced, percent, shield));
        }
        return result;
    }

    /** Side effects of the procs rolled in {@link #modifyDamage}, once the hit has landed. */
    private static void applyProcs(LivingEntity victim, PendingHit hit) {
        if (hit.attacker != null) {
            if (hit.crit) {
                Fx.crit(hit.attacker, victim);
                // During Rage every hit crits – no message spam then.
                if (!hit.rage) {
                    Feedback.proc(hit.attacker, "✦ CRIT +" + Math.round(hit.critMultiplier * 100) + "%", ChatFormatting.RED);
                }
            }
            if (hit.ambushBonus > 0) {
                UtilityPassives.consumeAmbush(hit.attacker, victim, hit.ambushBonus);
            }
        }
        if (victim instanceof ServerPlayer player) {
            if (hit.crit) {
                UtilityPassives.onCritReceived(player);
            }
            if (hit.reduced) {
                Fx.reduction(player);
                // Shield blocks every hit – only announce the random procs.
                if (!hit.shield) {
                    Feedback.proc(player, "⛨ BLOCK -" + Math.round(hit.reductionPercent * 100) + "%", ChatFormatting.AQUA);
                }
            }
        }
    }

    private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage,
                                    float damageTaken, boolean blocked) {
        PendingHit hit = PENDING.remove(victim);
        if (hit != null && hit.source == source && !blocked) {
            applyProcs(victim, hit);
        }
        // Combat tag for Swift Feet.
        if (victim instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity) {
            CombatTracker.tag(player);
        }
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
            CombatTracker.tag(attacker);
            if (isMelee(source) && !blocked) {
                onMeleeHit(attacker, victim);
                UtilityPassives.onAttack(attacker);
                if (victim instanceof Player) {
                    UtilityActives.onPlayerHit(attacker);
                }
            }
        }
        // Living Immortal: below 2 hearts after the hit.
        if (victim instanceof ServerPlayer player && player.isAlive()
                && player.getHealth() < SkillTreeConfig.IMMORTAL_HEALTH.get()) {
            tryLivingImmortal(player);
        }
    }

    private static void onMeleeHit(ServerPlayer attacker, LivingEntity victim) {
        // Combat Sense: hit player glows.
        double glow = SkillManager.getStat(attacker, Stat.GLOW_SECONDS);
        if (glow > 0 && victim instanceof Player) {
            if (!victim.hasEffect(MobEffects.GLOWING)) {
                Feedback.proc(attacker, "◉ MARKED", ChatFormatting.GOLD);
            }
            victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) Math.round(glow * 20), 0, false, false, true));
            Fx.combatSense(victim);
        }

        // Stun with internal cooldown per attacker (no chain stun).
        double stunChance = SkillManager.getStat(attacker, Stat.STUN_CHANCE);
        if (stunChance > 0 && Cooldowns.isReady(attacker, STUN_COOLDOWN)
                && attacker.getRandom().nextDouble() < stunChance) {
            Stun.apply(victim, SkillTreeConfig.STUN_SECONDS.get());
            Feedback.proc(attacker, "⚡ STUN", ChatFormatting.YELLOW);
            Cooldowns.start(attacker, STUN_COOLDOWN, SkillTreeConfig.STUN_COOLDOWN.get());
        }

        ActiveAbilities.onHit(attacker);
    }

    /** Lethal hit: Living Immortal works like a better totem (and, like a totem, not against /kill or the void). */
    private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        if (entity instanceof ServerPlayer player && tryLivingImmortal(player)) {
            player.setHealth(1.0f);
            return false; // cancel death
        }
        return true;
    }

    private static boolean tryLivingImmortal(ServerPlayer player) {
        double chance = SkillManager.getStat(player, Stat.IMMORTAL_CHANCE);
        if (chance <= 0 || !Cooldowns.isReady(player, IMMORTAL_COOLDOWN)
                || player.getRandom().nextDouble() >= chance) {
            return false;
        }
        int duration = (int) Math.round(SkillTreeConfig.IMMORTAL_SECONDS.get() * 20);
        int amplifier = SkillTreeConfig.IMMORTAL_AMPLIFIER.getInt();
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, amplifier));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, amplifier));
        Cooldowns.start(player, IMMORTAL_COOLDOWN, SkillTreeConfig.IMMORTAL_COOLDOWN.get());
        Fx.livingImmortal(player);
        Feedback.proc(player, "✚ LIVING IMMORTAL", ChatFormatting.GOLD);
        return true;
    }

    /** Direct melee hit by a player (no arrows, spells, thorns, ...). */
    private static boolean isMelee(DamageSource source) {
        return source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == source.getEntity();
    }
}
