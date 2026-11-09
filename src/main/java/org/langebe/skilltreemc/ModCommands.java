package org.langebe.skilltreemc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.config.Settings;
import org.langebe.skilltreemc.core.MarkedItems;
import org.langebe.skilltreemc.core.Skill;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.core.SkillRegistry;
import org.langebe.skilltreemc.gui.SkillTreeMenus;
import org.langebe.skilltreemc.mechanics.CooldownHud;
import org.langebe.skilltreemc.mechanics.Cosmetics;
import org.langebe.skilltreemc.mechanics.UtilityActives;
import org.langebe.skilltreemc.spell.Mana;
import org.langebe.skilltreemc.spell.SpellOrb;

import java.util.Set;
import java.util.TreeSet;

/**
 * Registers the {@code /st} command tree.
 *
 * <ul>
 *   <li>{@code /st help} – lists all commands</li>
 *   <li>{@code /st open} – opens the skill tree (players only)</li>
 *   <li>{@code /st info [player]} – shows unlocked skills</li>
 *   <li>{@code /st orb} – gives a new Spell Orb if you have none (Support path only)</li>
 *   <li>{@code /st spells} – opens the spell menu (Support path only)</li>
 *   <li>{@code /st compass} – gives a new Utility Compass if you have none (active Utils perk needed)</li>
 *   <li>{@code /st admin unlock <player> <skill>} – unlocks a skill for free (OP)</li>
 *   <li>{@code /st admin reset <player>} – deletes all skill progress (OP)</li>
 *   <li>{@code /st admin list} – lists all registered skills (OP)</li>
 *   <li>{@code /st admin config list [filter] | get <key> | set <key> <value> | reset <key> | resetall | reload}
 *       – view and change balance settings (OP), saved to {@code config/skilltreemc.json}</li>
 * </ul>
 */
public final class ModCommands {

    private static final DynamicCommandExceptionType UNKNOWN_SETTING =
            new DynamicCommandExceptionType(key -> Component.literal("Unknown setting: " + key));
    private static final DynamicCommandExceptionType UNKNOWN_SKILL =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown skill: " + id));

