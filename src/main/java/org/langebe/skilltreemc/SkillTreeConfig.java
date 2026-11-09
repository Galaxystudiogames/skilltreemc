package org.langebe.skilltreemc;

import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.data.SkillPath;

import static org.langebe.skilltreemc.config.Settings.number;
import static org.langebe.skilltreemc.config.Settings.tiers;

/**
 * Gameplay and balance settings for the mechanics.
 *
 * <p>Every value here is a {@link Setting}: the number in the code is only the
 * <b>default</b>. The live values are stored in {@code config/skilltreemc.json} and can be
 * changed in-game with {@code /st admin config set <key> <value>}.
 *
 * <p>Values from the design document are used as-is. Values the document leaves open
 * were chosen to fit the surrounding numbers; they are marked "(chosen)".
 *
 * <p>Skill stats per level are in {@code skills/ModSkills} ({@code skill.*} keys), spell
 * values in the spell classes ({@code spell.*} keys).
 */
public final class SkillTreeConfig {

    private SkillTreeConfig() {
    }

    /** Forces class loading so all settings are registered before the config file is read. */
    public static void init() {
    }

    // ----- General ---------------------------------------------------------------------

    public static final Setting MAX_ACTIVE_PATHS = number("general.max_paths", 3,
            "How many paths a player may choose (3 = all)");
    public static final Setting FREE_IN_CREATIVE = number("general.free_in_creative", 1,
            "1 = skills cost no XP in creative mode, 0 = they do");

    // ----- HUD -------------------------------------------------------------------------

    public static final Setting HUD_PROC_MESSAGES = number("hud.proc_messages", 1,
            "1 = action bar shows skill procs (crit, stun, block, ...), 0 = off");
    public static final Setting HUD_PROC_HOLD = number("hud.proc_hold_seconds", 1.5,
            "How long a proc message stays before timers (Rage/Shield) may overwrite it");
    public static final Setting HUD_COOLDOWN_SIDEBAR = number("hud.cooldown_sidebar", 1,
            "1 = sidebar scoreboard lists skills/spells on cooldown, 0 = off for everyone");

    // ----- Cosmetics -------------------------------------------------------------------

    public static final Setting COSMETIC_UNLOCK_TITLE = number("cosmetics.unlock_title", 1,
            "1 = big title on screen when a skill is unlocked");
    public static final Setting COSMETIC_TAB_BADGES = number("cosmetics.tab_badges", 1,
            "1 = tab list shows each player's path levels, e.g. Steve ⚔15★ ✦8");
    public static final Setting COSMETIC_MASTERY_AURA = number("cosmetics.mastery_aura", 1,
            "1 = players who maxed a path get a particle aura in the path colour (/st aura toggles it)");
    public static final Setting COSMETIC_WELCOME = number("cosmetics.welcome_message", 1,
            "1 = players get a short welcome message with clickable /st open and /st help on join");
    public static final Setting COSMETIC_MASTERY_BROADCAST = number("cosmetics.mastery_broadcast", 1,
            "1 = the whole server is told when someone masters a path");

    // Cost curve in XP levels: base * factor^(level - 1). (chosen)
    // Combat L15 ≈ 14, Support L18 ≈ 21, Utils L23 ≈ 20 levels.
    public static final Setting COST_COMBAT_BASE = number("cost.combat.base", 2, "Combat: XP levels for L1");
    public static final Setting COST_COMBAT_FACTOR = number("cost.combat.factor", 1.15, "Combat: cost multiplier per level");
    public static final Setting COST_SUPPORT_BASE = number("cost.support.base", 2, "Support: XP levels for L1");
    public static final Setting COST_SUPPORT_FACTOR = number("cost.support.factor", 1.15, "Support: cost multiplier per level");
    public static final Setting COST_UTILITY_BASE = number("cost.utility.base", 2, "Utils: XP levels for L1");
    public static final Setting COST_UTILITY_FACTOR = number("cost.utility.factor", 1.11, "Utils: cost multiplier per level");

    /** XP-level cost of a skill level: {@code base * factor^(level - 1)}, rounded, at least 1. */
    public static int levelCost(SkillPath path, int level) {
        if (level <= 0) {
            return 1;
        }
        double base;
        double factor;
        switch (path) {
            case COMBAT -> { base = COST_COMBAT_BASE.get(); factor = COST_COMBAT_FACTOR.get(); }
            case SUPPORT -> { base = COST_SUPPORT_BASE.get(); factor = COST_SUPPORT_FACTOR.get(); }
            default -> { base = COST_UTILITY_BASE.get(); factor = COST_UTILITY_FACTOR.get(); }
        }
        return Math.max(1, (int) Math.round(base * Math.pow(factor, level - 1)));
    }

