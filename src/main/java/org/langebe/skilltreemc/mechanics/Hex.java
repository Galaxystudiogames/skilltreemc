package org.langebe.skilltreemc.mechanics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hex of Corruption: while hexed, every heal the target receives deals damage instead.
 *
 * <p>The inversion itself happens in {@code mixin/LivingEntityMixin}, which calls
 * {@link #onHeal} at the start of {@code LivingEntity.heal}.
 */
public final class Hex {

    private static final Map<UUID, Integer> HEXED_UNTIL = new HashMap<>();

    private Hex() {
    }

    /** Animation: dark purple swirl around hexed players. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 4 != 0) {
                return;
            }
            HEXED_UNTIL.entrySet().removeIf(entry -> server.getTickCount() >= entry.getValue());
            for (UUID id : HEXED_UNTIL.keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    Fx.hexTick(player, server.getTickCount());
                }
            }
        });
    }

    public static void apply(LivingEntity target, double seconds) {
        if (target.level() instanceof ServerLevel level) {
            HEXED_UNTIL.put(target.getUUID(), level.getServer().getTickCount() + (int) Math.round(seconds * 20));
        }
    }

    public static boolean isHexed(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        Integer until = HEXED_UNTIL.get(entity.getUUID());
        if (until == null) {
            return false;
        }
        if (level.getServer().getTickCount() >= until) {
            HEXED_UNTIL.remove(entity.getUUID());
            return false;
        }
        return true;
    }

    /**
     * Called from the mixin before a heal is applied.
     *
     * @return {@code true} if the heal was turned into damage (the heal must be cancelled)
     */
    public static boolean onHeal(LivingEntity entity, float amount) {
        if (amount <= 0 || !isHexed(entity) || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        entity.hurtServer(level, entity.damageSources().magic(), amount);
        if (entity instanceof ServerPlayer player) {
            Feedback.proc(player, "☠ HEX: HEAL → DAMAGE", ChatFormatting.DARK_PURPLE);
        }
        level.sendParticles(ParticleTypes.SQUID_INK, entity.getX(), entity.getY() + 1, entity.getZ(),
                5, 0.3, 0.4, 0.3, 0.01);
        return true;
    }
}
