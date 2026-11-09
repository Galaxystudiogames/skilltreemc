package org.langebe.skilltreemc.data;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The skill paths a player can pick from in the path-selection menu.
 *
 * <p>Each path knows its stable save id, its display name and colour, a short summary,
 * the item used as its icon and the slot (in the 9x3 path-selection menu) where that
 * icon is placed. The indicator pane is drawn directly above the icon, i.e. at
 * {@code iconSlot - 9}, so {@code iconSlot} must be in the middle row (9–17).
 */
public enum SkillPath {
    COMBAT("combat", "Combat", "⚔", ChatFormatting.RED, 0xE53935, Items.NETHERITE_SWORD, 10,
            "PvP-focused. Split at level 5: DPS or Tank."),
    UTILITY("utility", "Utils", "➹", ChatFormatting.AQUA, 0x26C6DA, Items.NETHERITE_BOOTS, 13,
            "All-rounder with passive perks."),
    SUPPORT("support", "Support / Spells", "✦", ChatFormatting.LIGHT_PURPLE, 0xD05CE3, Items.HEART_OF_THE_SEA, 16,
            "Mana-based spells via the Spell Orb.", "Split at level 5: Healer or Damage.");

    private final String id;
    private final String displayName;
    private final String symbol;
    private final ChatFormatting color;
    private final int particleColor;
    private final Item icon;
    private final int iconSlot;
    private final String[] summary;

    SkillPath(String id, String displayName, String symbol, ChatFormatting color, int particleColor, Item icon, int iconSlot,
              String... summary) {
        this.id = id;
        this.displayName = displayName;
        this.symbol = symbol;
        this.color = color;
        this.particleColor = particleColor;
        this.icon = icon;
        this.iconSlot = iconSlot;
        this.summary = summary;
    }

    /** Id written to the save file. Never change it once released. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** One-character symbol used in the tab list and titles. */
    public String symbol() {
        return symbol;
    }

    public ChatFormatting color() {
        return color;
    }

    /** RGB colour for particle effects of this path. */
    public int particleColor() {
        return particleColor;
    }

    public Item icon() {
        return icon;
    }

    public int iconSlot() {
        return iconSlot;
    }

    /** Slot of the coloured indicator pane, one row above the icon. */
    public int indicatorSlot() {
        return iconSlot - 9;
    }

    /** Short description lines for the path icon. */
    public String[] summary() {
        return summary.clone();
    }
}
