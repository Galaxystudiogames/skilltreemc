package org.langebe.skilltreemc.mechanics;

import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.core.Stat;

import java.util.List;

/**
 * Utils path mechanics that are not simple attributes/effects:
 * <ul>
 *   <li><b>Swift Feet</b> – Speed while not in combat (from the L15 upgrade on: always).</li>
 *   <li><b>Scavenger</b> – chance for every mob drop to drop twice.</li>
 *   <li>Loot hook for <b>Midas Touch</b> (see {@link UtilityPassives}).</li>
 * </ul>
 * (Soft Landing and Night Owl are plain attribute/effect skills in {@code ModSkills}.)
 */
public final class UtilityMechanics {

    /** Short duration so the speed is gone quickly after entering combat. */
    private static final int SWIFT_FEET_TICKS = 30;

    private UtilityMechanics() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UtilityMechanics::tick);
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            Entity entity = context.getOptional(LootContextParams.THIS_ENTITY);
            Player killer = context.getOptional(LootContextParams.LAST_DAMAGE_PLAYER);
            BlockState block = context.getOptional(LootContextParams.BLOCK_STATE);
            Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
            if (block != null && entity instanceof ServerPlayer miner) {
                // Block loot: THIS_ENTITY is the player who broke the block.
                UtilityPassives.midasDrops(miner, block, false, drops, origin);
            } else if (entity instanceof LivingEntity && !(entity instanceof Player) && killer instanceof ServerPlayer player) {
                Vec3 at = origin != null ? origin : entity.position();
                scavenge(player, drops, at);
                UtilityPassives.midasDrops(player, null, true, drops, at);
            }
        });
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int level = (int) SkillManager.getStat(player, Stat.SWIFT_FEET);
            boolean always = SkillManager.getStat(player, Stat.SWIFT_FEET_COMBAT) > 0;
            if (level > 0 && player.isAlive() && (always || !CombatTracker.inCombat(player))) {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, SWIFT_FEET_TICKS, level - 1, true, false, true));
            }
        }
    }

    private static void scavenge(ServerPlayer player, List<ItemStack> drops, Vec3 at) {
        double chance = SkillManager.getStat(player, Stat.DROP_BONUS);
        if (chance <= 0) {
            return;
        }
        boolean doubled = false;
        for (ItemStack stack : List.copyOf(drops)) {
            if (player.getRandom().nextDouble() < chance) {
                drops.add(stack.copy());
                doubled = true;
            }
        }
        if (doubled) {
            Fx.scavenger(player.level(), at);
            Feedback.proc(player, "♻ SCAVENGER x2", ChatFormatting.GREEN);
        }
    }
}
