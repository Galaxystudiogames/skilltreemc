package org.langebe.skilltreemc.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.mechanics.CombatMechanics;
import org.langebe.skilltreemc.mechanics.Hex;
import org.langebe.skilltreemc.mechanics.UtilityPassives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into damage and healing of every living entity:
 * <ul>
 *   <li>damage amount → crit / reduction / Shield ({@link CombatMechanics#modifyDamage})</li>
 *   <li>heal → damage while hexed ({@link Hex#onHeal})</li>
 *   <li>received effects → longer potions with Potion Mastery</li>
 *   <li>loading a player → remember the saved health, which vanilla caps at the max health
 *       <i>without</i> the skill bonuses (they are only applied on join)</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float skilltreemc$modifyDamage(float amount, ServerLevel level, DamageSource source) {
        return CombatMechanics.modifyDamage((LivingEntity) (Object) this, source, amount);
    }

    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance skilltreemc$potionMastery(MobEffectInstance effect) {
        return UtilityPassives.modifyPotionEffect((LivingEntity) (Object) this, effect);
    }

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void skilltreemc$invertHeal(float amount, CallbackInfo ci) {
        if (Hex.onHeal((LivingEntity) (Object) this, amount)) {
            ci.cancel();
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void skilltreemc$rememberHealth(ValueInput input, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            SkillManager.rememberSavedHealth(player, input.getFloatOr("Health", 0));
        }
    }
}
