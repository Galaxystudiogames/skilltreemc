package org.langebe.skilltreemc.skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.core.Skill;
import org.langebe.skilltreemc.core.SkillRegistry;
import org.langebe.skilltreemc.core.Stat;
import org.langebe.skilltreemc.data.SkillPath;
import org.langebe.skilltreemc.mechanics.UtilityActives;
import org.langebe.skilltreemc.spell.Mana;
import org.langebe.skilltreemc.spell.SpellOrb;

import static org.langebe.skilltreemc.core.Stat.CRIT_MULTIPLIER;
import static org.langebe.skilltreemc.core.Stat.CRIT_RATE;
import static org.langebe.skilltreemc.core.Stat.DROP_BONUS;
import static org.langebe.skilltreemc.core.Stat.GLOW_SECONDS;
import static org.langebe.skilltreemc.core.Stat.TIER_L10;
import static org.langebe.skilltreemc.core.Stat.TIER_L9;
import static org.langebe.skilltreemc.core.Stat.TIER_L8;
import static org.langebe.skilltreemc.core.Stat.TIER_L7;
import static org.langebe.skilltreemc.core.Stat.TIER_L6;
import static org.langebe.skilltreemc.core.Stat.TINKER;
import static org.langebe.skilltreemc.config.Settings.number;
import static org.langebe.skilltreemc.config.Settings.tiers;
import static org.langebe.skilltreemc.core.Stat.AMBUSH;
import static org.langebe.skilltreemc.core.Stat.TRACKER;
import static org.langebe.skilltreemc.core.Stat.FORTIFY;
import static org.langebe.skilltreemc.core.Stat.PHASE_DASH;
import static org.langebe.skilltreemc.core.Stat.MIDAS_TOUCH;
import static org.langebe.skilltreemc.core.Stat.SMOKE_SCREEN;
import static org.langebe.skilltreemc.core.Stat.EAGLE_EYE;
import static org.langebe.skilltreemc.core.Stat.IRON_GUT;
import static org.langebe.skilltreemc.core.Stat.POTION_MASTERY;
import static org.langebe.skilltreemc.core.Stat.GHOST_WALK;
import static org.langebe.skilltreemc.core.Stat.GRAPPLE;
import static org.langebe.skilltreemc.core.Stat.IMMORTAL_CHANCE;
import static org.langebe.skilltreemc.core.Stat.MANA_REGEN;
import static org.langebe.skilltreemc.core.Stat.MAX_MANA;
import static org.langebe.skilltreemc.core.Stat.RAGE;
import static org.langebe.skilltreemc.core.Stat.REDUCTION_PERCENT;
import static org.langebe.skilltreemc.core.Stat.REDUCTION_RATE;
import static org.langebe.skilltreemc.core.Stat.SHIELD;
import static org.langebe.skilltreemc.core.Stat.SPELL_SLOTS;
import static org.langebe.skilltreemc.core.Stat.STUN_CHANCE;
import static org.langebe.skilltreemc.core.Stat.SWIFT_FEET;
import static org.langebe.skilltreemc.data.SkillPath.COMBAT;
import static org.langebe.skilltreemc.data.SkillPath.SUPPORT;
import static org.langebe.skilltreemc.data.SkillPath.UTILITY;

/**
 * <b>All skill-tree levels, implemented from the design document "Skill System — SMP Mod".</b>
 *
 * <p>Every level is one node. Stats use "highest value wins", so an upgrade level just
 * sets the new value (e.g. Crit Rate 3 % → 5 %). The numbers are config settings
 * ({@code skill.*}, see the fields below); values the design document leaves open are
 * marked "chosen". Description lines can show live config values with placeholders like
 * {@code {combat.stun.seconds}} (see {@code Settings.format}).
 *
 * <p>The menu draws each path as a tree from top to bottom: one row per level. If several
 * nodes share a level (branches, choices), they appear side by side in <b>registration
 * order</b> – so register the left branch (DPS / Healer) before the right one.
 */
public final class ModSkills {

    // ----- Configurable stat values (defaults; live values in config/skilltreemc.json) -----
    // Lists: one value per level in the order given in the description.

