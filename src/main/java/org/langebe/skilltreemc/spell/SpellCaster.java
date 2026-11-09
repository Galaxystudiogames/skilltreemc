package org.langebe.skilltreemc.spell;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import org.langebe.skilltreemc.core.Cooldowns;
import org.langebe.skilltreemc.core.Skill;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.core.Stat;
import org.langebe.skilltreemc.data.SkillData;
import org.langebe.skilltreemc.mechanics.Feedback;
import org.langebe.skilltreemc.mechanics.Fx;
import org.langebe.skilltreemc.mechanics.Stun;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything around casting: which spells a player has, the fast-spell slots, and the
 * checks (stun, mana, cooldown) before a spell runs.
 */
public final class SpellCaster {

    private SpellCaster() {
    }

    // ---------------------------------------------------------------------------------
    // Available spells & slots
    // ---------------------------------------------------------------------------------

    /**
     * Spells the player can use: all spells of active skills, where upgrades replace
     * their base spell.
     */
    public static List<Spell> availableSpells(ServerPlayer player) {
        Set<String> ids = new LinkedHashSet<>();
        for (Skill skill : SkillManager.activeSkills(player)) {
            ids.addAll(skill.getSpells());
        }
        List<Spell> result = new ArrayList<>();
        for (String id : ids) {
            Spell spell = resolve(id, ids);
            if (spell != null && !result.contains(spell)) {
                result.add(spell);
            }
        }
        return result;
    }

    /** Follows upgrades: returns the upgrade of {@code id} if the player has it. */
    private static Spell resolve(String id, Set<String> owned) {
        Spell current = SpellRegistry.get(id);
        boolean changed = true;
        while (current != null && changed) {
            changed = false;
            for (String other : owned) {
                Spell candidate = SpellRegistry.get(other);
                if (candidate != null && current.getId().equals(candidate.getReplaces())) {
                    current = candidate;
                    changed = true;
                    break;
                }
            }
        }
        return current;
    }

    public static int slotCount(ServerPlayer player) {
        return Math.min(6, (int) SkillManager.getStat(player, Stat.SPELL_SLOTS));
    }

    /** Spell in a fast slot (already upgraded), or {@code null} if empty / no longer available. */
    public static Spell spellInSlot(ServerPlayer player, int slot) {
        String id = SkillData.getState(player).fastSlot(slot);
        if (id.isEmpty()) {
            return null;
        }
        List<Spell> available = availableSpells(player);
        Set<String> ids = new LinkedHashSet<>();
        for (Skill skill : SkillManager.activeSkills(player)) {
            ids.addAll(skill.getSpells());
        }
        Spell spell = resolve(id, ids);
        return spell != null && available.contains(spell) ? spell : null;
    }

    public static void assign(ServerPlayer player, int slot, Spell spell) {
        SkillData.modifyState(player, state -> state.withFastSlot(slot, spell == null ? "" : spell.getId()));
    }

    public static int selectedSlot(ServerPlayer player) {
        int slots = slotCount(player);
        int selected = SkillData.getState(player).selectedSlot();
        return slots == 0 ? 0 : Math.floorMod(selected, slots);
    }

    public static void select(ServerPlayer player, int slot) {
        SkillData.modifyState(player, state -> state.withSelectedSlot(slot));
    }

    /** Sneak + right-click: jump to the next fast slot and show it. */
    public static void cycle(ServerPlayer player) {
        int slots = slotCount(player);
        if (slots == 0) {
            Feedback.deny(player, "You have no spell slots.");
            return;
        }
        int next = (selectedSlot(player) + 1) % slots;
        select(player, next);
        showSelection(player);
        Feedback.sound(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.5f);
    }

    public static void showSelection(ServerPlayer player) {
        int slot = selectedSlot(player);
        Spell spell = spellInSlot(player, slot);
        String text = "Slot " + (slot + 1) + ": " + (spell == null ? "empty"
                : spell.getName() + " (" + spell.getManaCost() + " Mana)");
        Feedback.actionBar(player, text, ChatFormatting.LIGHT_PURPLE);
    }

    // ---------------------------------------------------------------------------------
    // Casting
    // ---------------------------------------------------------------------------------

    /** Right-click with the orb: cast the spell in the selected slot. */
    public static void castSelected(ServerPlayer player) {
        Spell spell = spellInSlot(player, selectedSlot(player));
        if (spell == null) {
            Feedback.deny(player, "No spell in this slot – Sneak + offhand key opens the spell menu.");
            return;
        }
        tryCast(player, spell);
    }

    public static void tryCast(ServerPlayer player, Spell spell) {
        if (!canCast(player, spell)) {
            return;
        }
        Spell.CastResult result = spell.cast(player);
        switch (result) {
            case SUCCESS -> charge(player, spell);
            case NO_TARGET -> Feedback.deny(player, "No target.");
            case PENDING -> { /* finished later by completePending */ }
        }
    }

    /**
     * Finishes a spell that needed extra input (e.g. Hex target menu). Checks again,
     * runs {@code effect} and charges mana/cooldown.
     */
    public static void completePending(ServerPlayer player, Spell spell, Runnable effect) {
        if (!canCast(player, spell)) {
            return;
        }
        effect.run();
        charge(player, spell);
    }

    private static boolean canCast(ServerPlayer player, Spell spell) {
        // The spell may have been lost since the menu was opened (skill disabled / reset / path changed).
        if (!availableSpells(player).contains(spell)) {
            Feedback.deny(player, "You can no longer use " + spell.getName() + ".");
            return false;
        }
        if (Stun.isStunned(player)) {
            Feedback.deny(player, "You are stunned!");
            return false;
        }
        if (!Cooldowns.isReady(player, spell.cooldownKey())) {
            Feedback.deny(player, spell.getName() + " is on cooldown ("
                    + Cooldowns.remainingSeconds(player, spell.cooldownKey()) + "s)");
            return false;
        }
        if (Mana.get(player) < spell.getManaCost()) {
            Feedback.deny(player, "Not enough mana (" + spell.getManaCost() + " needed)");
            return false;
        }
        return true;
    }

    private static void charge(ServerPlayer player, Spell spell) {
        Mana.tryConsume(player, spell.getManaCost());
        Cooldowns.start(player, spell.cooldownKey(), spell.getCooldownSeconds());
        Fx.spellCast(player);
        Feedback.actionBar(player, spell.getName() + "!", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
    }

    /**
     * Gives back mana and cooldown of a spell whose animation was interrupted before it had any
     * effect (caster died / logged out / changed dimension, target gone).
     */
    public static void refund(ServerPlayer player, Spell spell) {
        Mana.set(player, Mana.get(player) + spell.getManaCost());
        Cooldowns.start(player, spell.cooldownKey(), 0);
        if (!player.isRemoved()) {
            Feedback.actionBar(player, spell.getName() + " fizzled – mana refunded", ChatFormatting.GRAY);
        }
    }

    /** Remaining cooldown text for menus, or "" if ready. */
    public static String cooldownText(ServerPlayer player, Spell spell) {
        long seconds = Cooldowns.remainingSeconds(player, spell.cooldownKey());
        return seconds > 0 ? "Cooldown: " + seconds + "s" : "";
    }
}
