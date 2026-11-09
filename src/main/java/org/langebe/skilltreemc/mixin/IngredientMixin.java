package org.langebe.skilltreemc.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.langebe.skilltreemc.core.MarkedItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spell Orb and Utility Compass never match a recipe ingredient (crafting, smithing,
 * stonecutter, ...). Otherwise the free orbs would be Hearts of the Sea for conduits.
 */
@Mixin(Ingredient.class)
public abstract class IngredientMixin {

    @Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void skilltreemc$rejectMarkedItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (MarkedItems.isMarked(stack)) {
            cir.setReturnValue(false);
        }
    }
}