    private static final Setting CRIT_RATE_BASE = tiers("skill.combat.crit_rate",
            "Crit Rate at L1 / L3", 0.03, 0.05);
    private static final Setting CRIT_MULTIPLIER_BASE = tiers("skill.combat.crit_multiplier",
            "Crit Multiplier at L1 / L3", 0.05, 0.07);
    private static final Setting REDUCTION_RATE_BASE = tiers("skill.combat.reduction_rate",
            "Reduction Rate at L1 / L3", 0.03, 0.05);
    private static final Setting REDUCTION_PERCENT_BASE = tiers("skill.combat.reduction_percent",
            "Reduction Percent at L1 / L3", 0.05, 0.07);
    private static final Setting GLOW = tiers("skill.combat.glow_seconds",
            "Combat Sense glowing seconds at L4 / L7 / L10 / L13 (chosen)", 1.5, 2, 2.5, 3);
    private static final Setting DPS_CRIT_RATE = tiers("skill.combat.dps.crit_rate",
            "DPS Crit Rate at L6, L7, L9–L14 (chosen)", 0.06, 0.07, 0.08, 0.09, 0.10, 0.11, 0.12, 0.13);
    private static final Setting DPS_CRIT_MULTIPLIER = tiers("skill.combat.dps.crit_multiplier",
            "DPS Crit Multiplier at L6, L7, L9–L14 (chosen)", 0.10, 0.12, 0.15, 0.18, 0.20, 0.22, 0.25, 0.30);
    private static final Setting DPS_HEARTS = tiers("skill.combat.dps.extra_hearts",
            "DPS: extra hearts at L6 / L9 / L11 / L14 (4 hearts in total)", 1, 1, 1, 1);
    private static final Setting TANK_HEARTS = tiers("skill.combat.tank.extra_hearts",
            "Tank: extra hearts at L6 / L7 / L9 / L10 / L11 / L12 / L13 / L14 / L15 (9 hearts in total)",
            1, 1, 1, 1, 1, 1, 1, 1, 1);
    private static final Setting DPS_STUN_CHANCE = number("skill.combat.dps.stun_chance", 0.04,
            "Stun chance per hit (doc: 3–5%)");
    private static final Setting TANK_REDUCTION_RATE = tiers("skill.combat.tank.reduction_rate",
            "Tank Reduction Rate at L6, L7, L9–L14 (chosen)", 0.06, 0.07, 0.08, 0.09, 0.10, 0.11, 0.12, 0.13);
    private static final Setting TANK_REDUCTION_PERCENT = tiers("skill.combat.tank.reduction_percent",
            "Tank Reduction Percent at L6, L7, L9–L14 (chosen)", 0.10, 0.12, 0.15, 0.18, 0.20, 0.22, 0.25, 0.30);
    private static final Setting TANK_IMMORTAL_CHANCE = number("skill.combat.tank.immortal_chance", 0.25,
            "Living Immortal proc chance (chosen)");

    private static final Setting MANA_MAX = tiers("skill.support.max_mana",
            "Max mana at L1 / L2 / L7 / L11 / L15 / L17 (L7+ chosen)", 100, 150, 175, 200, 250, 300);
    private static final Setting MANA_REGEN_PER_SECOND = tiers("skill.support.mana_regen",
            "Mana per second at L1 / L4 / L9 / L12 / L16 (L9+ chosen)", 0.5, 1, 1.25, 1.5, 1.75);

    private static final Setting SWIFT_FEET_LEVEL = tiers("skill.utility.swift_feet",
            "Swift Feet Speed level at L1 / L4", 1, 2);
    private static final Setting SCAVENGER_CHANCE = number("skill.utility.scavenger_chance", 0.15,
            "Scavenger: chance for each mob drop to drop twice");

    private ModSkills() {
    }

    public static void register() {
        registerCombat();
        registerSupport();
        registerUtility();

        // Checks requirements, spells etc. – keep this as the last line.
        SkillRegistry.validate();
    }

    // =====================================================================================
    // COMBAT – PvP-focused. Split at level 5: DPS or Tank.
    // =====================================================================================

