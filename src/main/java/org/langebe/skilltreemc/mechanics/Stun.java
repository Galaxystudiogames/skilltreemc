package org.langebe.skilltreemc.mechanics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.langebe.skilltreemc.SkillTreeMC;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Stun (Combat DPS L8): the target can't move or jump, and a stunned player can't use
 * skills (spells, Rage, Shield).
 *
 * <p>Implemented with temporary attribute modifiers that set movement speed and jump
 * strength to zero. They are removed again when the stun ends.
 */
public final class Stun {

    private static final Identifier MODIFIER_ID = SkillTreeMC.id("stun");
    private static final Map<LivingEntity, Integer> STUNNED_UNTIL = new HashMap<>();

    private Stun() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Stun::tick);
    }

    public static boolean isStunned(LivingEntity entity) {
        return STUNNED_UNTIL.containsKey(entity);
    }

    public static void apply(LivingEntity target, double seconds) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int until = level.getServer().getTickCount() + (int) Math.round(seconds * 20);
        STUNNED_UNTIL.merge(target, until, Math::max);
        setModifier(target, Attributes.MOVEMENT_SPEED, true);
        setModifier(target, Attributes.JUMP_STRENGTH, true);
        Fx.stunApply(target);
        if (target instanceof ServerPlayer player) {
            Feedback.actionBar(player, "You are stunned!", ChatFormatting.RED, ChatFormatting.BOLD);
        }
    }

    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<LivingEntity, Integer>> iterator = STUNNED_UNTIL.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, Integer> entry = iterator.next();
            LivingEntity entity = entry.getKey();
            if (entity.isRemoved() || server.getTickCount() >= entry.getValue()) {
                setModifier(entity, Attributes.MOVEMENT_SPEED, false);
                setModifier(entity, Attributes.JUMP_STRENGTH, false);
                iterator.remove();
            } else if (server.getTickCount() % 3 == 0) {
                Fx.stunTick(entity, server.getTickCount());
            }
        }
    }

    private static void setModifier(LivingEntity entity, Holder<Attribute> attribute, boolean add) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (add) {
            // -100 % of the total value = 0
            instance.addOrUpdateTransientModifier(new AttributeModifier(
                    MODIFIER_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            instance.removeModifier(MODIFIER_ID);
        }
    }
}
