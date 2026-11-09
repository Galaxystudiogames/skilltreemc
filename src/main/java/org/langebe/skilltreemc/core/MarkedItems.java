package org.langebe.skilltreemc.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.langebe.skilltreemc.mechanics.UtilityActives;
import org.langebe.skilltreemc.spell.SpellOrb;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Helpers for the mod's marked vanilla items (Spell Orb = Heart of the Sea, Utility Compass =
 * Compass).
 *
 * <p>Because these are real vanilla items, they must never be usable as their vanilla
 * counterpart – otherwise {@code /st orb} / {@code /st compass} would be a free source of
 * Hearts of the Sea (conduits) and compasses (cartographer trades). The mixins
 * {@code IngredientMixin} and {@code ItemCostMixin} use {@link #isMarked} to keep them out of
 * every recipe and villager trade.
 */
public final class MarkedItems {

    /** Result of {@link #giveOnce}. */
    public enum GiveResult {
        GIVEN,
        ALREADY_OWNED,
        INVENTORY_FULL
    }

    private MarkedItems() {
    }

    /** {@code true} for a Spell Orb or a Utility Compass. */
    public static boolean isMarked(ItemStack stack) {
        return !stack.isEmpty() && (SpellOrb.isOrb(stack) || UtilityActives.isCompass(stack));
    }

    /** {@code true} if the player carries a matching item (inventory or the cursor of an open menu). */
    public static boolean owns(ServerPlayer player, Predicate<ItemStack> test) {
        return player.getInventory().contains(test) || test.test(player.containerMenu.getCarried());
    }

    /**
     * Gives the item only if the player does not carry one already.
     *
     * @param dropIfFull drop the item at the player's feet when the inventory is full
     *                   (used for the one-time gift on unlock; the commands refuse instead so
     *                   they cannot be used to spam item entities)
     */
    public static GiveResult giveOnce(ServerPlayer player, Supplier<ItemStack> factory, Predicate<ItemStack> test,
                                      boolean dropIfFull) {
        if (owns(player, test)) {
            return GiveResult.ALREADY_OWNED;
        }
        ItemStack stack = factory.get();
        if (player.getInventory().add(stack)) {
            return GiveResult.GIVEN;
        }
        if (!dropIfFull) {
            return GiveResult.INVENTORY_FULL;
        }
        player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
        return GiveResult.GIVEN;
    }
}