    private static void registerCombat() {
        // ----- Pre-split L1–L4 ---------------------------------------------------------
        add(node("combat.l1", COMBAT, 1, "Basis-Stats", Items.IRON_SWORD)
                .stat(CRIT_RATE, CRIT_RATE_BASE, 1).stat(CRIT_MULTIPLIER, CRIT_MULTIPLIER_BASE, 1)
                .stat(REDUCTION_RATE, REDUCTION_RATE_BASE, 1).stat(REDUCTION_PERCENT, REDUCTION_PERCENT_BASE, 1));
        add(node("combat.l2", COMBAT, 2, "+1 Heart", Items.GOLDEN_APPLE)
                .requires("combat.l1")
                .description("Max HP +2 (1 heart)")
                .attribute(Attributes.MAX_HEALTH, 2, Operation.ADD_VALUE));
        add(node("combat.l3", COMBAT, 3, "Stat Upgrade", Items.DIAMOND_SWORD)
                .requires("combat.l2")
                .stat(CRIT_RATE, CRIT_RATE_BASE, 2).stat(CRIT_MULTIPLIER, CRIT_MULTIPLIER_BASE, 2)
                .stat(REDUCTION_RATE, REDUCTION_RATE_BASE, 2).stat(REDUCTION_PERCENT, REDUCTION_PERCENT_BASE, 2));
        add(node("combat.l4", COMBAT, 4, "Combat Sense", Items.GLOWSTONE_DUST)
                .requires("combat.l3")
                .description("Players you hit glow briefly")
                .stat(GLOW_SECONDS, GLOW, 1));

        // ----- DPS (left branch) -------------------------------------------------------
        add(node("combat.dps.l5", COMBAT, 5, "DPS", Items.NETHERITE_SWORD)
                .requires("combat.l4").exclusiveGroup("combat.branch")
                .description("Damage branch: crits, Stun, Rage"));
        // L6–7, L9–14: stat upgrades (values: DPS_CRIT_RATE / DPS_CRIT_MULTIPLIER / GLOW)
        dpsStats(6, 1, 0);
        hearts(DPS_HEARTS, 1, "combat.dps.l6");
        dpsStats(7, 2, 2);
        add(node("combat.dps.l8", COMBAT, 8, "Stun", Items.COBWEB)
                .requires("combat.dps.l7")
                .description("Passive: chance per hit to stun the target",
                        "(can't move, skills blocked) for {combat.stun.seconds}s",
                        "Internal cooldown: {combat.stun.cooldown}s")
                .stat(STUN_CHANCE, DPS_STUN_CHANCE));
        dpsStats(9, 3, 0);
        hearts(DPS_HEARTS, 2, "combat.dps.l9");
        dpsStats(10, 4, 3);
        dpsStats(11, 5, 0);
        hearts(DPS_HEARTS, 3, "combat.dps.l11");
        dpsStats(12, 6, 0);
        dpsStats(13, 7, 4);
        dpsStats(14, 8, 0);
        hearts(DPS_HEARTS, 4, "combat.dps.l14");
        add(node("combat.dps.l15", COMBAT, 15, "Rage", Items.BLAZE_ROD)
                .requires("combat.dps.l14")
                .description("Active (Sneak + offhand key, no orb in hand):",
                        "Speed II + 100% crit rate for {combat.rage.seconds}s",
                        "Each hit extends it by {combat.rage.extension%}% of the time since the last hit",
                        "Cooldown: {combat.rage.cooldown}s after it ends")
                .stat(RAGE, 1));

        // ----- Tank (right branch) -----------------------------------------------------
        add(node("combat.tank.l5", COMBAT, 5, "Tank", Items.NETHERITE_CHESTPLATE)
                .requires("combat.l4").exclusiveGroup("combat.branch")
                .description("Defense branch: damage reduction, Living Immortal, Shield"));
        // L6–7, L9–14: stat upgrades (values: TANK_REDUCTION_RATE / TANK_REDUCTION_PERCENT / GLOW)
        tankStats(6, 1, 0);
        hearts(TANK_HEARTS, 1, "combat.tank.l6");
        tankStats(7, 2, 2);
        hearts(TANK_HEARTS, 2, "combat.tank.l7");
        add(node("combat.tank.l8", COMBAT, 8, "Living Immortal", Items.TOTEM_OF_UNDYING)
                .requires("combat.tank.l7")
                .description("Passive: below 2 hearts a chance for",
                        "Resistance 10 + Regeneration 10 ({combat.immortal.seconds}s)",
                        "Also saves you from a lethal hit", "Cooldown: {combat.immortal.cooldown}s")
                .stat(IMMORTAL_CHANCE, TANK_IMMORTAL_CHANCE));
        tankStats(9, 3, 0);
        hearts(TANK_HEARTS, 3, "combat.tank.l9");
        tankStats(10, 4, 3);
        hearts(TANK_HEARTS, 4, "combat.tank.l10");
        tankStats(11, 5, 0);
        hearts(TANK_HEARTS, 5, "combat.tank.l11");
        tankStats(12, 6, 0);
        hearts(TANK_HEARTS, 6, "combat.tank.l12");
        tankStats(13, 7, 4);
        hearts(TANK_HEARTS, 7, "combat.tank.l13");
        tankStats(14, 8, 0);
        hearts(TANK_HEARTS, 8, "combat.tank.l14");
        add(node("combat.tank.l15", COMBAT, 15, "Shield", Items.SHIELD)
                .requires("combat.tank.l14")
                .description("Active (Sneak + offhand key, no orb in hand):",
                        "Every hit is reduced, reduction +{combat.shield.extra_reduction%}%",
                        "for {combat.shield.seconds}s, cooldown: {combat.shield.cooldown}s after it ends")
                .stat(SHIELD, 1));
        hearts(TANK_HEARTS, 9, "combat.tank.l15");
    }

