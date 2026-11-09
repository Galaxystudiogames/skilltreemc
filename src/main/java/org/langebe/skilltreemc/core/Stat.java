package org.langebe.skilltreemc.core;

/**
 * Numeric values that skills give to a player and that the game mechanics read.
 *
 * <p>A skill sets a stat with {@code .stat(Stat.CRIT_RATE, 0.05)}. If several active
 * skills set the same stat, the <b>highest</b> value wins. This matches the design
 * ("Crit Rate → 5%") – a later level simply sets a higher value.
 *
 * <p>Percentages are written as fractions: {@code 0.05} = 5 %.
 */
public enum Stat {
    // ----- Combat ------------------------------------------------------------------
    /** Chance per melee hit to land a crit (0.05 = 5 %). */
    CRIT_RATE("Crit Rate", true),
    /** Extra damage on a crit (0.05 = +5 %). */
    CRIT_MULTIPLIER("Crit Multiplier", true),
    /** Chance that incoming damage is reduced. */
    REDUCTION_RATE("Reduction Rate", true),
    /** How much damage is reduced on a proc. */
    REDUCTION_PERCENT("Reduction Percent", true),
    /** Combat Sense: seconds of Glowing on a player you hit. */
    GLOW_SECONDS("Combat Sense Glowing", false),
    /** Stun: chance per melee hit. */
    STUN_CHANCE("Stun Chance", true),
    /** Living Immortal: proc chance below 2 hearts. */
    IMMORTAL_CHANCE("Living Immortal Chance", true),
    /** 1 = player can use Rage. */
    RAGE("Rage", false, true),
    /** 1 = player can use Shield. */
    SHIELD("Shield", false, true),

    // ----- Support -----------------------------------------------------------------
    /** Maximum mana. 0 = player has no mana system. */
    MAX_MANA("Max Mana", false),
    /** Mana regenerated per second. */
    MANA_REGEN("Mana Regen / s", false),
    /** Number of fast-spell slots. */
    SPELL_SLOTS("Fast-Spell Slots", false),

    // ----- Utils -------------------------------------------------------------------
    /** Swift Feet: Speed level outside of combat (1 = Speed I). */
    SWIFT_FEET("Swift Feet Speed", false),
    /** Scavenger: chance for each mob drop to drop twice. */
    DROP_BONUS("Extra Mob Drops", true),

    // Utils choice perks: 1 = the player chose this perk (see mechanics/UtilityPerks).
    GRAPPLE("Grapple", false, true),
    GHOST_WALK("Ghost Walk", false, true),
    POTION_MASTERY("Potion Mastery", false, true),
    IRON_GUT("Iron Gut", false, true),
    EAGLE_EYE("Eagle Eye", false, true),
    SMOKE_SCREEN("Smoke Screen", false, true),
    MIDAS_TOUCH("Midas Touch", false, true),
    PHASE_DASH("Phase Dash", false, true),
    FORTIFY("Fortify", false, true),
    TRACKER("Tracker", false, true),
    AMBUSH("Ambush", false, true),
    TINKER("Tinker", false, true),

    // Upgrade tier of the perk chosen at a choice level (1 = base, 2+ = upgrades).
    TIER_L6("Upgrade Tier L6", false, true),
    TIER_L7("Upgrade Tier L7", false, true),
    TIER_L8("Upgrade Tier L8", false, true),
    TIER_L9("Upgrade Tier L9", false, true),
    TIER_L10("Upgrade Tier L10", false, true);

    private final String displayName;
    private final boolean percent;
    private final boolean hidden;

    Stat(String displayName, boolean percent) {
        this(displayName, percent, false);
    }

    Stat(String displayName, boolean percent, boolean hidden) {
        this.displayName = displayName;
        this.percent = percent;
        this.hidden = hidden;
    }

    /** Hidden stats are flags/tiers that are not listed in tooltips (the description explains them). */
    public boolean hidden() {
        return hidden;
    }

    public String displayName() {
        return displayName;
    }

    /** Formats a value for tooltips, e.g. {@code "5%"} or {@code "150"}. */
    public String format(double value) {
        if (percent) {
            return trim(value * 100) + "%";
        }
        return trim(value);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(Math.round(value * 100) / 100.0);
    }
}
