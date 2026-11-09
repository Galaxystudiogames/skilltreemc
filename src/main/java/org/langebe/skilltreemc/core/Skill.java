package org.langebe.skilltreemc.core;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.langebe.skilltreemc.SkillTreeConfig;
import org.langebe.skilltreemc.SkillTreeMC;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.data.SkillPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * One node in a skill tree (usually one level of a path).
 *
 * <h2>Creating a skill</h2>
 * Most skills need no own class – they are described with the builder methods and
 * registered in {@code skills/ModSkills}:
 *
 * <pre>{@code
 * SkillRegistry.register(new Skill("combat.l2", COMBAT)
 *         .level(2)
 *         .name("+1 Heart")
 *         .icon(Items.GOLDEN_APPLE)
 *         .requires("combat.l1")
 *         .attribute(Attributes.MAX_HEALTH, 2, Operation.ADD_VALUE));
 * }</pre>
 *
 * What a skill can give (all optional, can be combined):
 * <ul>
 *   <li>{@link #stat} – a value read by the mechanics (crit rate, mana, ...)</li>
 *   <li>{@link #attribute} – a vanilla attribute modifier (max health, fall damage, ...)</li>
 *   <li>{@link #effect} – a permanent status effect</li>
 *   <li>{@link #spell} – unlocks a spell for the Spell Orb</li>
 * </ul>
 * For anything else, subclass it and override {@link #onEnable}, {@link #onDisable}
 * or {@link #everySecond}.
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>{@link #onUnlock} – once, right after unlocking.</li>
 *   <li>{@link #onEnable} – skill becomes active: after unlocking, when toggled on,
 *       when the player joins and after respawning. Must be safe to repeat.</li>
 *   <li>{@link #onDisable} – skill is toggled off or reset.</li>
 *   <li>{@link #everySecond} – once per second while active.</li>
 * </ul>
 * Exceptions in these hooks are caught and logged, so a broken skill cannot crash the server.
 */
public class Skill {

    private final String id;
    private final SkillPath path;

    private String name;
    private List<String> description = List.of();
    private Item icon = Items.BOOK;
    private int level;
    private Integer cost;
    private List<String> requires = List.of();
    private List<String> requiresAny = List.of();
    private String exclusiveGroup;

    private final Map<Stat, DoubleSupplier> stats = new EnumMap<>(Stat.class);
    private final List<AttributeBonus> attributes = new ArrayList<>();
    private final List<EffectBonus> effects = new ArrayList<>();
    private final List<String> spells = new ArrayList<>();

    /**
     * @param id   unique, permanent id, e.g. {@code "combat.l1"}.
     *             Only lowercase letters, digits, {@code _} and {@code .}.
     *             It is stored in the player's save – never change it once released.
     * @param path the path whose menu shows this skill
     */
    public Skill(String id, SkillPath path) {
        this.id = id;
        this.path = path;
        this.name = id;
    }

    // ---------------------------------------------------------------------------------
    // Hooks – override these in a subclass for custom behaviour.
    // ---------------------------------------------------------------------------------

    /** Called exactly once, right after the player unlocked the skill (e.g. to give an item). */
    public void onUnlock(ServerPlayer player) {
    }

    /** Called when the skill becomes active. Must be safe to call multiple times. */
    public void onEnable(ServerPlayer player) {
    }

    /** Called when the skill is switched off. Undo everything {@link #onEnable} did. */
    public void onDisable(ServerPlayer player) {
    }

    /**
     * Called once per second while the skill is active.
     *
     * @param second running second counter; use e.g. {@code second % 5 == 0} for "every 5 s"
     */
    public void everySecond(ServerPlayer player, long second) {
    }

    // ---------------------------------------------------------------------------------
    // Builder methods – they return "this" so they can be chained.
    // ---------------------------------------------------------------------------------

    /** Display name in the menu. Defaults to the id. */
    public Skill name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Extra description lines shown in the tooltip (stats are listed automatically).
     * Lines may contain config placeholders like {@code {combat.stun.seconds}}, see
     * {@link org.langebe.skilltreemc.config.Settings#format}.
     */
    public Skill description(String... lines) {
        this.description = List.of(lines);
        return this;
    }

    /** Appends one description line (keeps the existing ones). */
    public Skill addDescription(String line) {
        List<String> lines = new ArrayList<>(description);
        lines.add(line);
        this.description = List.copyOf(lines);
        return this;
    }

    /** Item shown in the menu. Defaults to a book. */
    public Skill icon(Item icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Level of this node inside its path. It decides the row in the tree menu (shown as
     * "L5"); nodes with the same level appear side by side. If no explicit
     * {@link #cost} is set, the cost is calculated from the level with the curve in
     * {@link SkillTreeConfig#levelCost}.
     */
    public Skill level(int level) {
        this.level = level;
        return this;
    }

    /** Fixed price in XP levels (overrides the cost curve). */
    public Skill cost(int levels) {
        this.cost = levels;
        return this;
    }

    /** Ids of skills that must be unlocked before this one can be bought. */
    public Skill requires(String... skillIds) {
        this.requires = List.of(skillIds);
        return this;
    }

    /**
     * At least <i>one</i> of these skills must be unlocked – used after a choice level
     * ("whichever option you took at L6").
     */
    public Skill requiresAny(String... skillIds) {
        this.requiresAny = List.of(skillIds);
        return this;
    }

    /**
     * Only one skill per group can ever be unlocked – used for branch splits
     * ("DPS or Tank") and choice levels.
     */
    public Skill exclusiveGroup(String group) {
        this.exclusiveGroup = group;
        return this;
    }

    /** Gives a stat value while active. The highest value of all active skills counts. */
    public Skill stat(Stat stat, double value) {
        stats.put(stat, () -> value);
        return this;
    }

    /** Like {@link #stat(Stat, double)}, but the value comes from a config setting. */
    public Skill stat(Stat stat, Setting setting) {
        stats.put(stat, setting::get);
        return this;
    }

    /** Value of one tier (1 = first entry) of a per-tier config setting. */
    public Skill stat(Stat stat, Setting setting, int tier) {
        stats.put(stat, () -> setting.tier(tier));
        return this;
    }

    /** Adds a vanilla attribute modifier while active. Several skills stack. */
    public Skill attribute(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        return attribute(attribute, () -> amount, operation);
    }

    /**
     * Like {@link #attribute(Holder, double, AttributeModifier.Operation)}, but the amount is
     * read live (e.g. from a config setting). Changes are applied within a second.
     */
    public Skill attribute(Holder<Attribute> attribute, DoubleSupplier amount, AttributeModifier.Operation operation) {
        attributes.add(new AttributeBonus(attribute, amount, operation,
                SkillTreeMC.id("skill/" + id + "/" + attributes.size())));
        return this;
    }

    /** Gives a permanent status effect while active ({@code amplifier} 0 = level I). */
    public Skill effect(Holder<MobEffect> effect, int amplifier) {
        effects.add(new EffectBonus(effect, amplifier));
        return this;
    }

    /** Unlocks a spell (id from {@code spell/ModSpells}) for the Spell Orb. */
    public Skill spell(String spellId) {
        spells.add(spellId);
        return this;
    }

    // ---------------------------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public SkillPath getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public List<String> getDescription() {
        return description;
    }

    public Item getIcon() {
        return icon;
    }

    public int getLevel() {
        return level;
    }

    public int getCost() {
        return cost != null ? cost : SkillTreeConfig.levelCost(path, level);
    }

    public List<String> getRequires() {
        return requires;
    }

    public List<String> getRequiresAny() {
        return requiresAny;
    }

    /** @return the exclusive group or {@code null}. */
    public String getExclusiveGroup() {
        return exclusiveGroup;
    }

    /** Current stat values (read from the config each time). */
    public Map<Stat, Double> getStats() {
        Map<Stat, Double> result = new EnumMap<>(Stat.class);
        stats.forEach((stat, value) -> result.put(stat, value.getAsDouble()));
        return result;
    }

    public List<String> getSpells() {
        return Collections.unmodifiableList(spells);
    }

    @Override
    public String toString() {
        return id;
    }

    // ---------------------------------------------------------------------------------
    // Internal – called by SkillManager. Applies the built-in bonuses, then the hooks.
    // ---------------------------------------------------------------------------------

    final void enable(ServerPlayer player) {
        applyAttributes(player);
        refreshEffects(player);
        onEnable(player);
    }

    /** Sets the attribute modifiers; only touches them if the amount changed (config edits). */
    private void applyAttributes(ServerPlayer player) {
        for (AttributeBonus bonus : attributes) {
            AttributeInstance instance = player.getAttribute(bonus.attribute());
            if (instance == null) {
                continue;
            }
            double amount = bonus.amount().getAsDouble();
            AttributeModifier current = instance.getModifier(bonus.modifierId());
            if (current == null || current.amount() != amount) {
                instance.addOrUpdateTransientModifier(new AttributeModifier(bonus.modifierId(), amount, bonus.operation()));
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    final void disable(ServerPlayer player) {
        for (AttributeBonus bonus : attributes) {
            AttributeInstance instance = player.getAttribute(bonus.attribute());
            if (instance != null) {
                instance.removeModifier(bonus.modifierId());
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        for (EffectBonus bonus : effects) {
            MobEffectInstance current = player.getEffect(bonus.effect());
            // Only remove it if it is (very likely) ours, not e.g. a potion the player drank.
            if (current != null && current.getAmplifier() == bonus.amplifier()
                    && current.endsWithin(EffectBonus.DURATION)) {
                player.removeEffect(bonus.effect());
            }
        }
        onDisable(player);
    }

    final void tick(ServerPlayer player, long second) {
        applyAttributes(player);
        refreshEffects(player);
        everySecond(player, second);
    }

    private void refreshEffects(ServerPlayer player) {
        for (EffectBonus bonus : effects) {
            MobEffectInstance current = player.getEffect(bonus.effect());
            boolean stronger = current != null && current.getAmplifier() > bonus.amplifier();
            if (!stronger && (current == null || current.endsWithin(EffectBonus.REFRESH_BELOW))) {
                // ambient = true, particles = false, icon = true
                player.addEffect(new MobEffectInstance(bonus.effect(), EffectBonus.DURATION,
                        bonus.amplifier(), true, false, true));
            }
        }
    }

    private record AttributeBonus(Holder<Attribute> attribute, DoubleSupplier amount,
                                  AttributeModifier.Operation operation, Identifier modifierId) {
    }

    /**
     * Effects are given with a limited duration and refreshed every second, so they
     * disappear by themselves if the skill is removed. Above 200 ticks so night vision
     * never starts blinking.
     */
    private record EffectBonus(Holder<MobEffect> effect, int amplifier) {
        static final int DURATION = 400;
        static final int REFRESH_BELOW = 300;
    }
}