    /**
     * Adds extra hearts (from a per-level config list) to an already registered skill.
     * Each skill gets its own modifier, so the hearts of all levels stack.
     */
    private static void hearts(Setting setting, int index, String skillId) {
        SkillRegistry.get(skillId)
                .attribute(Attributes.MAX_HEALTH, () -> setting.tier(index) * 2, Operation.ADD_VALUE)
                .addDescription("+{" + setting.key() + "@" + index + "} heart(s)");
    }

    /**
     * DPS stat-upgrade level.
     *
     * @param index    entry in the DPS crit lists (1 = L6)
     * @param glowTier entry in {@link #GLOW} for longer Combat Sense, or 0
     */
    private static void dpsStats(int level, int index, int glowTier) {
        Skill skill = node("combat.dps.l" + level, COMBAT, level, "Stat Upgrade", Items.REDSTONE)
                .requires("combat.dps.l" + (level - 1))
                .stat(CRIT_RATE, DPS_CRIT_RATE, index).stat(CRIT_MULTIPLIER, DPS_CRIT_MULTIPLIER, index);
        if (glowTier > 0) {
            skill.stat(GLOW_SECONDS, GLOW, glowTier);
        }
        add(skill);
    }

    /** Tank stat-upgrade level, see {@link #dpsStats}. */
    private static void tankStats(int level, int index, int glowTier) {
        Skill skill = node("combat.tank.l" + level, COMBAT, level, "Stat Upgrade", Items.IRON_INGOT)
                .requires("combat.tank.l" + (level - 1))
                .stat(REDUCTION_RATE, TANK_REDUCTION_RATE, index).stat(REDUCTION_PERCENT, TANK_REDUCTION_PERCENT, index);
        if (glowTier > 0) {
            skill.stat(GLOW_SECONDS, GLOW, glowTier);
        }
        add(skill);
    }

    // =====================================================================================
    // SUPPORT / SPELLS – mana-based. Split at level 5: Healer/Buffer or Damage/Debuffer.
    // =====================================================================================

