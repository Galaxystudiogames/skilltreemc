package org.langebe.skilltreemc.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of one player's skill-tree progress.
 *
 * <p>This record is stored on the player via the Fabric Data Attachment API
 * (see {@link SkillData}). Attachment values should be immutable, so every
 * "modifying" method returns a <em>new</em> instance instead of changing this one.
 *
 * <p>Only ids are stored. If a skill is removed from the code later, its id simply
 * stays in the save and is ignored.
 *
 * @param unlockedSkills ids of skills the player has bought (permanent)
 * @param activeSkills   ids of unlocked skills that are currently switched on
 * @param activePaths    ids of the skill paths the player has chosen (see {@link SkillPath#id()})
 */
public record PlayerSkills(Set<String> unlockedSkills, Set<String> activeSkills, Set<String> activePaths) {

    public static final PlayerSkills EMPTY = new PlayerSkills(Set.of(), Set.of(), Set.of());

    /** Sets are written to NBT as plain string lists. */
    private static final Codec<Set<String>> STRING_SET =
            Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf);

    /**
     * Codec used to save/load the data with the player file.
     * Fields are optional so that older saves (or new fields added later) still load.
     */
    public static final Codec<PlayerSkills> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            STRING_SET.optionalFieldOf("unlocked_skills", Set.of()).forGetter(PlayerSkills::unlockedSkills),
            STRING_SET.optionalFieldOf("active_skills", Set.of()).forGetter(PlayerSkills::activeSkills),
            STRING_SET.optionalFieldOf("active_paths", Set.of()).forGetter(PlayerSkills::activePaths)
    ).apply(instance, PlayerSkills::new));

    public PlayerSkills {
        // Defensive copies: guarantees the record really is immutable.
        unlockedSkills = Set.copyOf(unlockedSkills);
        activeSkills = Set.copyOf(activeSkills);
        activePaths = Set.copyOf(activePaths);
    }

    public PlayerSkills withUnlockedSkill(String skillId) {
        return new PlayerSkills(plus(unlockedSkills, skillId), activeSkills, activePaths);
    }

    public PlayerSkills withActiveSkill(String skillId) {
        return new PlayerSkills(unlockedSkills, plus(activeSkills, skillId), activePaths);
    }

    public PlayerSkills withoutActiveSkill(String skillId) {
        return new PlayerSkills(unlockedSkills, minus(activeSkills, skillId), activePaths);
    }

    public PlayerSkills withActivePath(String pathId) {
        return new PlayerSkills(unlockedSkills, activeSkills, plus(activePaths, pathId));
    }

    private static Set<String> plus(Set<String> set, String value) {
        Set<String> copy = new HashSet<>(set);
        copy.add(value);
        return copy;
    }

    private static Set<String> minus(Set<String> set, String value) {
        Set<String> copy = new HashSet<>(set);
        copy.remove(value);
        return copy;
    }
}
