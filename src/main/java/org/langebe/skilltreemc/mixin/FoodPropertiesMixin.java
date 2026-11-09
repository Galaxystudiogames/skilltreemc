package org.langebe.skilltreemc.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.langebe.skilltreemc.mechanics.UtilityPassives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Iron Gut tier 3: extra saturation after eating. */
@Mixin(FoodProperties.class)
public abstract class FoodPropertiesMixin {

    @Inject(method = "onConsume", at = @At("TAIL"))
    private void skilltreemc$extraSaturation(Level level, LivingEntity entity, ItemStack stack, Consumable consumable, CallbackInfo ci) {
        if (!level.isClientSide()) {
            FoodProperties food = (FoodProperties) (Object) this;
            UtilityPassives.onFoodEaten(entity, food.saturation());
        }
    }
}
