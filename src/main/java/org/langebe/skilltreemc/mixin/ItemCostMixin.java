package org.langebe.skilltreemc.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import org.langebe.skilltreemc.core.MarkedItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Villagers and wandering traders never accept a Spell Orb or Utility Compass as payment
 * (the cartographer buys compasses for emeralds).
 */
@Mixin(ItemCost.class)
public abstract class ItemCostMixin {

    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void skilltreemc$rejectMarkedItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (MarkedItems.isMarked(stack)) {
            cir.setReturnValue(false);
        }
    }
}
