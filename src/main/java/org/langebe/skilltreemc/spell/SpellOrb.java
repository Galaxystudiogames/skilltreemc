package org.langebe.skilltreemc.spell;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.langebe.skilltreemc.core.MarkedItems;
import org.langebe.skilltreemc.gui.ItemBuilder;

/**
 * The Spell Orb – the focus item for spells.
 *
 * <p>Because the mod is server-side only, the orb is a vanilla Heart of the Sea with a
 * hidden marker in its custom data. Controls (orb in main hand):
 * <ul>
 *   <li>Right-click – cast the selected fast spell</li>
 *   <li>Sneak + right-click – select the next fast slot</li>
 *   <li>Sneak + offhand key (F) – open the spell menu</li>
 * </ul>
 */
public final class SpellOrb {

    private static final String MARKER = "skilltreemc_spell_orb";

    private SpellOrb() {
    }

    public static ItemStack create() {
        ItemStack stack = ItemBuilder.of(Items.HEART_OF_THE_SEA)
                .name("Spell Orb", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .lore("Right-click: cast selected spell")
                .lore("Sneak + right-click: next spell slot")
                .lore("Sneak + offhand key: spell menu")
                .glint()
                .build();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.setCount(1);
        return stack;
    }

    public static boolean isOrb(ItemStack stack) {
        if (!stack.is(Items.HEART_OF_THE_SEA)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(MARKER, false);
    }

    /**
     * Gives the player an orb unless they already carry one.
     *
     * @param dropIfFull drop it at the player's feet if the inventory is full (otherwise nothing is given)
     */
    public static MarkedItems.GiveResult give(ServerPlayer player, boolean dropIfFull) {
        return MarkedItems.giveOnce(player, SpellOrb::create, SpellOrb::isOrb, dropIfFull);
    }
}
