package org.langebe.skilltreemc.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.langebe.skilltreemc.SkillTreeConfig;
import org.langebe.skilltreemc.core.Skill;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.core.SkillRegistry;
import org.langebe.skilltreemc.config.Settings;
import org.langebe.skilltreemc.core.Stat;
import org.langebe.skilltreemc.data.SkillPath;
import org.langebe.skilltreemc.mechanics.Cosmetics;
import org.langebe.skilltreemc.mechanics.Feedback;
import org.langebe.skilltreemc.mechanics.Fx;
import org.langebe.skilltreemc.spell.Spell;
import org.langebe.skilltreemc.spell.SpellRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The skill tree of one path, drawn from top to bottom (6 rows).
 *
 * <pre>
 * Rows 1–5: one row per level. Column 0 shows the level number, the nodes sit in the
 *           middle (1 node: centre, 2: left/right, 3: left/centre/right, ...).
 * Row 6:    ▲ scroll up (bottom left), Back, your XP level, ▼ scroll down (bottom right)
 * </pre>
 *
 * Left-click on a scroll button moves one level, right-click a whole page. When the menu
 * opens, it scrolls to the next level the player can work on.
 *
 * <p>Clicking a skill unlocks it (if possible) or toggles it on/off once unlocked.
 */
public class SkillTreeMenu extends ServerMenu {

    private static final int VISIBLE_ROWS = 5;
    private static final int BAR_START = 45;
    private static final int SCROLL_UP_SLOT = 45;
    private static final int BACK_SLOT = 47;
    private static final int INFO_SLOT = 49;
    private static final int SCROLL_DOWN_SLOT = 53;

    /** Columns used for 1..7 nodes on one level, centred. */
    private static final int[][] COLUMNS = {
            {},
            {4},
            {2, 6},
            {2, 4, 6},
            {1, 3, 5, 7},
            {2, 3, 4, 5, 6},
            {1, 2, 3, 5, 6, 7},
            {1, 2, 3, 4, 5, 6, 7},
    };

    private final SkillPath path;
    /** Levels of this path, each with its nodes in registration order. */
    private final List<List<Skill>> levels;
    /** Index of the first visible level. */
    private int scroll;

    public SkillTreeMenu(int containerId, Inventory playerInventory, SkillPath path) {
        super(MenuType.GENERIC_9x6, 6, containerId, playerInventory);
        this.path = path;
        this.levels = groupByLevel(path);
        this.scroll = initialScroll();
        refresh();
    }

    private static List<List<Skill>> groupByLevel(SkillPath path) {
        Map<Integer, List<Skill>> byLevel = new TreeMap<>();
        for (Skill skill : SkillRegistry.byPath(path)) {
            byLevel.computeIfAbsent(skill.getLevel(), level -> new ArrayList<>()).add(skill);
        }
        return new ArrayList<>(byLevel.values());
    }

    /** Scrolls so the first level without an unlocked node is in view (second row). */
    private int initialScroll() {
        for (int i = 0; i < levels.size(); i++) {
            boolean anyUnlocked = levels.get(i).stream().anyMatch(skill -> SkillManager.isUnlocked(player, skill));
            if (!anyUnlocked) {
                return clampScroll(i - 1);
            }
        }
        return clampScroll(levels.size());
    }

    private int maxScroll() {
        return Math.max(0, levels.size() - VISIBLE_ROWS);
    }

    private int clampScroll(int value) {
        return Math.max(0, Math.min(value, maxScroll()));
    }

