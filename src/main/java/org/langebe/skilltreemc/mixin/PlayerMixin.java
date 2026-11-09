package org.langebe.skilltreemc.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.langebe.skilltreemc.mechanics.UtilityPassives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Iron Gut: less hunger exhaustion. */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float skilltreemc$ironGut(float exhaustion) {
        return UtilityPassives.modifyExhaustion((LivingEntity) (Object) this, exhaustion);
    }
}
