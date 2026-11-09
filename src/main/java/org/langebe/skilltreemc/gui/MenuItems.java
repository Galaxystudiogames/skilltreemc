package org.langebe.skilltreemc.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Items that appear in several menus. */
final class MenuItems {

    private MenuItems() {
    }

    /** Nameless gray pane used as background. */
    static ItemStack filler() {
        return ItemBuilder.of(Items.STAINED_GLASS_PANE.gray()).build();
    }

    /** Nameless black pane used for the navigation bar. */
    static ItemStack barFiller() {
        return ItemBuilder.of(Items.STAINED_GLASS_PANE.black()).build();
    }

    static ItemStack back() {
        return ItemBuilder.of(Items.ARROW).name("Back", ChatFormatting.YELLOW).build();
    }

    static ItemStack levelInfo(int level) {
        return ItemBuilder.of(Items.EXPERIENCE_BOTTLE)
                .name("Your level: " + level, ChatFormatting.GREEN)
                .lore("Skills are paid with XP levels.")
                .build();
    }
}
