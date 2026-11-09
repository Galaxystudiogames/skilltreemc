package org.langebe.skilltreemc.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, persistent runtime state of a player: mana, cooldowns and spell setup.
 *
 * <p>Stored next to {@link PlayerSkills} (see {@link SkillData}). Cooldowns are saved as
 * the world game time at which they end, so logging out does not reset them.
 *
 * @param mana         current mana
 * @param cooldowns    cooldown key → game time (ticks) when it is ready again
 * @param fastSlots    spell id per fast-spell slot ("" = empty)
 * @param selectedSlot index of the fast slot that is cast with right-click
 * @param hideCooldowns {@code true} if the player turned the cooldown sidebar off ({@code /st cooldowns})
 * @param hideAura      {@code true} if the player turned the mastery aura off ({@code /st aura})
 */
public record PlayerState(float mana, Map<String, Long> cooldowns, List<String> fastSlots, int selectedSlot,
                          boolean hideCooldowns, boolean hideAura) {

    public static final PlayerState EMPTY = new PlayerState(0, Map.of(), List.of(), 0, false, false);

    public static final Codec<PlayerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("mana", 0f).forGetter(PlayerState::mana),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of())
                    .forGetter(PlayerState::cooldowns),
            Codec.STRING.listOf().optionalFieldOf("fast_slots", List.of()).forGetter(PlayerState::fastSlots),
            Codec.INT.optionalFieldOf("selected_slot", 0).forGetter(PlayerState::selectedSlot),
            Codec.BOOL.optionalFieldOf("hide_cooldowns", false).forGetter(PlayerState::hideCooldowns),
            Codec.BOOL.optionalFieldOf("hide_aura", false).forGetter(PlayerState::hideAura)
    ).apply(instance, PlayerState::new));

    public PlayerState {
        cooldowns = Map.copyOf(cooldowns);
        fastSlots = List.copyOf(fastSlots);
    }

    public PlayerState withMana(float newMana) {
        return new PlayerState(newMana, cooldowns, fastSlots, selectedSlot, hideCooldowns, hideAura);
    }

    /**
     * Sets a cooldown. Cooldowns that have already run out (at {@code now}) are dropped, so the
     * saved map does not keep growing with stale keys.
     */
    public PlayerState withCooldown(String key, long readyAt, long now) {
        Map<String, Long> copy = new HashMap<>(cooldowns);
        copy.values().removeIf(ready -> ready <= now);
        if (readyAt > now) {
            copy.put(key, readyAt);
        } else {
            copy.remove(key);
        }
        return new PlayerState(mana, copy, fastSlots, selectedSlot, hideCooldowns, hideAura);
    }

    /** Sets the spell in a fast slot; the list grows as needed. */
    public PlayerState withFastSlot(int index, String spellId) {
        List<String> copy = new ArrayList<>(fastSlots);
        while (copy.size() <= index) {
            copy.add("");
        }
        copy.set(index, spellId);
        return new PlayerState(mana, cooldowns, copy, selectedSlot, hideCooldowns, hideAura);
    }

    public PlayerState withSelectedSlot(int index) {
        return new PlayerState(mana, cooldowns, fastSlots, index, hideCooldowns, hideAura);
    }

    public PlayerState withHideCooldowns(boolean hide) {
        return new PlayerState(mana, cooldowns, fastSlots, selectedSlot, hide, hideAura);
    }

    public PlayerState withHideAura(boolean hide) {
        return new PlayerState(mana, cooldowns, fastSlots, selectedSlot, hideCooldowns, hide);
    }

    /** @return the spell id in a slot, or "" if empty. */
    public String fastSlot(int index) {
        return index >= 0 && index < fastSlots.size() ? fastSlots.get(index) : "";
    }
}
