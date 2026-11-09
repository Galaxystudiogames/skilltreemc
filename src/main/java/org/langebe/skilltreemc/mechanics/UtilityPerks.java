package org.langebe.skilltreemc.mechanics;

import net.minecraft.server.level.ServerPlayer;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.core.Stat;

/**
 * The Utils choice perks and their upgrade tiers.
 *
 * <p>At each choice level (L6–L10) the player picks one perk. The perk node sets the
 * perk flag (e.g. {@link Stat#GRAPPLE}) and tier 1 of its level track
 * (e.g. {@link Stat#TIER_L6}). Later upgrade levels raise the track tier for whatever
 * perk was chosen at that level – so one upgrade node works for every option.
 */
public enum UtilityPerks {
    // L6: Mobility vs. Stealth
    GRAPPLE(Stat.GRAPPLE, Stat.TIER_L6, true),
    GHOST_WALK(Stat.GHOST_WALK, Stat.TIER_L6, false),
    // L7: Combat Support vs. Sustain
    POTION_MASTERY(Stat.POTION_MASTERY, Stat.TIER_L7, false),
    IRON_GUT(Stat.IRON_GUT, Stat.TIER_L7, false),
    // L8: Information vs. Offensiv vs. Ressource
    EAGLE_EYE(Stat.EAGLE_EYE, Stat.TIER_L8, true),
    SMOKE_SCREEN(Stat.SMOKE_SCREEN, Stat.TIER_L8, true),
    MIDAS_TOUCH(Stat.MIDAS_TOUCH, Stat.TIER_L8, false),
    // L9: Aggressive Mobility vs. Defense
    PHASE_DASH(Stat.PHASE_DASH, Stat.TIER_L9, true),
    FORTIFY(Stat.FORTIFY, Stat.TIER_L9, false),
    // L10: Tracking vs. Ambush vs. Equipment
    TRACKER(Stat.TRACKER, Stat.TIER_L10, true),
    AMBUSH(Stat.AMBUSH, Stat.TIER_L10, false),
    TINKER(Stat.TINKER, Stat.TIER_L10, false);

    private final Stat flag;
    private final Stat track;
    private final boolean active;

    UtilityPerks(Stat flag, Stat track, boolean active) {
        this.flag = flag;
        this.track = track;
        this.active = active;
    }

    public Stat flag() {
        return flag;
    }

    public Stat track() {
        return track;
    }

    /** Active perks are used with the Utility Compass. */
    public boolean isActive() {
        return active;
    }

    /**
     * Upgrade tier of this perk for the player: 0 = not chosen, 1 = base, 2+ = upgrades.
     */
    public int tier(ServerPlayer player) {
        if (SkillManager.getStat(player, flag) <= 0) {
            return 0;
        }
        return Math.max(1, (int) SkillManager.getStat(player, track));
    }

    public boolean has(ServerPlayer player) {
        return tier(player) > 0;
    }
}