    // ----- Combat ----------------------------------------------------------------------

    public static final Setting COMBAT_TAG_SECONDS = number("combat.tag_seconds", 10,
            "Seconds after the last hit a player counts as 'in combat' (Swift Feet) (chosen)");

    public static final Setting STUN_SECONDS = number("combat.stun.seconds", 2.5, "Stun duration (doc: 2–3s)");
    public static final Setting STUN_COOLDOWN = number("combat.stun.cooldown", 8, "Internal Stun cooldown per attacker");

    public static final Setting RAGE_SECONDS = number("combat.rage.seconds", 10, "Rage base duration (chosen)");
    public static final Setting RAGE_EXTENSION = number("combat.rage.extension", 0.8,
            "Each hit extends Rage by this fraction of the time since the last hit");
    public static final Setting RAGE_SPEED_AMPLIFIER = number("combat.rage.speed_amplifier", 1, "Speed amplifier (1 = Speed II)");
    public static final Setting RAGE_COOLDOWN = number("combat.rage.cooldown", 150, "Rage cooldown after it ends");

    public static final Setting SHIELD_SECONDS = number("combat.shield.seconds", 8, "Shield duration (chosen)");
    public static final Setting SHIELD_EXTRA_REDUCTION = number("combat.shield.extra_reduction", 0.10,
            "Extra damage reduction on top of Reduction Percent");
    public static final Setting SHIELD_COOLDOWN = number("combat.shield.cooldown", 120, "Shield cooldown after it ends");

    public static final Setting IMMORTAL_HEALTH = number("combat.immortal.health", 4,
            "Living Immortal triggers below this health (4 = 2 hearts)");
    public static final Setting IMMORTAL_AMPLIFIER = number("combat.immortal.amplifier", 9,
            "Resistance/Regeneration amplifier (9 = level 10)");
    public static final Setting IMMORTAL_SECONDS = number("combat.immortal.seconds", 2, "Effect duration");
    public static final Setting IMMORTAL_COOLDOWN = number("combat.immortal.cooldown", 40, "Cooldown");

    // ----- Utils choice perks (lists: tier 1 = base, then one value per upgrade) -------

    public static final Setting GRAPPLE_COOLDOWN = tiers("utils.grapple.cooldown",
            "Grapple cooldown per charge (L6 / L11 / L15 / L19 / L23)", 12, 9, 9, 7, 7);
    public static final Setting GRAPPLE_STRENGTH = number("utils.grapple.strength", 1.5, "Launch speed (chosen)");
    public static final Setting GRAPPLE_LIFT = number("utils.grapple.lift", 0.35, "Extra upward boost (chosen)");

    public static final Setting GHOST_SNEAK_SPEED = tiers("utils.ghost_walk.sneak_speed",
            "Extra sneaking speed, vanilla base is 0.3 (chosen)", 0.15, 0.3);
    public static final Setting GHOST_INVIS_SECONDS = tiers("utils.ghost_walk.invis_seconds",
            "Invisibility when starting to sneak (from L15)", 0, 0, 2, 4);
    public static final Setting GHOST_INVIS_COOLDOWN = number("utils.ghost_walk.invis_cooldown", 8,
            "Cooldown of the sneak invisibility, prevents permanent invisibility (chosen)");
    public static final Setting GHOST_BREAK_DELAY = number("utils.ghost_walk.break_delay", 1,
            "L23: attacking breaks invisibility only after this many seconds");

    public static final Setting POTION_DURATION_BONUS = tiers("utils.potion_mastery.bonus",
            "Potion duration bonus (L7 / L12)", 0.30, 0.50);
    public static final Setting POTION_REFUND_COOLDOWN = number("utils.potion_mastery.refund_cooldown", 60,
            "L20: a drunk potion is kept once per this many seconds");

    public static final Setting HUNGER_REDUCTION = tiers("utils.iron_gut.hunger_reduction",
            "Hunger drain reduction (L7 / L12)", 0.40, 0.60);
    public static final Setting SATURATION_BONUS = number("utils.iron_gut.saturation_bonus", 0.5,
            "L16: extra saturation from food");
    public static final Setting IRON_GUT_REGEN_SECONDS = number("utils.iron_gut.regen_interval", 4,
            "L20: heal 1 HP every N seconds at full hunger (chosen)");