    private static void registerSupport() {
        // ----- Pre-split L1–L4 ---------------------------------------------------------
        add(new Skill("support.l1", SUPPORT) {
            @Override
            public void onUnlock(ServerPlayer player) {
                SpellOrb.give(player, true);
                Mana.set(player, Mana.max(player));
            }
        }.level(1).name("Mana System + Basic Spells").icon(Items.HEART_OF_THE_SEA)
                .description("You receive the Spell Orb (/st orb replaces a lost one)")
                .stat(MAX_MANA, MANA_MAX, 1).stat(MANA_REGEN, MANA_REGEN_PER_SECOND, 1).stat(SPELL_SLOTS, 2)
                .spell("area_heal").spell("heal_ray").spell("firebolt"));
        add(node("support.l2", SUPPORT, 2, "Mana Upgrade", Items.LAPIS_LAZULI)
                .requires("support.l1")
                .stat(MAX_MANA, MANA_MAX, 2).stat(SPELL_SLOTS, 3));
        add(node("support.l3", SUPPORT, 3, "Damage Aura", Items.BLAZE_POWDER)
                .requires("support.l2")
                .spell("damage_aura"));
        add(node("support.l4", SUPPORT, 4, "Mana Regen", Items.AMETHYST_SHARD)
                .requires("support.l3")
                .stat(MANA_REGEN, MANA_REGEN_PER_SECOND, 2));

        // ----- Healer / Buffer (left branch) -------------------------------------------
        supportBranch("healer", "Healer / Buffer", Items.GLISTERING_MELON_SLICE,
                "Heals and buffs your allies",
                new String[]{"area_heal_advanced", "heal_ray_advanced", "buff_shower"}, "Heal Upgrades + Buff Shower",
                new String[]{"angels_circle", "buff_shower_plus"}, "Angels Circle + Buff Shower+",
                "sanctum", "Sanctum (Ultimate)");

        // ----- Damage / Debuffer (right branch) -----------------------------------------
        supportBranch("damage", "Damage / Debuffer", Items.FIRE_CHARGE,
                "Damages and weakens your enemies",
                new String[]{"firebolt_advanced", "damage_aura_advanced", "dixiklo"}, "Damage Upgrades + Dixiklo",
                new String[]{"demon_shockwave"}, "Demon Shockwave",
                "hex", "Hex of Corruption (Ultimate)");
    }

    /**
     * One Support branch, levels 5–18. Shared structure from the design document:
     * slot upgrades at L6/L10/L14, spells at L8/L13/L18, mana and regen upgrades in between.
     */
    private static void supportBranch(String branch, String branchName, Item branchIcon,
                                      String branchDescription,
                                      String[] l8Spells, String l8Name,
                                      String[] l13Spells, String l13Name,
                                      String l18Spell, String l18Name) {
        String prefix = "support." + branch + ".l";
        add(node(prefix + 5, SUPPORT, 5, branchName, branchIcon)
                .requires("support.l4").exclusiveGroup("support.branch")
                .description(branchDescription));

        for (int level = 6; level <= 18; level++) {
            Skill skill = node(prefix + level, SUPPORT, level, "Mana Upgrade", Items.LAPIS_LAZULI)
                    .requires(prefix + (level - 1));
            switch (level) {
                // Shared slot upgrades
                case 6 -> skill.name("4th Fast-Spell Slot").icon(Items.BOOK).stat(SPELL_SLOTS, 4);
                case 10 -> skill.name("5th Fast-Spell Slot").icon(Items.BOOK).stat(SPELL_SLOTS, 5);
                case 14 -> skill.name("6th Fast-Spell Slot").icon(Items.BOOK).stat(SPELL_SLOTS, 6);
                // Spell levels
                case 8 -> addSpells(skill.name(l8Name).icon(Items.ENCHANTED_BOOK), l8Spells);
                case 13 -> addSpells(skill.name(l13Name).icon(Items.ENCHANTED_BOOK), l13Spells);
                case 18 -> skill.name(l18Name).icon(Items.NETHER_STAR).spell(l18Spell);
                // "Rest: Mana + Regen-Upgrades zwischen den Spell-Leveln" (values: MANA_MAX / MANA_REGEN_PER_SECOND)
                case 7 -> skill.stat(MAX_MANA, MANA_MAX, 3);
                case 9 -> skill.name("Mana Regen").icon(Items.AMETHYST_SHARD).stat(MANA_REGEN, MANA_REGEN_PER_SECOND, 3);
                case 11 -> skill.stat(MAX_MANA, MANA_MAX, 4);
                case 12 -> skill.name("Mana Regen").icon(Items.AMETHYST_SHARD).stat(MANA_REGEN, MANA_REGEN_PER_SECOND, 4);
                case 15 -> skill.stat(MAX_MANA, MANA_MAX, 5);
                case 16 -> skill.name("Mana Regen").icon(Items.AMETHYST_SHARD).stat(MANA_REGEN, MANA_REGEN_PER_SECOND, 5);
                case 17 -> skill.stat(MAX_MANA, MANA_MAX, 6);
                default -> { }
            }
            add(skill);
        }
    }

