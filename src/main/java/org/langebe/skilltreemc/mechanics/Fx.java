package org.langebe.skilltreemc.mechanics;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.langebe.skilltreemc.data.SkillPath;

/**
 * All visual and audio feedback ("animations") of the mod in one place.
 *
 * <p>Everything here is vanilla particles and sounds sent from the server, so it works
 * without a client mod and everyone nearby sees it. Effects that last a while are
 * animated with the {@link Scheduler}.
 *
 * <p>Colours used: red = crits / Rage, yellow = Stun, blue = Shield, gold = Living
 * Immortal / Midas, dark purple = Hex, gray = Fortify / Ghost Walk, path colour = unlocks.
 */
public final class Fx {

    private static final DustParticleOptions RED = dust(0xFF1A1A, 1.3f);
    private static final DustParticleOptions DARK_RED = dust(0x8B0000, 1.5f);
    private static final DustParticleOptions YELLOW = dust(0xFFE14D, 1.0f);
    private static final DustParticleOptions BLUE = dust(0x3FA9FF, 1.1f);
    private static final DustParticleOptions LIGHT_BLUE = dust(0xBFE6FF, 0.9f);
    private static final DustParticleOptions GOLD = dust(0xFFC400, 1.2f);
    private static final DustParticleOptions GRAY = dust(0x9E9E9E, 1.0f);
    private static final DustParticleOptions DARK_PURPLE = dust(0x5B1A7A, 1.3f);
    private static final DustParticleOptions MANA = dust(0x7C4DFF, 1.0f);

    private Fx() {
    }

    // =================================================================================
    // Combat
    // =================================================================================

