package org.langebe.skilltreemc.spell;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/** Targeting and particle helpers shared by the spells. */
public final class SpellUtil {

    private SpellUtil() {
    }

    /** Living players (no spectators) within {@code radius} of {@code center}. */
    public static List<ServerPlayer> playersAround(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(ServerPlayer.class, box(center, radius),
                player -> player.isAlive() && !player.isSpectator() && player.position().distanceTo(center) <= radius);
    }

    /**
     * {@code true} if {@code caster} may hit {@code target} with a harmful effect (stun, blindness,
     * hex, ...). Respects the server/world PvP setting, teams with friendly fire off, Creative mode
     * and invulnerable entities – the same rules vanilla uses for a melee hit.
     */
    public static boolean canHarm(ServerPlayer caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isSpectator() || target.isInvulnerable()) {
            return false;
        }
        if (target instanceof Player player) {
            return !player.isCreative() && caster.canHarmPlayer(player);
        }
        return true;
    }

    /**
     * {@code true} if {@code other} counts as an ally of {@code caster} for heals and buffs:
     * the caster, a team mate, or – when neither player is in a team – every player.
     */
    public static boolean isAlly(ServerPlayer caster, ServerPlayer other) {
        if (other == caster) {
            return true;
        }
        if (caster.getTeam() == null && other.getTeam() == null) {
            return true;
        }
        return caster.isAlliedTo(other);
    }

    /** Allies of {@code caster} (see {@link #isAlly}) within {@code radius} of {@code center}. */
    public static List<ServerPlayer> alliesAround(ServerPlayer caster, ServerLevel level, Vec3 center, double radius) {
        return playersAround(level, center, radius).stream().filter(other -> isAlly(caster, other)).toList();
    }

    /** All living entities (no spectators) within {@code radius} of {@code center}. */
    public static List<LivingEntity> livingAround(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, box(center, radius),
                entity -> entity.isAlive() && !entity.isSpectator() && entity.position().distanceTo(center) <= radius);
    }

    /**
     * First entity the caster looks at within {@code range} (blocks stop the ray).
     *
     * @return the entity or {@code null}
     */
    public static <T extends Entity> T lookTarget(ServerPlayer caster, double range, Class<T> type) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = lookEnd(caster, range);
        Predicate<Entity> filter = entity -> type.isInstance(entity) && entity != caster
                && entity.isAlive() && !entity.isSpectator();
        AABB area = caster.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, end, area, filter, eye.distanceToSqr(end));
        return hit == null ? null : type.cast(hit.getEntity());
    }

    /** Point where the caster's view hits a block, or the point at max range. */
    public static Vec3 lookEnd(ServerPlayer caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        HitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        return block.getType() == HitResult.Type.MISS ? end : block.getLocation();
    }

    /** Particles along a line. */
    public static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double spacing) {
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) (length / spacing));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.lerp(to, (double) i / steps);
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    /** Particles on a horizontal circle. */
    public static void circle(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y + 0.2,
                    center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    private static AABB box(Vec3 center, double radius) {
        return new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
    }
}
