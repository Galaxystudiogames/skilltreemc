package org.langebe.skilltreemc.core;

import org.langebe.skilltreemc.data.SkillPath;
import org.langebe.skilltreemc.spell.SpellRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Holds all registered skills.
 *
 * <p>Skills are registered once at startup (see {@code skills/ModSkills}). Every skill
 * is checked; mistakes such as duplicate ids, a missing level or a
 * requirement/spell that doesn't exist stop the server with a clear error message
 * instead of causing strange behaviour later.
 */
public final class SkillRegistry {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_.]+");
    /** A tree row has 9 slots; column 0 shows the level, so at most 7 nodes fit nicely. */
    public static final int MAX_PER_LEVEL = 7;

    private static final Map<String, Skill> SKILLS = new LinkedHashMap<>();

    private SkillRegistry() {
    }

    public static <T extends Skill> T register(T skill) {
        String id = skill.getId();
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid skill id '" + id + "': only a-z, 0-9, _ and . are allowed");
        }
        if (SKILLS.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate skill id '" + id + "'");
        }
        if (skill.getLevel() < 1) {
            throw new IllegalArgumentException("Skill '" + id + "' needs a level >= 1 (call level(...))");
        }
        if (skill.getCost() < 0) {
            throw new IllegalArgumentException("Skill '" + id + "' has a negative cost");
        }
        long sameLevel = SKILLS.values().stream()
                .filter(other -> other.getPath() == skill.getPath() && other.getLevel() == skill.getLevel()).count();
        if (sameLevel >= MAX_PER_LEVEL) {
            throw new IllegalArgumentException("Too many skills on level " + skill.getLevel() + " of path "
                    + skill.getPath().id() + " (max " + MAX_PER_LEVEL + ")");
        }
        SKILLS.put(id, skill);
        return skill;
    }

    /**
     * Checks things that can only be verified once everything is registered
     * (requirements may be registered after the skill that needs them).
     */
    public static void validate() {
        for (Skill skill : SKILLS.values()) {
            List<String> allRequired = new ArrayList<>(skill.getRequires());
            allRequired.addAll(skill.getRequiresAny());
            for (String required : allRequired) {
                if (!SKILLS.containsKey(required)) {
                    throw new IllegalStateException("Skill '" + skill.getId() + "' requires unknown skill '" + required + "'");
                }
                if (required.equals(skill.getId())) {
                    throw new IllegalStateException("Skill '" + skill.getId() + "' requires itself");
                }
            }
            for (String spell : skill.getSpells()) {
                if (SpellRegistry.get(spell) == null) {
                    throw new IllegalStateException("Skill '" + skill.getId() + "' unlocks unknown spell '" + spell + "'");
                }
            }
        }
    }

    /** @return the skill with this id, or {@code null} if there is none. */
    public static Skill get(String id) {
        return SKILLS.get(id);
    }

    public static Collection<Skill> all() {
        return Collections.unmodifiableCollection(SKILLS.values());
    }

    /** Skills of a path in registration order. */
    public static List<Skill> byPath(SkillPath path) {
        return SKILLS.values().stream().filter(skill -> skill.getPath() == path).toList();
    }

    /** All other skills in the same exclusive group. */
    public static List<Skill> exclusiveRivals(Skill skill) {
        if (skill.getExclusiveGroup() == null) {
            return List.of();
        }
        return SKILLS.values().stream()
                .filter(other -> other != skill && skill.getExclusiveGroup().equals(other.getExclusiveGroup()))
                .toList();
    }
}
