package org.langebe.skilltreemc.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.langebe.skilltreemc.mechanics.Cosmetics;
import org.langebe.skilltreemc.mechanics.UtilityActives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <ul>
 *   <li>Tab list name with path badges (see {@link Cosmetics#tabListName}).</li>
 *   <li>Dropped Utility Compasses forget the tracked position ({@link UtilityActives#onDrop}).</li>
 * </ul>
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void skilltreemc$tabBadges(CallbackInfoReturnable<Component> cir) {
        Component name = Cosmetics.tabListName((ServerPlayer) (Object) this);
        if (name != null) {
            cir.setReturnValue(name);
        }
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"))
    private void skilltreemc$clearCompass(ItemStack stack, boolean throwRandomly, Prediction prediction,
                                          CallbackInfoReturnable<ItemEntity> cir) {
        UtilityActives.onDrop(stack);
    }
}
