package org.langebe.skilltreemc.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.langebe.skilltreemc.mechanics.UtilityPassives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks "this effect comes from a drunk potion" for Potion Mastery. */
@Mixin(PotionContents.class)
public abstract class PotionContentsMixin {

    @Inject(method = "onConsume", at = @At("HEAD"))
    private void skilltreemc$beginDrink(Level level, LivingEntity entity, ItemStack stack, Consumable consumable, CallbackInfo ci) {
        if (!level.isClientSide()) {
            UtilityPassives.beginDrink(entity, stack);
        }
    }

    @Inject(method = "onConsume", at = @At("TAIL"))
    private void skilltreemc$endDrink(Level level, LivingEntity entity, ItemStack stack, Consumable consumable, CallbackInfo ci) {
        if (!level.isClientSide()) {
            UtilityPassives.endDrink(entity);
        }
    }
}