    public static final Setting EAGLE_RADIUS = tiers("utils.eagle_eye.radius", "Radius (L8 / L13)", 80, 120);
    public static final Setting EAGLE_SECONDS = tiers("utils.eagle_eye.seconds", "Duration (L8 / L13)", 10, 15);
    public static final Setting EAGLE_COOLDOWN = tiers("utils.eagle_eye.cooldown", "Cooldown (L8 / … / L21)", 60, 60, 60, 40);

    public static final Setting SMOKE_RADIUS = number("utils.smoke_screen.radius", 4, "Cloud radius (chosen)");
    public static final Setting SMOKE_BLIND_SECONDS = tiers("utils.smoke_screen.blind_seconds", "Blindness (L8 / L13)", 3, 5);
    public static final Setting SMOKE_COOLDOWN = tiers("utils.smoke_screen.cooldown", "Cooldown (L8 / … / L21)", 45, 45, 45, 30);
    public static final Setting SMOKE_LINGER_SECONDS = number("utils.smoke_screen.linger_seconds", 4,
            "L17: the cloud stays this long");

    public static final Setting MIDAS_CHANCE = tiers("utils.midas_touch.chance",
            "Double-drop chance (L8 / L13 / L17 / L21)", 0.10, 0.20, 0.25, 0.30);
    public static final Setting MIDAS_FORTUNE_LEVEL = number("utils.midas_touch.fortune_level", 1,
            "L17: Fortune level for tools without Fortune (chosen)");
    public static final Setting MIDAS_XP_BOTTLE_CHANCE = number("utils.midas_touch.xp_bottle_chance", 0.005,
            "L21: chance per mined block for an XP bottle (chosen)");

    public static final Setting DASH_DISTANCE = tiers("utils.phase_dash.distance", "Distance (L9 / L14)", 5, 8);
    public static final Setting DASH_COOLDOWN = number("utils.phase_dash.cooldown", 8, "Cooldown per charge");
    public static final Setting DASH_STUN_SECONDS = number("utils.phase_dash.stun_seconds", 0.5,
            "L22: stun for players you dash through");

    public static final Setting FORTIFY_REDUCTION = tiers("utils.fortify.reduction", "Damage reduction (L9 / L14)", 0.10, 0.15);
    public static final Setting FORTIFY_STILL_TICKS = number("utils.fortify.still_ticks", 10,
            "Ticks without moving before Fortify activates (chosen)");
    public static final Setting FORTIFY_DAMAGE_BONUS = number("utils.fortify.damage_bonus", 0.10, "L18: extra damage while fortified");
    public static final Setting FORTIFY_AUTO_SECONDS = number("utils.fortify.auto_seconds", 5,
            "L22: auto-Fortify duration after taking a crit (chosen)");
    public static final Setting FORTIFY_AUTO_COOLDOWN = number("utils.fortify.auto_cooldown", 30, "L22: auto-Fortify cooldown");

    public static final Setting TRACKER_SECONDS = number("utils.tracker.seconds", 15, "Tracking duration");
    public static final Setting TRACKER_COOLDOWN = tiers("utils.tracker.cooldown", "Cooldown (L10 / … / L23)", 45, 45, 45, 25);

    public static final Setting AMBUSH_BONUS = tiers("utils.ambush.bonus", "Bonus damage (L10 / L15)", 0.25, 0.40);
    public static final Setting AMBUSH_POISON_SECONDS = number("utils.ambush.poison_seconds", 3, "L15: Poison on ambush hit");
    public static final Setting AMBUSH_ARM_TICKS = number("utils.ambush.arm_ticks", 20,
            "Ticks of sneaking needed to arm Ambush (chosen)");
    public static final Setting AMBUSH_WINDOW_TICKS = number("utils.ambush.window_ticks", 100,
            "How long Ambush stays armed after standing up (chosen)");
    public static final Setting AMBUSH_REINVIS_TICKS = number("utils.ambush.reinvis_ticks", 30,
            "L19: invisibility after an ambush hit with Ghost Walk (chosen)");

    public static final Setting TINKER_SAVE = tiers("utils.tinker.save",
            "Chance per durability point to not lose it (L10 / L15 / L19)", 0.20, 0.30, 0.40);
    public static final Setting TINKER_FULL_SAVE = number("utils.tinker.full_save", 0.05,
            "L19: chance to use no durability at all");

    // ----- Support ---------------------------------------------------------------------

    public static final Setting HEX_RANGE = number("support.hex.range", 50, "Hex of Corruption target range (chosen)");
}
