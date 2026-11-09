package org.langebe.skilltreemc.spell;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Holds all registered spells (see {@link ModSpells}). */
public final class SpellRegistry {

    private static final Map<String, Spell> SPELLS = new LinkedHashMap<>();

    private SpellRegistry() {
    }

    public static <T extends Spell> T register(T spell) {
        if (SPELLS.containsKey(spell.getId())) {
            throw new IllegalArgumentException("Duplicate spell id '" + spell.getId() + "'");
        }
        SPELLS.put(spell.getId(), spell);
        return spell;
    }

    public static void validate() {
        for (Spell spell : SPELLS.values()) {
            if (spell.getReplaces() != null && !SPELLS.containsKey(spell.getReplaces())) {
                throw new IllegalStateException("Spell '" + spell.getId() + "' replaces unknown spell '"
                        + spell.getReplaces() + "'");
            }
        }
    }

    /** @return the spell or {@code null}. */
    public static Spell get(String id) {
        return SPELLS.get(id);
    }

    public static Collection<Spell> all() {
        return Collections.unmodifiableCollection(SPELLS.values());
    }
}
