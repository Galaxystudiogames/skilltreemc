package org.langebe.skilltreemc.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.langebe.skilltreemc.mechanics.UtilityPassives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks "these effects come from a splash potion" for Potion Mastery. */
@Mixin(ThrownSplashPotion.class)
public abstract class ThrownSplashPotionMixin {

    @Inject(method = "onHitAsPotion", at = @At("HEAD"))
    private void skilltreemc$beginSplash(ServerLevel level, ItemStack stack, HitResult hit, CallbackInfo ci) {
        UtilityPassives.beginSplash(level.getServer(), stack);
    }

    @Inject(method = "onHitAsPotion", at = @At("TAIL"))
    private void skilltreemc$endSplash(ServerLevel level, ItemStack stack, HitResult hit, CallbackInfo ci) {
        UtilityPassives.endSplash();
    }
}
