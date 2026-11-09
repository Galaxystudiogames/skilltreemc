package org.langebe.skilltreemc.spell;

import net.minecraft.core.particles.ColorParticleOption;
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
import org.langebe.skilltreemc.mechanics.Feedback;
import org.langebe.skilltreemc.mechanics.Fx;
import org.langebe.skilltreemc.mechanics.Scheduler;

import java.util.List;

/**
 * Animations of the bigger spells (L8+ and the ultimates).
 *
 * <p>All effects are particles and sounds sent by the server, animated over several
 * ticks with the {@link Scheduler}. Spells that hit after a wind-up (Demon Shockwave,
 * Hex of Corruption) take a callback that runs at the moment of impact.
 */
public final class SpellFx {

    private static final DustParticleOptions GOLD = Fx.dust(0xFFD54F, 1.4f);
    private static final DustParticleOptions WHITE = Fx.dust(0xFFFFFF, 1.2f);
    private static final DustParticleOptions HOLY = Fx.dust(0xFFF3B0, 1.0f);
    private static final DustParticleOptions CURSE = Fx.dust(0x6A1B9A, 1.6f);
    private static final DustParticleOptions CURSE_DARK = Fx.dust(0x1A0026, 1.8f);
    private static final DustParticleOptions DEMON_RED = Fx.dust(0xB71C1C, 1.8f);
    private static final DustParticleOptions DEMON_BLACK = Fx.dust(0x111111, 2.0f);
    private static final DustParticleOptions SPEED_BLUE = Fx.dust(0x7CAFC6, 1.1f);
    private static final DustParticleOptions STRENGTH_RED = Fx.dust(0x932423, 1.1f);
    private static final DustParticleOptions FIRE_ORANGE = Fx.dust(0xFF9900, 1.1f);
    private static final DustParticleOptions TOXIC_GREEN = Fx.dust(0x7CB342, 1.5f);
    private static final DustParticleOptions TOXIC_BROWN = Fx.dust(0x5D4037, 1.5f);

    private SpellFx() {
    }

    // =================================================================================
    // Healer
    // =================================================================================

    /**
     * Sanctum: pillar of light, a golden rune circle (ring + pentagram) drawing itself on
     * the ground, halos and pulsing light connections to every ally for the whole duration.
     */
    public static void sanctum(ServerPlayer caster, List<ServerPlayer> allies, double radius, int seconds) {
        ServerLevel level = caster.level();
        Vec3 center = caster.position().add(0, 0.1, 0);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE08A), center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
        column(level, ParticleTypes.END_ROD, center, 10, 0.25);
        sound(level, center, SoundEvents.BELL_BLOCK, 1.5f, 0.8f);
        sound(level, center, SoundEvents.BELL_RESONATE, 1.0f, 1.0f);

        // Rune circle draws itself in 10 ticks, then the pentagram appears.
        Scheduler.repeat(1, 10, step -> {
            double from = 2 * Math.PI * step / 10;
            double to = 2 * Math.PI * (step + 1) / 10;
            arc(level, GOLD, center, radius, from, to, (int) (radius * 3));
            arc(level, HOLY, center, radius * 0.55, -from, -to, (int) (radius * 2));
            if (step == 9) {
                pentagram(level, GOLD, center, radius * 0.95, 0);
            }
        });

        // Connections + halos pulse every half second while the buff lasts.
        Scheduler.repeat(10, seconds * 2, pulse -> {
            if (caster.isRemoved()) {
                return;
            }
            Vec3 from = caster.position().add(0, 1.2, 0);
            Fx.ring(level, ParticleTypes.GLOW, caster.position().add(0, 0.1, 0), radius * (0.3 + (pulse % 4) * 0.2), 40);
            for (ServerPlayer ally : allies) {
                if (ally.isRemoved() || !ally.isAlive()) {
                    continue;
                }
                Fx.ring(level, GOLD, ally.position().add(0, ally.getBbHeight() + 0.35, 0), 0.35, 10);
                if (ally != caster) {
                    SpellUtil.line(level, pulse % 2 == 0 ? ParticleTypes.END_ROD : HOLY, from,
                            ally.position().add(0, 1.2, 0), 0.6);
                }
            }
        });

