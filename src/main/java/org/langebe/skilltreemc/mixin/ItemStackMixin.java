package org.langebe.skilltreemc.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.langebe.skilltreemc.mechanics.UtilityPassives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/**
 * Tinker: less durability loss. All tool/armour damage ends up in this overload of
 * {@code hurtAndBreak}, so one hook covers everything.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true)
    private int skilltreemc$tinker(int amount, int originalAmount, ServerLevel level, ServerPlayer player, Consumer<ItemStack> onBreak) {
        return UtilityPassives.modifyDurabilityLoss((ItemStack) (Object) this, amount, player);
    }
}