    /** Custom crit: burst of red particles on the victim plus a crit sound. */
    public static void crit(ServerPlayer attacker, LivingEntity victim) {
        ServerLevel level = attacker.level();
        Vec3 body = chest(victim);
        level.sendParticles(RED, body.x, body.y, body.z, 25, 0.35, 0.45, 0.35, 0.05);
        level.sendParticles(ParticleTypes.CRIT, body.x, body.y, body.z, 12, 0.3, 0.4, 0.3, 0.3);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, body.x, body.y + 0.3, body.z, 3, 0.2, 0.2, 0.2, 0.1);
        sound(level, body, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.9f);
        Feedback.sound(attacker, SoundEvents.PLAYER_ATTACK_STRONG, 1.4f);
    }

    /** Damage reduction proc: a light-blue half shell in front of the player. */
    public static void reduction(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 look = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z).normalize();
        Vec3 center = chest(player).add(look.scale(0.6));
        for (int i = -3; i <= 3; i++) {
            double angle = Math.atan2(look.z, look.x) + i * 0.35;
            level.sendParticles(LIGHT_BLUE, player.getX() + Math.cos(angle) * 0.7, center.y,
                    player.getZ() + Math.sin(angle) * 0.7, 2, 0.02, 0.35, 0.02, 0);
        }
        sound(level, center, SoundEvents.SHIELD_BLOCK.value(), 0.7f, 1.4f);
    }

    /** Combat Sense: sparks on the player that starts glowing. */
    public static void combatSense(LivingEntity victim) {
        if (victim.level() instanceof ServerLevel level) {
            Vec3 body = chest(victim);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, body.x, body.y, body.z, 12, 0.3, 0.5, 0.3, 0.05);
        }
    }

    /** Stun applied: yellow flash + anvil sound. */
    public static void stunApply(LivingEntity target) {
        if (target.level() instanceof ServerLevel level) {
            Vec3 head = head(target);
            ring(level, YELLOW, head, 0.6, 16);
            level.sendParticles(ParticleTypes.CRIT, head.x, head.y, head.z, 10, 0.3, 0.1, 0.3, 0.2);
            sound(level, head, SoundEvents.ANVIL_LAND, 0.6f, 1.8f);
        }
    }

    /** While stunned: three yellow "stars" circle above the head. */
    public static void stunTick(LivingEntity target, int tick) {
        if (target.level() instanceof ServerLevel level) {
            Vec3 head = head(target).add(0, 0.25, 0);
            for (int i = 0; i < 3; i++) {
                double angle = tick * 0.35 + i * (2 * Math.PI / 3);
                level.sendParticles(YELLOW, head.x + Math.cos(angle) * 0.45, head.y,
                        head.z + Math.sin(angle) * 0.45, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Living Immortal: totem burst and a golden spiral rising around the player. */
    public static void livingImmortal(ServerPlayer player) {
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
                50, 0.4, 0.7, 0.4, 0.35);
        sound(level, player.position(), SoundEvents.TOTEM_USE, 0.8f, 1.2f);
        spiral(player, GOLD, 20, 2.2);
    }

    /** Rage starts: expanding red shockwave and a roar. */
    public static void rageStart(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 center = player.position();
        sound(level, center, SoundEvents.RAVAGER_ROAR, 1.0f, 1.1f);
        Scheduler.repeat(2, 6, step -> ring(level, RED, center.add(0, 0.2, 0), 0.6 + step * 0.5, 14 + step * 4));
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 2.1, player.getZ(), 3, 0.3, 0.1, 0.3, 0);
    }

    /** While raging (every 10 ticks): red swirl at the feet. */
    public static void rageTick(ServerPlayer player, int tick) {
        ServerLevel level = player.level();
        for (int i = 0; i < 6; i++) {
            double angle = tick * 0.3 + i * Math.PI / 3;
            level.sendParticles(RED, player.getX() + Math.cos(angle) * 0.6, player.getY() + 0.15,
                    player.getZ() + Math.sin(angle) * 0.6, 1, 0, 0.05, 0, 0);
        }
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 2.1, player.getZ(), 1, 0.3, 0.1, 0.3, 0);
    }

    /** Shield starts: blue dome and armour sound. */
    public static void shieldStart(ServerPlayer player) {
        ServerLevel level = player.level();
        sound(level, player.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.8f);
        Vec3 center = player.position();
        for (int layer = 0; layer < 5; layer++) {
            double y = layer * 0.45;
            double radius = Math.sqrt(Math.max(0.05, 1.3 * 1.3 - Math.pow(y - 0.2, 2)));
            ring(level, BLUE, center.add(0, y, 0), radius, 16);
        }
    }

    /** While shielded (every 10 ticks): rotating blue ring at chest height. */
    public static void shieldTick(ServerPlayer player, int tick) {
        ServerLevel level = player.level();
        Vec3 chest = chest(player);
        for (int i = 0; i < 8; i++) {
            double angle = tick * 0.2 + i * Math.PI / 4;
            level.sendParticles(BLUE, chest.x + Math.cos(angle) * 0.8, chest.y,
                    chest.z + Math.sin(angle) * 0.8, 1, 0, 0, 0, 0);
        }
    }

    /** Rage / Shield ends: puff of smoke. */
    public static void abilityEnd(ServerPlayer player) {
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.WHITE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
        sound(level, player.position(), SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.4f);
    }

    /** Cooldown is over (only the player hears/sees it). */
    public static void ready(ServerPlayer player) {
        Feedback.sound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f);
        player.level().sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false,
                player.getX(), player.getY() + 2.2, player.getZ(), 5, 0.3, 0.1, 0.3, 0);
    }

    // =================================================================================
    // Utils perks
    // =================================================================================

    /** Ambush hit: dark red slash. */
    public static void ambush(ServerPlayer attacker, LivingEntity victim) {
        ServerLevel level = attacker.level();
        Vec3 body = chest(victim);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, body.x, body.y, body.z, 1, 0, 0, 0, 0);
        level.sendParticles(DARK_RED, body.x, body.y, body.z, 20, 0.3, 0.4, 0.3, 0.02);
        sound(level, body, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
    }

    /** Fortify becomes active: gray ring at the feet. */
    public static void fortifyOn(ServerPlayer player) {
        ring(player.level(), GRAY, player.position().add(0, 0.1, 0), 0.8, 16);
        Feedback.sound(player, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f);
    }

    /** While fortified (every second): a few gray particles. */
    public static void fortifyTick(ServerPlayer player) {
        player.level().sendParticles(GRAY, player.getX(), player.getY() + 0.1, player.getZ(), 4, 0.4, 0.02, 0.4, 0);
    }

    /** Ghost Walk invisibility starts / ends: puff of smoke. */
    public static void ghostVanish(ServerPlayer player) {
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.8, player.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        sound(level, player.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.6f, 1.3f);
    }

    /** Grapple launch: gust plus a short cloud trail behind the player. */
    public static void grapple(ServerPlayer player) {
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0, 0, 0, 0);
        sound(level, player.position(), SoundEvents.BREEZE_JUMP, 1.0f, 1.2f);
        Scheduler.repeat(2, 10, step -> {
            if (!player.isRemoved()) {
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 2, 0.1, 0.1, 0.1, 0);
            }
        });
    }

    /** Phase Dash: portal bursts at start and end. */
    public static void phaseDash(ServerLevel level, Vec3 from, Vec3 to) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.05);
        level.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1, to.z, 40, 0.3, 0.6, 0.3, 0.6);
        sound(level, to, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.4f);
    }

    /** Eagle Eye / Tracker activated: expanding ring of light. */
    public static void tracking(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 center = player.position().add(0, 0.2, 0);
        sound(level, center, SoundEvents.ENDER_EYE_LAUNCH, 0.8f, 1.2f);
        Scheduler.repeat(2, 6, step -> ring(level, ParticleTypes.END_ROD, center, 0.5 + step * 0.7, 16 + step * 4));
    }

    /** Midas Touch doubled a drop. */
    public static void midas(ServerLevel level, Vec3 pos) {
        level.sendParticles(GOLD, pos.x, pos.y + 0.5, pos.z, 15, 0.3, 0.3, 0.3, 0);
        level.sendParticles(ParticleTypes.WAX_ON, pos.x, pos.y + 0.5, pos.z, 8, 0.3, 0.3, 0.3, 0.5);
        sound(level, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
    }

    /** Scavenger doubled a drop. */
    public static void scavenger(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.5, pos.z, 10, 0.3, 0.3, 0.3, 0);
        sound(level, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.6f);
    }

    /** Potion Mastery kept a potion. */
    public static void potionRefund(ServerPlayer player) {
        player.level().sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.2, player.getZ(), 15, 0.3, 0.4, 0.3, 0.05);
        Feedback.sound(player, SoundEvents.BREWING_STAND_BREW, 1.5f);
    }

    // =================================================================================
    // Support
    // =================================================================================

    /** Every successful spell: mana sparks from the caster's hand. */
    public static void spellCast(ServerPlayer caster) {
        ServerLevel level = caster.level();
        Vec3 hand = caster.getEyePosition().add(caster.getLookAngle().scale(0.6)).add(0, -0.4, 0);
        level.sendParticles(MANA, hand.x, hand.y, hand.z, 12, 0.15, 0.15, 0.15, 0.02);
        level.sendParticles(ParticleTypes.ENCHANT, caster.getX(), caster.getY() + 1, caster.getZ(), 25, 0.5, 0.6, 0.5, 0.6);
    }

    /** While hexed: dark purple swirl rising around the body and a rotating curse ring at the feet. */
    public static void hexTick(LivingEntity target, int tick) {
        if (target.level() instanceof ServerLevel level) {
            Vec3 feet = target.position().add(0, 0.05, 0);
            for (int i = 0; i < 3; i++) {
                double angle = -tick * 0.15 + i * 2 * Math.PI / 3;
                level.sendParticles(DARK_PURPLE, feet.x + Math.cos(angle) * 0.9, feet.y,
                        feet.z + Math.sin(angle) * 0.9, 1, 0, 0, 0, 0);
            }
            if (tick % 20 == 0) {
                ring(level, ParticleTypes.SOUL, feet, 0.9, 12);
            }
            for (int i = 0; i < 4; i++) {
                double angle = tick * 0.25 + i * Math.PI / 2;
                double y = target.getY() + 0.3 + (tick % 20) / 20.0 * target.getBbHeight();
                level.sendParticles(DARK_PURPLE, target.getX() + Math.cos(angle) * 0.5, y,
                        target.getZ() + Math.sin(angle) * 0.5, 1, 0, 0, 0, 0);
            }
        }
    }

    // =================================================================================
    // Skill tree
    // =================================================================================

    /** Skill unlocked: spiral in the path's colour. */
    public static void unlock(ServerPlayer player, SkillPath path) {
        spiral(player, dust(path.particleColor(), 1.1f), 16, 2.0);
        player.level().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
                15, 0.3, 0.5, 0.3, 0.2);
    }

    // =================================================================================
    // Helpers
    // =================================================================================

    public static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(rgb, scale);
    }

    /** Horizontal ring of particles. */
    public static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y,
                    center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Two particle strands spiralling up around the player over {@code ticks} ticks. */
    private static void spiral(ServerPlayer player, ParticleOptions particle, int ticks, double height) {
        ServerLevel level = player.level();
        Scheduler.repeat(1, ticks, step -> {
            if (player.isRemoved()) {
                return;
            }
            double progress = (double) step / ticks;
            for (int strand = 0; strand < 2; strand++) {
                double angle = progress * 4 * Math.PI + strand * Math.PI;
                level.sendParticles(particle, player.getX() + Math.cos(angle) * 0.7, player.getY() + progress * height,
                        player.getZ() + Math.sin(angle) * 0.7, 1, 0, 0, 0, 0);
            }
        });
    }

    private static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static Vec3 chest(LivingEntity entity) {
        return entity.position().add(0, entity.getBbHeight() * 0.6, 0);
    }

    private static Vec3 head(LivingEntity entity) {
        return entity.position().add(0, entity.getBbHeight() + 0.2, 0);
    }
}
