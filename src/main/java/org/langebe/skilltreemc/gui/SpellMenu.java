package org.langebe.skilltreemc.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.langebe.skilltreemc.mechanics.Feedback;
import org.langebe.skilltreemc.spell.Mana;
import org.langebe.skilltreemc.spell.Spell;
import org.langebe.skilltreemc.spell.SpellCaster;

import java.util.List;

/**
 * Spell menu (Sneak + offhand key with the Spell Orb): put spells into fast slots.
 *
 * <pre>
 * Row 1: fast slots 1–6 (slots 1–6); locked slots show a barrier
 * Row 2–3: all spells the player has (slots 9–26)
 * Row 4: mana info (31)
 * </pre>
 *
 * Usage: click a spell, then click a fast slot to put it there. Left-click a slot to
 * make it the selected slot (cast with right-click), right-click a slot to clear it.
 */
public class SpellMenu extends ServerMenu {

    private static final int FIRST_FAST_SLOT = 1;
    private static final int FIRST_SPELL_SLOT = 9;
    private static final int MAX_SPELLS_SHOWN = 18;
    private static final int INFO_SLOT = 31;

    /** Spell picked in the list, waiting to be put into a slot. */
    private Spell picked;

    public SpellMenu(int containerId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x4, 4, containerId, playerInventory);
        refresh();
    }

    @Override
    protected void draw() {
        int slots = SpellCaster.slotCount(player);
        int selected = SpellCaster.selectedSlot(player);

        for (int i = 0; i < 6; i++) {
            int menuSlot = FIRST_FAST_SLOT + i;
            if (i >= slots) {
                setItem(menuSlot, ItemBuilder.of(Items.BARRIER).name("Slot " + (i + 1) + " – locked", ChatFormatting.DARK_GRAY)
                        .lore("Unlocked at a higher Support level").build());
                continue;
            }
            int index = i;
            setButton(menuSlot, fastSlotItem(index, index == selected), rightClick -> onFastSlotClicked(index, rightClick));
        }

        List<Spell> spells = SpellCaster.availableSpells(player);
        for (int i = 0; i < Math.min(spells.size(), MAX_SPELLS_SHOWN); i++) {
            Spell spell = spells.get(i);
            ItemBuilder item = ItemBuilder.of(spell.getIcon())
                    .name(spell.getName(), spell == picked ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE)
                    .lore(spell.getManaCost() + " Mana · " + spell.getCooldownSeconds() + "s cooldown", ChatFormatting.AQUA);
            spell.getDescription().forEach(item::lore);
            String cooldown = SpellCaster.cooldownText(player, spell);
            if (!cooldown.isEmpty()) {
                item.lore(cooldown, ChatFormatting.RED);
            }
            item.emptyLine();
            if (spell == picked) {
                item.glint().lore("Now click a fast slot above", ChatFormatting.GREEN);
            } else {
                item.lore("Click to pick, then click a slot", ChatFormatting.YELLOW);
            }
            setButton(FIRST_SPELL_SLOT + i, item.build(), () -> {
                picked = spell == picked ? null : spell;
                refresh();
            });
        }

        setItem(INFO_SLOT, ItemBuilder.of(Items.LAPIS_LAZULI)
                .name("Mana " + (int) Mana.get(player) + " / " + Mana.max(player), ChatFormatting.BLUE)
                .lore("Orb right-click: cast selected slot")
                .lore("Orb sneak + right-click: next slot")
                .build());
    }

    private ItemStack fastSlotItem(int index, boolean selected) {
        Spell spell = SpellCaster.spellInSlot(player, index);
        ItemBuilder item = spell == null
                ? ItemBuilder.of(Items.STAINED_GLASS_PANE.white()).name("Slot " + (index + 1) + " – empty", ChatFormatting.GRAY)
                : ItemBuilder.of(spell.getIcon()).name("Slot " + (index + 1) + ": " + spell.getName(), ChatFormatting.LIGHT_PURPLE);
        if (selected) {
            item.glint().lore("▶ Selected (cast with right-click)", ChatFormatting.GREEN);
        }
        item.emptyLine();
        if (picked != null) {
            item.lore("Click to put " + picked.getName() + " here", ChatFormatting.YELLOW);
        } else {
            item.lore("Left-click: select", ChatFormatting.YELLOW);
            item.lore("Right-click: clear", ChatFormatting.YELLOW);
        }
        return item.build();
    }

    private void onFastSlotClicked(int index, boolean rightClick) {
        if (picked != null) {
            SpellCaster.assign(player, index, picked);
            picked = null;
        } else if (rightClick) {
            SpellCaster.assign(player, index, null);
        } else {
            SpellCaster.select(player, index);
        }
        Feedback.sound(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.3f);
        refresh();
    }
}
