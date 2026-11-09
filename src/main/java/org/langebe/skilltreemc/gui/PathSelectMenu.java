package org.langebe.skilltreemc.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import org.langebe.skilltreemc.SkillTreeConfig;
import org.langebe.skilltreemc.core.Skill;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.core.SkillRegistry;
import org.langebe.skilltreemc.data.PlayerSkills;
import org.langebe.skilltreemc.data.SkillData;
import org.langebe.skilltreemc.data.SkillPath;

import java.util.List;

/**
 * First screen of the skill tree: lets the player choose between the skill paths.
 *
 * <pre>
 * Layout (slot numbers):
 *  0  1  2  3  4  5  6  7  8    <- row 1: indicator panes above the icons (1, 4, 7)
 *  9 10 11 12 13 14 15 16 17    <- row 2: path icons (10, 13, 16)
 * 18 19 20 21 22 23 24 25 26    <- row 3: level info in the middle (22)
 * </pre>
 *
 * The indicator is lime once the player has chosen the path, red otherwise.
 */
public class PathSelectMenu extends ServerMenu {

    private static final int INFO_SLOT = 22;

    public PathSelectMenu(int containerId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x3, 3, containerId, playerInventory);
        refresh();
    }

    @Override
    protected void draw() {
        PlayerSkills data = SkillData.get(player);

        for (SkillPath path : SkillPath.values()) {
            boolean chosen = data.activePaths().contains(path.id());
            List<Skill> skills = SkillRegistry.byPath(path);
            long unlocked = skills.stream().filter(skill -> SkillManager.isUnlocked(player, skill)).count();

            ItemBuilder icon = ItemBuilder.of(path.icon())
                    .name(path.displayName(), path.color(), ChatFormatting.BOLD);
            for (String line : path.summary()) {
                icon.lore(line);
            }
            icon.lore(unlocked + " / " + skills.size() + " skills unlocked", ChatFormatting.DARK_AQUA)
                    .emptyLine();
            if (chosen) {
                icon.lore("✔ Path chosen", ChatFormatting.GREEN).glint();
            } else {
                icon.lore("Unlock a skill to choose this path", ChatFormatting.DARK_GRAY);
            }
            icon.lore("Click to open", ChatFormatting.YELLOW);

            setButton(path.iconSlot(), icon.build(), () -> SkillTreeMenus.openPath(player, path));
            setItem(path.indicatorSlot(), ItemBuilder.of(chosen
                    ? Items.STAINED_GLASS_PANE.lime()
                    : Items.STAINED_GLASS_PANE.red()).build());
        }

        setItem(INFO_SLOT, ItemBuilder.of(Items.EXPERIENCE_BOTTLE)
                .name("Your level: " + player.experienceLevel, ChatFormatting.GREEN)
                .lore("Paths chosen: " + data.activePaths().size() + " / " + SkillTreeConfig.MAX_ACTIVE_PATHS.getInt())
                .lore("Skills are paid with XP levels.")
                .build());
    }
}