        // Pillars of light on every ally.
        for (ServerPlayer ally : allies) {
            column(level, HOLY, ally.position(), 4, 0.3);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, ally.getX(), ally.getY() + 1, ally.getZ(), 15, 0.3, 0.6, 0.3, 0.2);
        }
    }

    /**
     * Angels Circle: light wings behind the caster, two counter-rotating rings that grow to
     * the full radius and white light falling down inside the circle.
     */
    public static void angelsCircle(ServerPlayer caster, double radius) {
        ServerLevel level = caster.level();
        Vec3 center = caster.position().add(0, 0.15, 0);
        sound(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 1.2f);
        sound(level, center, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 1.0f, 0.8f);

        Scheduler.repeat(1, 40, step -> {
            if (caster.isRemoved()) {
                return;
            }
            // Wings flap slowly for 2 seconds.
            if (step % 2 == 0) {
                wings(caster, Math.sin(step * 0.3) * 0.25);
            }
            // Rings grow during the first 10 ticks, then keep rotating.
            double r = radius * Math.min(1.0, (step + 1) / 10.0);
            double rotation = step * 0.15;
            if (step % 2 == 0) {
                arc(level, ParticleTypes.END_ROD, center, r, rotation, rotation + Math.PI, (int) (r * 3) + 6);
                arc(level, WHITE, center, r * 0.7, -rotation, -rotation + Math.PI, (int) (r * 2) + 6);
            }
            // Falling light.
            for (int i = 0; i < 3; i++) {
                double angle = level.getRandom().nextDouble() * 2 * Math.PI;
                double dist = Math.sqrt(level.getRandom().nextDouble()) * radius;
                level.sendParticles(ParticleTypes.SNOWFLAKE, center.x + Math.cos(angle) * dist, center.y + 4,
                        center.z + Math.sin(angle) * dist, 0, 0, -1, 0, 0.15);
            }
        });
    }

    /** Buff Shower: coloured "drops" in the buff colours rain down in the zone. */
    public static void buffShowerPulse(ServerLevel level, Vec3 center, double radius, boolean plus) {
        int drops = plus ? 45 : 30;
        for (int i = 0; i < drops; i++) {
            double angle = level.getRandom().nextDouble() * 2 * Math.PI;
            double dist = Math.sqrt(level.getRandom().nextDouble()) * radius;
            DustParticleOptions color = switch (i % 3) {
                case 0 -> SPEED_BLUE;
                case 1 -> STRENGTH_RED;
                default -> FIRE_ORANGE;
            };
            level.sendParticles(color, center.x + Math.cos(angle) * dist, center.y + 3.5 + level.getRandom().nextDouble(),
                    center.z + Math.sin(angle) * dist, 0, 0, -1, 0, 0.3);
        }
        if (plus) {
            Fx.ring(level, GOLD, center.add(0, 0.1, 0), radius, 48);
        }
        sound(level, center, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.6f, 1.4f);
    }

    // =================================================================================
    // Damage
    // =================================================================================

    /** Firebolt impact: fire burst, lava sparks; bigger for the advanced version. */
    public static void fireboltImpact(ServerLevel level, Vec3 pos, boolean advanced) {
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, advanced ? 35 : 18, 0.3, 0.3, 0.3, 0.08);
        level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, advanced ? 8 : 3, 0.2, 0.2, 0.2, 0);
        if (advanced) {
            level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            sound(level, pos, SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
        }
    }

    /** Dixiklo: toxic bubbles rise from the cloud for its whole lifetime. */
    public static void dixiklo(ServerLevel level, Vec3 point, double radius, int lifetimeTicks) {
        sound(level, point, SoundEvents.PUFFER_FISH_BLOW_UP, 1.0f, 0.6f);
        sound(level, point, SoundEvents.SLIME_SQUISH, 1.0f, 0.5f);
        Scheduler.repeat(5, Math.max(1, lifetimeTicks / 5), step -> {
            for (int i = 0; i < 6; i++) {
                double angle = level.getRandom().nextDouble() * 2 * Math.PI;
                double dist = Math.sqrt(level.getRandom().nextDouble()) * radius;
                level.sendParticles(i % 2 == 0 ? TOXIC_GREEN : TOXIC_BROWN, point.x + Math.cos(angle) * dist,
                        point.y + 0.1, point.z + Math.sin(angle) * dist, 0, 0, 1, 0, 0.08);
            }
            level.sendParticles(ParticleTypes.ITEM_SLIME, point.x, point.y + 0.3, point.z, 2, radius / 2, 0.1, radius / 2, 0);
            if (step % 4 == 0) {
                sound(level, point, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.5f, 0.6f);
            }
        });
    }

    /**
     * Demon Shockwave wind-up (0.75 s): dark energy is sucked into the caster, then
     * {@code blast} runs together with the explosion animation. If the caster dies, logs out
     * or changes dimension during the wind-up, {@code onAbort} runs once instead.
     */
    public static void demonShockwave(ServerPlayer caster, double radius, Runnable blast, Runnable onAbort) {
        ServerLevel level = caster.level();
        sound(level, caster.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.5f, 0.8f);
        int chargeTicks = 15;
        boolean[] aborted = {false};
        Scheduler.repeat(1, chargeTicks + 1, step -> {
            if (aborted[0]) {
                return;
            }
            if (caster.isRemoved() || !caster.isAlive() || caster.level() != level) {
                aborted[0] = true;
                onAbort.run();
                return;
            }
            Vec3 center = caster.position();
            if (step < chargeTicks) {
                // Particles fly inwards from a ring around the caster.
                for (int i = 0; i < 8; i++) {
                    double angle = level.getRandom().nextDouble() * 2 * Math.PI;
                    double dx = Math.cos(angle) * 4;
                    double dz = Math.sin(angle) * 4;
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x + dx, center.y + 1, center.z + dz,
                            0, -dx, 0, -dz, 0.12);
                }
                level.sendParticles(step % 2 == 0 ? DEMON_RED : DEMON_BLACK, center.x, center.y + 1, center.z,
                        6, 0.4, 0.6, 0.4, 0);
                if (step % 5 == 0) {
                    sound(level, center, SoundEvents.WARDEN_HEARTBEAT, 1.2f, 1.0f + step * 0.03f);
                }
                return;
            }
            // Blast
            blast.run();
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFB71C1C), center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
            sound(level, center, SoundEvents.WARDEN_SONIC_BOOM, 2.0f, 0.6f);
            sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.6f);
            Scheduler.repeat(2, 10, ringStep -> {
                double r = radius * (ringStep + 1) / 10.0;
                int points = (int) (r * 5) + 10;
                Fx.ring(level, ParticleTypes.SCULK_SOUL, center.add(0, 0.2, 0), r, points);
                Fx.ring(level, ringStep % 2 == 0 ? DEMON_RED : DEMON_BLACK, center.add(0, 0.6, 0), r, points);
                Fx.ring(level, ParticleTypes.FLAME, center.add(0, 0.1, 0), r * 0.9, points / 2);
            });
        });
    }

    /**
     * Hex of Corruption: dark vortex at the caster, a curse orb flies to the target, then
     * {@code onHit} runs with a purple flash and a soul explosion. If the caster or target dies,
     * logs out, changes dimension or becomes unhittable (PvP off, ...) on the way, {@code onAbort}
     * runs once instead.
     */
    public static void hexCurse(ServerPlayer caster, ServerPlayer target, Runnable onHit, Runnable onAbort) {
        ServerLevel level = caster.level();
        sound(level, caster.position(), SoundEvents.EVOKER_PREPARE_ATTACK, 1.2f, 0.6f);
        // Vortex around the caster.
        Scheduler.repeat(1, 10, step -> {
            for (int strand = 0; strand < 3; strand++) {
                double angle = step * 0.6 + strand * 2 * Math.PI / 3;
                double r = 1.2 - step * 0.08;
                level.sendParticles(strand == 0 ? CURSE_DARK : CURSE, caster.getX() + Math.cos(angle) * r,
                        caster.getY() + 0.2 + step * 0.18, caster.getZ() + Math.sin(angle) * r, 1, 0, 0, 0, 0);
            }
        });
        // Curse orb travels to the (moving) target in 12 ticks.
        int flightTicks = 12;
        boolean[] aborted = {false};
        Scheduler.later(10, () -> Scheduler.repeat(1, flightTicks + 1, step -> {
            if (aborted[0]) {
                return;
            }
            if (caster.isRemoved() || !caster.isAlive() || caster.level() != level
                    || target.isRemoved() || target.level() != level || !SpellUtil.canHarm(caster, target)) {
                aborted[0] = true;
                onAbort.run();
                return;
            }
            Vec3 from = caster.getEyePosition();
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
            if (step < flightTicks) {
                Vec3 orb = from.lerp(to, (double) (step + 1) / flightTicks);
                level.sendParticles(CURSE, orb.x, orb.y, orb.z, 6, 0.12, 0.12, 0.12, 0);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, orb.x, orb.y, orb.z, 2, 0.05, 0.05, 0.05, 0.01);
                level.sendParticles(ParticleTypes.SMOKE, orb.x, orb.y, orb.z, 1, 0.05, 0.05, 0.05, 0);
                return;
            }
            onHit.run();
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF6A1B9A), to.x, to.y, to.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SCULK_SOUL, to.x, to.y, to.z, 30, 0.4, 0.6, 0.4, 0.08);
            level.sendParticles(CURSE_DARK, to.x, to.y, to.z, 30, 0.5, 0.7, 0.5, 0);
            Scheduler.repeat(2, 5, ring -> Fx.ring(level, ParticleTypes.SQUID_INK, target.position().add(0, 0.1, 0),
                    0.5 + ring * 0.5, 14 + ring * 4));
            sound(level, to, SoundEvents.WITHER_SHOOT, 1.0f, 0.6f);
            Feedback.sound(target, SoundEvents.ELDER_GUARDIAN_CURSE, 1.0f);
        }));
    }

    // =================================================================================
    // Shapes
    // =================================================================================

    /** Part of a horizontal circle from angle {@code from} to {@code to} (radians). */
    private static void arc(ServerLevel level, ParticleOptions particle, Vec3 center, double radius,
                            double from, double to, int points) {
        for (int i = 0; i <= points; i++) {
            double angle = from + (to - from) * i / Math.max(1, points);
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y,
                    center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Five-pointed star on the ground. */
    private static void pentagram(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, double rotation) {
        Vec3[] points = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double angle = rotation - Math.PI / 2 + i * 2 * Math.PI / 5;
            points[i] = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        }
        for (int i = 0; i < 5; i++) {
            SpellUtil.line(level, particle, points[i], points[(i + 2) % 5], 0.35);
        }
    }

    /** Vertical column of particles. */
    private static void column(ServerLevel level, ParticleOptions particle, Vec3 base, double height, double spacing) {
        for (double y = 0; y <= height; y += spacing) {
            level.sendParticles(particle, base.x, base.y + y, base.z, 1, 0.05, 0, 0.05, 0);
        }
    }

    /** Two light wings on the caster's back; {@code flap} tilts them up/down a little. */
    private static void wings(LivingEntity entity, double flap) {
        ServerLevel level = (ServerLevel) entity.level();
        double yaw = Math.toRadians(entity.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 back = entity.position().add(0, 1.35, 0).subtract(forward.scale(0.35));
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i <= 10; i++) {
                double t = i / 10.0;
                double out = 0.25 + t * 1.5;
                double up = Math.sin(t * Math.PI) * 0.55 + t * flap - t * 0.2;
                Vec3 p = back.add(right.scale(side * out)).add(0, up, 0).subtract(forward.scale(t * 0.3));
                level.sendParticles(WHITE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                // Feather tips
                if (i % 3 == 0) {
                    level.sendParticles(HOLY, p.x, p.y - 0.25 - t * 0.3, p.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