    private static void addSpells(Skill skill, String[] spellIds) {
        for (String spellId : spellIds) {
            skill.spell(spellId);
        }
    }

    // =====================================================================================
    // UTILS – all-rounder. From L6 on: one of 2–3 options per choice level, no hard split.
    // =====================================================================================

    private static void registerUtility() {
        add(node("utility.l1", UTILITY, 1, "Basic Passives", Items.FEATHER)
                .description("Swift Feet: Speed I outside of combat",
                        "Soft Landing: -20% fall damage")
                .stat(SWIFT_FEET, SWIFT_FEET_LEVEL, 1)
                .attribute(Attributes.FALL_DAMAGE_MULTIPLIER, -0.2, Operation.ADD_VALUE));
        add(node("utility.l2", UTILITY, 2, "Night Owl", Items.GOLDEN_CARROT)
                .requires("utility.l1")
                .description("Permanent Night Vision (no blinking)")
                .effect(MobEffects.NIGHT_VISION, 0));
        add(node("utility.l3", UTILITY, 3, "Scavenger", Items.BONE)
                .requires("utility.l2")
                .description("Mob drops have a {skill.utility.scavenger_chance%}% chance to drop twice")
                .stat(DROP_BONUS, SCAVENGER_CHANCE));
        add(node("utility.l4", UTILITY, 4, "Upgrades", Items.RABBIT_FOOT)
                .requires("utility.l3")
                .description("Swift Feet → Speed II", "Soft Landing → -40% fall damage")
                .stat(SWIFT_FEET, SWIFT_FEET_LEVEL, 2)
                .attribute(Attributes.FALL_DAMAGE_MULTIPLIER, -0.2, Operation.ADD_VALUE)); // stacks with L1
        add(node("utility.l5", UTILITY, 5, "Choice System", Items.COMPASS)
                .requires("utility.l4")
                .description("From now on: 2–3 options per level, only one can be chosen.",
                        "Upgrades for your choices come at later levels."));

        // ----- L6: Mobility vs. Stealth -------------------------------------------------
        choice(6, "grapple", "Grapple", Items.FISHING_ROD, GRAPPLE, true, new String[]{"utility.l5"},
                "Active: launches you in your view direction", "Cooldown: {utils.grapple.cooldown@1}s");
        choice(6, "ghost_walk", "Ghost Walk", Items.PHANTOM_MEMBRANE, GHOST_WALK, false, new String[]{"utility.l5"},
                "Passive: no step sounds, faster sneaking");

        // ----- L7: Combat Support vs. Sustain --------------------------------------------
        choice(7, "potion_mastery", "Potion Mastery", Items.BREWING_STAND, POTION_MASTERY, false,
                l6Options(), "Passive: potions last {utils.potion_mastery.bonus@1%}% longer");
        choice(7, "iron_gut", "Iron Gut", Items.COOKED_BEEF, IRON_GUT, false,
                l6Options(), "Passive: hunger drains {utils.iron_gut.hunger_reduction@1%}% slower");

        // ----- L8: Information vs. Offensiv vs. Ressource ---------------------------------
        String[] l7 = ids(7, "potion_mastery", "iron_gut");
        choice(8, "eagle_eye", "Eagle Eye", Items.SPYGLASS, EAGLE_EYE, true, l7,
                "Active: all players within {utils.eagle_eye.radius@1} blocks",
                "on your compass for {utils.eagle_eye.seconds@1}s", "Cooldown: {utils.eagle_eye.cooldown@1}s");
        choice(8, "smoke_screen", "Smoke Screen", Items.GUNPOWDER, SMOKE_SCREEN, true, l7,
                "Active: smoke cloud blinds enemies for {utils.smoke_screen.blind_seconds@1}s",
                "Cooldown: {utils.smoke_screen.cooldown@1}s");
        choice(8, "midas_touch", "Midas Touch", Items.GOLD_INGOT, MIDAS_TOUCH, false, l7,
                "Passive: ores have a {utils.midas_touch.chance@1%}% chance to drop twice");

        // ----- L9: Aggressive Mobility vs. Defense -------------------------------------
        String[] l8 = ids(8, "eagle_eye", "smoke_screen", "midas_touch");
        choice(9, "phase_dash", "Phase Dash", Items.ENDER_PEARL, PHASE_DASH, true, l8,
                "Active: teleport {utils.phase_dash.distance@1} blocks in view direction",
                "Cooldown: {utils.phase_dash.cooldown}s");
        choice(9, "fortify", "Fortify", Items.IRON_CHESTPLATE, FORTIFY, false, l8,
                "Passive: {utils.fortify.reduction@1%}% damage reduction while standing still");

        // ----- L10: Tracking vs. Ambush vs. Equipment -------------------------------------
        String[] l9 = ids(9, "phase_dash", "fortify");
        choice(10, "tracker", "Tracker", Items.RECOVERY_COMPASS, TRACKER, true, l9,
                "Active: nearest player on your compass for {utils.tracker.seconds}s",
                "Cooldown: {utils.tracker.cooldown@1}s");
        choice(10, "ambush", "Ambush", Items.IRON_SWORD, AMBUSH, false, l9,
                "Passive: first hit after sneaking deals +{utils.ambush.bonus@1%}% damage");
        choice(10, "tinker", "Tinker", Items.ANVIL, TINKER, false, l9,
                "Passive: equipment loses {utils.tinker.save@1%}% less durability");

        // ----- L11–L23: upgrades for whatever was chosen ---------------------------------
        String[] l10 = ids(10, "tracker", "ambush", "tinker");
        upgrade(11, l10, new Stat[]{TIER_L6}, new int[]{2},
                "Grapple: cooldown → {utils.grapple.cooldown@2}s",
                "Ghost Walk: sneaking even faster");
        upgrade(12, null, new Stat[]{TIER_L7}, new int[]{2},
                "Potion Mastery: → {utils.potion_mastery.bonus@2%}% longer",
                "Iron Gut: → {utils.iron_gut.hunger_reduction@2%}% slower");
        upgrade(13, null, new Stat[]{TIER_L8}, new int[]{2},
                "Eagle Eye: radius → {utils.eagle_eye.radius@2}, duration → {utils.eagle_eye.seconds@2}s",
                "Smoke Screen: Blindness → {utils.smoke_screen.blind_seconds@2}s + Slowness I",
                "Midas Touch: → {utils.midas_touch.chance@2%}%, also rare mob drops");
        upgrade(14, null, new Stat[]{TIER_L9}, new int[]{2},
                "Phase Dash: distance → {utils.phase_dash.distance@2} blocks",
                "Fortify: → {utils.fortify.reduction@2%}%, also while sneaking");
        upgrade(15, null, new Stat[]{TIER_L6, TIER_L10}, new int[]{3, 2},
                "Grapple: second charge",
                "Ghost Walk: {utils.ghost_walk.invis_seconds@3}s Invisibility when you start sneaking",
                "Tracker: shows distance + HP",
                "Ambush: +{utils.ambush.bonus@2%}% damage + Poison {utils.ambush.poison_seconds}s",
                "Tinker: → {utils.tinker.save@2%}%");
        upgrade(16, null, new Stat[]{TIER_L7}, new int[]{3},
                "Potion Mastery: splash potions hit you at full strength",
                "Iron Gut: food gives {utils.iron_gut.saturation_bonus%}% more saturation");
        upgrade(17, null, new Stat[]{TIER_L8}, new int[]{3},
                "Eagle Eye: also shows player HP",
                "Smoke Screen: cloud stays {utils.smoke_screen.linger_seconds}s, hits players walking in",
                "Midas Touch: {utils.midas_touch.chance@3%}%, Fortune on all tools");
        upgrade(18, null, new Stat[]{TIER_L9}, new int[]{3},
                "Phase Dash: second charge",
                "Fortify: +{utils.fortify.damage_bonus%}% own damage while fortified");
        upgrade(19, null, new Stat[]{TIER_L6, TIER_L10}, new int[]{4, 3},
                "Grapple: cooldown per charge → {utils.grapple.cooldown@4}s",
                "Ghost Walk: Invisibility → {utils.ghost_walk.invis_seconds@4}s",
                "Tracker: tracks 3 players at once",
                "Ambush: short re-invisibility with Ghost Walk",
                "Tinker: → {utils.tinker.save@3%}%, {utils.tinker.full_save%}% chance to use no durability");
        upgrade(20, null, new Stat[]{TIER_L7}, new int[]{4},
                "Potion Mastery: every {utils.potion_mastery.refund_cooldown}s a potion is not used up",
                "Iron Gut: passive regeneration at full hunger");
        upgrade(21, null, new Stat[]{TIER_L8}, new int[]{4},
                "Eagle Eye: cooldown → {utils.eagle_eye.cooldown@4}s, detects invisible players",
                "Smoke Screen: cooldown → {utils.smoke_screen.cooldown@4}s, Speed I in your own cloud",
                "Midas Touch: {utils.midas_touch.chance@4%}%, chance for XP bottles when mining");
        upgrade(22, null, new Stat[]{TIER_L9}, new int[]{4},
                "Phase Dash: dashing through players stuns them {utils.phase_dash.stun_seconds}s",
                "Fortify: auto-Fortify after taking a crit ({utils.fortify.auto_cooldown}s CD)");
        upgrade(23, null, new Stat[]{TIER_L6, TIER_L10}, new int[]{5, 4},
                "Grapple: hitting a player restores a charge",
                "Ghost Walk: attacking breaks Invisibility only after {utils.ghost_walk.break_delay}s",
                "Tracker: cooldown → {utils.tracker.cooldown@4}s, particle trail only you can see",
                "Ambush: crits from Ambush double the bonus damage",
                "Tinker: a breaking hit leaves equipment at 1 durability instead (once)");
    }