    @Override
    protected void draw() {
        // Tree rows
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= levels.size()) {
                break;
            }
            List<Skill> nodes = levels.get(index);
            setItem(row * 9, levelMarker(nodes));
            int[] columns = COLUMNS[Math.min(nodes.size(), COLUMNS.length - 1)];
            for (int i = 0; i < columns.length; i++) {
                Skill skill = nodes.get(i);
                setButton(row * 9 + columns[i], skillItem(skill), () -> onSkillClicked(skill));
            }
        }

        // Navigation bar
        for (int slot = BAR_START; slot < getContainer().getContainerSize(); slot++) {
            setItem(slot, MenuItems.barFiller());
        }
        setButton(SCROLL_UP_SLOT, scrollButton(true), page -> scrollBy(page ? -VISIBLE_ROWS : -1));
        setButton(SCROLL_DOWN_SLOT, scrollButton(false), page -> scrollBy(page ? VISIBLE_ROWS : 1));
        setButton(BACK_SLOT, MenuItems.back(), () -> SkillTreeMenus.openPathSelect(player));
        setItem(INFO_SLOT, MenuItems.levelInfo(player.experienceLevel));
    }

    private void scrollBy(int amount) {
        int target = clampScroll(scroll + amount);
        if (target != scroll) {
            scroll = target;
            Feedback.sound(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.6f);
            refresh();
        }
    }

    private ItemStack scrollButton(boolean up) {
        boolean possible = up ? scroll > 0 : scroll < maxScroll();
        int first = levelNumber(scroll);
        int last = levelNumber(Math.min(levels.size(), scroll + VISIBLE_ROWS) - 1);
        ItemBuilder item = ItemBuilder.of(possible ? Items.ARROW : Items.STAINED_GLASS_PANE.gray())
                .name(up ? "▲ Scroll up" : "▼ Scroll down", possible ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)
                .lore("Showing L" + first + "–L" + last + " of " + levels.size() + " levels");
        if (possible) {
            item.lore("Left-click: 1 level · Right-click: 1 page", ChatFormatting.GRAY);
        }
        return item.build();
    }

    private int levelNumber(int index) {
        if (levels.isEmpty() || index < 0) {
            return 0;
        }
        return levels.get(Math.min(index, levels.size() - 1)).get(0).getLevel();
    }

    /** Column 0: level number as stack size; lime once something on this level is unlocked. */
    private ItemStack levelMarker(List<Skill> nodes) {
        int level = nodes.get(0).getLevel();
        boolean done = nodes.stream().anyMatch(skill -> SkillManager.isUnlocked(player, skill));
        ItemBuilder item = ItemBuilder.of(done ? Items.STAINED_GLASS_PANE.lime() : Items.STAINED_GLASS_PANE.white())
                .name("Level " + level, done ? ChatFormatting.GREEN : ChatFormatting.WHITE);
        if (nodes.size() > 1 && nodes.get(0).getExclusiveGroup() != null) {
            item.lore("Choose one of " + nodes.size(), ChatFormatting.GOLD);
        } else if (nodes.size() > 1) {
            item.lore("Follow your branch", ChatFormatting.GOLD);
        }
        ItemStack stack = item.build();
        stack.setCount(Math.max(1, Math.min(level, stack.getMaxStackSize())));
        return stack;
    }

    private void onSkillClicked(Skill skill) {
        if (SkillManager.isUnlocked(player, skill)) {
            boolean nowActive = !SkillManager.isActive(player, skill);
            SkillManager.setActive(player, skill, nowActive);
            Feedback.sound(player, SoundEvents.UI_BUTTON_CLICK.value(), nowActive ? 1.2f : 0.8f);
        } else {
            SkillManager.UnlockResult result = SkillManager.tryUnlock(player, skill);
            if (result == SkillManager.UnlockResult.SUCCESS) {
                Feedback.chat(player, "Unlocked " + skill.getName() + "!", ChatFormatting.GREEN);
                Feedback.sound(player, SoundEvents.PLAYER_LEVELUP, 1.0f);
                Fx.unlock(player, skill.getPath());
                Cosmetics.onUnlock(player, skill);
            } else {
                Feedback.deny(player, failureMessage(skill, result));
            }
        }
        refresh();
    }

    private String failureMessage(Skill skill, SkillManager.UnlockResult result) {
        return switch (result) {
            case NOT_ENOUGH_XP -> "You need " + skill.getCost() + " levels to unlock " + skill.getName() + ".";
            case MISSING_REQUIREMENTS -> "Unlock " + requirementText(skill) + " first.";
            case EXCLUDED -> "You already chose " + SkillManager.chosenRival(player, skill).getName() + ".";
            case PATH_LIMIT_REACHED -> "You can only choose " + SkillTreeConfig.MAX_ACTIVE_PATHS.getInt() + " path(s).";
            case ALREADY_UNLOCKED, SUCCESS -> "";
        };
    }

    /** Builds the menu item for a skill depending on its state for this player. */
    private ItemStack skillItem(Skill skill) {
        boolean unlocked = SkillManager.isUnlocked(player, skill);
        boolean active = SkillManager.isActive(player, skill);
        Skill rival = unlocked ? null : SkillManager.chosenRival(player, skill);

        String title = (skill.getLevel() > 0 ? "L" + skill.getLevel() + " · " : "") + skill.getName();
        ItemBuilder item = ItemBuilder.of(rival != null ? Items.BARRIER : skill.getIcon());
        if (active) {
            item.name(title, ChatFormatting.GREEN).glint();
        } else if (unlocked) {
            item.name(title, ChatFormatting.YELLOW);
        } else if (rival != null) {
            item.name(title, ChatFormatting.DARK_GRAY);
        } else {
            item.name(title, ChatFormatting.WHITE);
        }

        for (String line : skill.getDescription()) {
            item.lore(Settings.format(line));
        }
        for (Map.Entry<Stat, Double> stat : skill.getStats().entrySet()) {
            if (!stat.getKey().hidden()) {
                item.lore(stat.getKey().displayName() + ": " + stat.getKey().format(stat.getValue()), ChatFormatting.BLUE);
            }
        }
        for (String spellId : skill.getSpells()) {
            Spell spell = SpellRegistry.get(spellId);
            item.lore("Spell: " + spell.getName() + " (" + spell.getManaCost() + " Mana, "
                    + spell.getCooldownSeconds() + "s CD)", ChatFormatting.LIGHT_PURPLE);
            for (String line : spell.getDescription()) {
                item.lore("  " + line, ChatFormatting.DARK_PURPLE);
            }
        }
        if (skill.getExclusiveGroup() != null && !unlocked) {
            item.lore("Choose one: " + names(withSelf(skill)), ChatFormatting.GOLD);
        }
        item.emptyLine();

        if (unlocked) {
            item.lore(active ? "✔ Active" : "✘ Inactive", active ? ChatFormatting.GREEN : ChatFormatting.RED);
            item.lore(active ? "Click to disable" : "Click to enable", ChatFormatting.YELLOW);
            return item.build();
        }
        if (rival != null) {
            item.lore("Not available – you chose " + rival.getName(), ChatFormatting.RED);
            return item.build();
        }

        List<Skill> missing = SkillManager.missingRequirements(player, skill);
        if (!missing.isEmpty()) {
            item.lore("Requires: " + requirementText(skill), ChatFormatting.RED);
        }
        boolean affordable = player.experienceLevel >= skill.getCost();
        item.lore("Cost: " + skill.getCost() + " levels", affordable ? ChatFormatting.AQUA : ChatFormatting.RED);
        if (SkillManager.checkUnlock(player, skill) == SkillManager.UnlockResult.SUCCESS) {
            item.lore("Click to unlock", ChatFormatting.YELLOW);
        }
        return item.build();
    }

    /** "A, B" for normal requirements, "A or B" for requiresAny – only the missing ones. */
    private String requirementText(Skill skill) {
        List<Skill> missing = SkillManager.missingRequirements(player, skill);
        List<String> all = new ArrayList<>();
        List<String> any = new ArrayList<>();
        for (Skill required : missing) {
            (skill.getRequiresAny().contains(required.getId()) ? any : all).add(required.getName());
        }
        if (!any.isEmpty()) {
            all.add(String.join(" or ", any));
        }
        return String.join(", ", all);
    }

    private static List<Skill> withSelf(Skill skill) {
        List<Skill> all = new ArrayList<>();
        all.add(skill);
        all.addAll(SkillRegistry.exclusiveRivals(skill));
        return all;
    }

    private static String names(List<Skill> skills) {
        return String.join(", ", skills.stream().map(Skill::getName).toList());
    }
}
