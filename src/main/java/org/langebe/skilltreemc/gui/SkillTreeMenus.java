package org.langebe.skilltreemc.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import org.langebe.skilltreemc.data.SkillPath;

/** Helper for opening the skill-tree menus. */
public final class SkillTreeMenus {

    private SkillTreeMenus() {
    }

    /** Opens the path-selection screen (the entry point of the skill tree). */
    public static void openPathSelect(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, menuPlayer) -> new PathSelectMenu(containerId, playerInventory),
                Component.literal("Skill Tree")
        ));
    }

    /** Opens the skill tree of one path. */
    public static void openPath(ServerPlayer player, SkillPath path) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, menuPlayer) -> new SkillTreeMenu(containerId, playerInventory, path),
                Component.literal(path.displayName())
        ));
    }

    /** Opens the spell menu (fast-slot setup). */
    public static void openSpells(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, menuPlayer) -> new SpellMenu(containerId, playerInventory),
                Component.literal("Spells")
        ));
    }
}