    /**
     * One option of a Utils choice level. Options appear left to right in registration order.
     * Active perks give the Utility Compass when unlocked.
     */
    private static void choice(int level, String perkId, String name, Item icon, Stat perk,
                               boolean active, String[] previous, String... description) {
        Skill skill = active ? new Skill("utility.l" + level + "." + perkId, UTILITY) {
            @Override
            public void onUnlock(ServerPlayer player) {
                UtilityActives.giveCompass(player, true);
            }
        } : new Skill("utility.l" + level + "." + perkId, UTILITY);
        Stat track = switch (level) {
            case 6 -> TIER_L6;
            case 7 -> TIER_L7;
            case 8 -> TIER_L8;
            case 9 -> TIER_L9;
            default -> TIER_L10;
        };
        add(skill.level(level).name(name).icon(icon)
                .requiresAny(previous)
                .exclusiveGroup("utility.l" + level)
                .description(active ? withCompassHint(description) : description)
                .stat(perk, 1).stat(track, 1));
    }

    /** One upgrade level: raises the tier of the perks chosen at the given choice levels. */
    private static void upgrade(int level, String[] requiresAnyOf, Stat[] tracks, int[] tiers,
                                String... description) {
        Skill skill = node("utility.l" + level, UTILITY, level, "Upgrades", Items.EXPERIENCE_BOTTLE)
                .description(description);
        if (requiresAnyOf != null) {
            skill.requiresAny(requiresAnyOf);
        } else {
            skill.requires("utility.l" + (level - 1));
        }
        for (int i = 0; i < tracks.length; i++) {
            skill.stat(tracks[i], tiers[i]);
        }
        add(skill);
    }

    private static String[] l6Options() {
        return ids(6, "grapple", "ghost_walk");
    }

    private static String[] ids(int level, String... perkIds) {
        String[] result = new String[perkIds.length];
        for (int i = 0; i < perkIds.length; i++) {
            result[i] = "utility.l" + level + "." + perkIds[i];
        }
        return result;
    }

    private static String[] withCompassHint(String[] description) {
        String[] result = new String[description.length + 1];
        System.arraycopy(description, 0, result, 0, description.length);
        result[description.length] = "Use with the Utility Compass (you get one)";
        return result;
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private static Skill node(String id, SkillPath path, int level, String name, Item icon) {
        return new Skill(id, path).level(level).name(name).icon(icon);
    }

    private static void add(Skill skill) {
        SkillRegistry.register(skill);
    }
}