    private ModCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    /** Feedback for {@code /st orb} and {@code /st compass}. */
    private static int giveResult(CommandSourceStack source, MarkedItems.GiveResult result, String item) {
        switch (result) {
            case GIVEN -> {
                source.sendSuccess(() -> Component.literal("You received a new " + item + "."), false);
                return 1;
            }
            case ALREADY_OWNED -> source.sendFailure(Component.literal("You already have a " + item + "."));
            case INVENTORY_FULL -> source.sendFailure(Component.literal("Your inventory is full."));
        }
        return 0;
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("st")
                .executes(context -> {
                    context.getSource().sendFailure(
                            Component.literal("Not a valid command, use /st help to see all commands"));
                    return 0;
                })
                .then(Commands.literal("help").executes(ModCommands::showHelp))
                .then(Commands.literal("open").executes(context -> {
                    // Throws a "player required" error if run from the console / a command block.
                    SkillTreeMenus.openPathSelect(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("orb").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (Mana.max(player) <= 0) {
                        context.getSource().sendFailure(Component.literal("You need the Support path for the Spell Orb."));
                        return 0;
                    }
                    return giveResult(context.getSource(), SpellOrb.give(player, false), "Spell Orb");
                }))
                .then(Commands.literal("compass").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (UtilityActives.ownedActives(player).isEmpty()) {
                        context.getSource().sendFailure(Component.literal("You have no active Utils perk."));
                        return 0;
                    }
                    return giveResult(context.getSource(), UtilityActives.giveCompass(player, false), "Utility Compass");
                }))
                .then(Commands.literal("spells").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (Mana.max(player) <= 0) {
                        context.getSource().sendFailure(Component.literal("You need the Support path to use spells."));
                        return 0;
                    }
                    SkillTreeMenus.openSpells(player);
                    return 1;
                }))
                .then(Commands.literal("cooldowns").executes(context -> {
                    boolean shown = CooldownHud.toggle(context.getSource().getPlayerOrException());
                    context.getSource().sendSuccess(() -> Component.literal("Cooldown sidebar: " + (shown ? "on" : "off")), false);
                    return 1;
                }))
                .then(Commands.literal("aura").executes(context -> {
                    boolean shown = Cosmetics.toggleAura(context.getSource().getPlayerOrException());
                    context.getSource().sendSuccess(() -> Component.literal("Mastery aura: " + (shown ? "on" : "off")), false);
                    return 1;
                }))
                .then(Commands.literal("info")
                        .executes(context -> showInfo(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> showInfo(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("list").executes(ModCommands::listSkills))
                        .then(Commands.literal("config")
                                .then(Commands.literal("list")
                                        .executes(context -> listSettings(context, ""))
                                        .then(Commands.argument("filter", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        settingCategories(), builder))
                                                .executes(context -> listSettings(context,
                                                        StringArgumentType.getString(context, "filter")))))
                                .then(Commands.literal("get")
                                        .then(settingKey().executes(ModCommands::getSetting)))
                                .then(Commands.literal("set")
                                        .then(settingKey()
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(ModCommands::setSetting))))
                                .then(Commands.literal("reset")
                                        .then(settingKey().executes(ModCommands::resetSetting)))
                                .then(Commands.literal("resetall").executes(context -> {
                                    Settings.resetAll();
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "All settings reset to their defaults."), true);
                                    return 1;
                                }))
                                .then(Commands.literal("reload").executes(context -> {
                                    Settings.load();
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "Reloaded " + Settings.file().getFileName()), true);
                                    return 1;
                                })))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ModCommands::resetPlayer)))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("skill", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        SkillRegistry.all().stream().map(Skill::getId), builder))
                                                .executes(ModCommands::unlockSkill))))));
    }

    // ---------------------------------------------------------------------------------
    // /st admin config
    // ---------------------------------------------------------------------------------

    private static RequiredArgumentBuilder<CommandSourceStack, String> settingKey() {
        return Commands.argument("key", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        Settings.all().stream().map(Setting::key), builder));
    }

    /** First part of every key ("combat", "utils", "spell", ...) plus "spell.<id>" groups. */
    private static Set<String> settingCategories() {
        Set<String> categories = new TreeSet<>();
        for (Setting setting : Settings.all()) {
            String[] parts = setting.key().split("\\.");
            categories.add(parts[0]);
            if (parts.length > 2) {
                categories.add(parts[0] + "." + parts[1]);
            }
        }
        return categories;
    }

    private static Setting requireSetting(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String key = StringArgumentType.getString(context, "key");
        Setting setting = Settings.get(key);
        if (setting == null) {
            throw UNKNOWN_SETTING.create(key);
        }
        return setting;
    }

    private static int listSettings(CommandContext<CommandSourceStack> context, String filter) {
        int count = 0;
        for (Setting setting : Settings.all()) {
            if (setting.key().startsWith(filter)) {
                boolean changed = !setting.format().equals(setting.formatDefault());
                context.getSource().sendSuccess(() -> Component.literal(setting.key() + " = ")
                        .append(Component.literal(setting.format())
                                .withStyle(changed ? ChatFormatting.YELLOW : ChatFormatting.GREEN)), false);
                count++;
            }
        }
        if (count == 0) {
            context.getSource().sendFailure(Component.literal("No settings start with '" + filter + "'"));
        } else {
            context.getSource().sendSuccess(() -> Component.literal("(yellow = changed from default) "
                    + "/st admin config get <key> shows the description").withStyle(ChatFormatting.GRAY), false);
        }
        return count;
    }

    private static int getSetting(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Setting setting = requireSetting(context);
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(setting.key()).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(setting.description()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Value: " + setting.format()
                + "   Default: " + setting.formatDefault()), false);
        if (setting.isList()) {
            source.sendSuccess(() -> Component.literal("One value per level/tier, set with e.g. 12,9,9,7,7")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int setSetting(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Setting setting = requireSetting(context);
        String value = StringArgumentType.getString(context, "value");
        try {
            Settings.set(setting.key(), value);
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(setting.key() + " = " + setting.format()), true);
        return 1;
    }

    private static int resetSetting(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Setting setting = requireSetting(context);
        Settings.reset(setting.key());
        context.getSource().sendSuccess(() -> Component.literal(setting.key() + " reset to " + setting.format()), true);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String[] lines = {
                "---- Skill Tree MC ----",
                "/st help - Shows this help",
                "/st open - Opens the skill tree",
                "/st info - Shows your unlocked skills",
                "/st orb - Gives you a new Spell Orb (if you lost yours)",
                "/st spells - Opens the spell menu",
                "/st compass - Gives you a new Utility Compass (if you lost yours)",
                "/st cooldowns - Shows/hides the cooldown sidebar",
                "/st aura - Shows/hides your mastery aura",
                "Utility Compass: right-click use perk, sneak+right-click next perk",
                "Spell Orb: right-click cast, sneak+right-click next slot",
                "Sneak + offhand key: spell menu (orb) / Rage or Shield",
                "/st admin list|reset|unlock - Admin commands (OP)",
                "/st admin config list|get|set|reset|reload - Balance settings (OP)",
        };
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int showInfo(CommandSourceStack source, ServerPlayer target) {
        source.sendSuccess(() -> Component.literal("---- Skills of ").append(target.getName())
                .append(" ----").withStyle(ChatFormatting.GOLD), false);
        int count = 0;
        for (Skill skill : SkillRegistry.all()) {
            if (SkillManager.isUnlocked(target, skill)) {
                boolean active = SkillManager.isActive(target, skill);
                source.sendSuccess(() -> Component.literal((active ? "✔ " : "✘ ") + skill.getName()
                        + " (" + skill.getPath().displayName() + ")")
                        .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
                count++;
            }
        }
        if (count == 0) {
            source.sendSuccess(() -> Component.literal("No skills unlocked yet."), false);
        }
        return count;
    }

    private static int listSkills(CommandContext<CommandSourceStack> context) {
        for (Skill skill : SkillRegistry.all()) {
            context.getSource().sendSuccess(() -> Component.literal(skill.getId() + " - " + skill.getName()
                    + " (" + skill.getCost() + " levels)"), false);
        }
        return SkillRegistry.all().size();
    }

    private static int resetPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        SkillManager.reset(target);
        context.getSource().sendSuccess(() -> Component.literal("Reset all skills of ").append(target.getName()), true);
        return 1;
    }

    private static int unlockSkill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String id = StringArgumentType.getString(context, "skill");
        Skill skill = SkillRegistry.get(id);
        if (skill == null) {
            throw UNKNOWN_SKILL.create(id);
        }
        SkillManager.forceUnlock(target, skill);
        context.getSource().sendSuccess(() -> Component.literal("Unlocked " + skill.getName() + " for ")
                .append(target.getName()), true);
        return 1;
    }
}
