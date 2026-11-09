package org.langebe.skilltreemc.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * Small helper to build the "button" items shown in the menus.
 *
 * <pre>{@code
 * ItemStack stack = ItemBuilder.of(Items.DIAMOND)
 *         .name("Hello", ChatFormatting.AQUA)
 *         .lore("First line")
 *         .glint()
 *         .build();
 * }</pre>
 *
 * Names and lore are not italic (vanilla makes custom text italic by default), and the
 * item's own tooltip extras such as armour attributes are hidden.
 */
public final class ItemBuilder {

    private final Item item;
    private Component name = Component.empty();
    private final List<Component> lore = new ArrayList<>();
    private boolean glint;

    private ItemBuilder(Item item) {
        this.item = item;
    }

    public static ItemBuilder of(Item item) {
        return new ItemBuilder(item);
    }

    public ItemBuilder name(String text, ChatFormatting... formatting) {
        this.name = plain(text, formatting);
        return this;
    }

    /** Adds one tooltip line (gray if no formatting is given). */
    public ItemBuilder lore(String text, ChatFormatting... formatting) {
        lore.add(plain(text, formatting.length == 0 ? new ChatFormatting[]{ChatFormatting.GRAY} : formatting));
        return this;
    }

    public ItemBuilder emptyLine() {
        lore.add(Component.empty());
        return this;
    }

    /** Adds the enchantment shimmer. */
    public ItemBuilder glint() {
        this.glint = true;
        return this;
    }

    /** Always returns a new stack, so it is safe to put the result into a slot. */
    public ItemStack build() {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lore)));
        }
        if (glint) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT
                .withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)
                .withHidden(DataComponents.POTION_CONTENTS, true));
        return stack;
    }

    private static MutableComponent plain(String text, ChatFormatting... formatting) {
        return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formatting);
    }
}
