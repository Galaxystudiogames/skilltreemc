package org.langebe.skilltreemc.spell;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.config.Settings;

import java.util.List;

/**
 * Base class for all spells of the Support path.
 *
 * <p>A spell only implements {@link #cast} and {@link #getDescription}. Mana, cooldown,
 * stun check and messages are handled by {@link SpellCaster}. Spells are registered in
 * {@link ModSpells} and unlocked by skills via {@code Skill.spell("id")}.
 *
 * <h2>Config</h2>
 * Mana cost, cooldown and every value created with {@link #param} are config settings
 * ({@code spell.<id>.<name>}), changeable in {@code config/skilltreemc.json} or with
 * {@code /st admin config}. Always read them at cast time ({@code setting.get()}).
 *
 * <h2>Upgrades</h2>
 * An "Advanced" spell sets {@link #replaces} to the base spell id. Once the player has
 * the upgrade, it takes the place of the base spell everywhere (also in fast slots)
 * and shares its cooldown.
 */
public abstract class Spell {

    /** What happened when the spell was cast. */
    public enum CastResult {
        /** Spell worked – mana and cooldown are charged. */
        SUCCESS,
        /** Nothing to hit/heal – nothing is charged. */
        NO_TARGET,
        /** The spell opened a menu and will finish later via {@link SpellCaster#completePending}. */
        PENDING
    }

    private final String id;
    private final String name;
    private final Item icon;
    private final Setting manaCost;
    private final Setting cooldown;
    private String replaces;

    protected Spell(String id, String name, Item icon, int manaCost, int cooldownSeconds) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.manaCost = param("mana", manaCost, name + ": mana cost");
        this.cooldown = param("cooldown", cooldownSeconds, name + ": cooldown in seconds");
    }

    /** Performs the spell. Called only if the player has enough mana and no cooldown. */
    public abstract CastResult cast(ServerPlayer caster);

    /** Tooltip lines describing the spell with its current values. */
    public abstract List<String> getDescription();

    /** Creates a config setting {@code spell.<id>.<name>} for a spell value. */
    protected final Setting param(String paramName, double defaultValue, String description) {
        return Settings.number("spell." + id + "." + paramName, defaultValue, description);
    }

    /** Marks this spell as upgrade of another spell. */
    public Spell replaces(String baseSpellId) {
        this.replaces = baseSpellId;
        return this;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Item getIcon() {
        return icon;
    }

    public int getManaCost() {
        return manaCost.getInt();
    }

    public int getCooldownSeconds() {
        return cooldown.getInt();
    }

    /** @return id of the spell this one upgrades, or {@code null}. */
    public String getReplaces() {
        return replaces;
    }

    /** Upgrades share the cooldown of their base spell. */
    public String cooldownKey() {
        return "spell." + (replaces != null ? replaces : id);
    }

    /** Formats a number without useless decimals ({@code 3} instead of {@code 3.0}). */
    protected static String fmt(double value) {
        return Settings.formatNumber(value);
    }
}
