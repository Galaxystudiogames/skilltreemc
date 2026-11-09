package org.langebe.skilltreemc.spell;

import net.minecraft.world.item.Items;
import org.langebe.skilltreemc.spell.spells.AngelsCircleSpell;
import org.langebe.skilltreemc.spell.spells.AreaHealSpell;
import org.langebe.skilltreemc.spell.spells.BuffShowerSpell;
import org.langebe.skilltreemc.spell.spells.DamageAuraSpell;
import org.langebe.skilltreemc.spell.spells.DemonShockwaveSpell;
import org.langebe.skilltreemc.spell.spells.DixikloSpell;
import org.langebe.skilltreemc.spell.spells.FireboltSpell;
import org.langebe.skilltreemc.spell.spells.HealRaySpell;
import org.langebe.skilltreemc.spell.spells.HexSpell;
import org.langebe.skilltreemc.spell.spells.SanctumSpell;

/**
 * <b>All spells with their balance values.</b>
 *
 * <p>Constructor order: id, name, icon, mana cost, cooldown (s), then spell-specific values.
 * These numbers are only the <b>defaults</b> – every value is a config setting
 * ({@code spell.<id>.<name>}) that can be changed in {@code config/skilltreemc.json} or with
 * {@code /st admin config set}. Values from the design document are used as-is; values the
 * document leaves open are marked "chosen".
 */
public final class ModSpells {

    private ModSpells() {
    }

    public static void register() {
        // ----- Basis (L1 / L3) -------------------------------------------------------
        SpellRegistry.register(new AreaHealSpell("area_heal", "Area Heal", Items.GLISTERING_MELON_SLICE,
                10, 60, 3, 3));
        SpellRegistry.register(new HealRaySpell("heal_ray", "Heal Ray", Items.END_ROD,
                10, 30, 3, 5)); // mana + cooldown chosen: single target, so cheaper than Area Heal
        SpellRegistry.register(new FireboltSpell("firebolt", "Firebolt", Items.FIRE_CHARGE,
                15, 40, 3, 3)); // fire duration chosen
        SpellRegistry.register(new DamageAuraSpell("damage_aura", "Damage Aura", Items.BLAZE_POWDER,
                20, 70, 4, 3));

        // ----- Healer / Buffer -------------------------------------------------------
        SpellRegistry.register(new AreaHealSpell("area_heal_advanced", "Advanced Area Heal", Items.GLISTERING_MELON_SLICE,
                15, 60, 4, 5).replaces("area_heal")); // chosen: +1 heart, +2 radius
        SpellRegistry.register(new HealRaySpell("heal_ray_advanced", "Advanced Heal Ray", Items.END_ROD,
                15, 30, 4.5f, 8).replaces("heal_ray")); // chosen: +1.5 hearts, +3 range
        SpellRegistry.register(new BuffShowerSpell("buff_shower", "Buff Shower", Items.SPLASH_POTION,
                45, 75, 5, 10, 90, 8 * 60)); // Speed/Strength 90s chosen (Buff Shower+ raises it to 3 min)
        SpellRegistry.register(new AngelsCircleSpell("angels_circle", "Angels Circle", Items.FEATHER,
                80, 120, 4, 8, 5));
        SpellRegistry.register(new BuffShowerSpell("buff_shower_plus", "Buff Shower+", Items.SPLASH_POTION,
                70, 80, 5, 10, 3 * 60, 8 * 60).replaces("buff_shower"));
        SpellRegistry.register(new SanctumSpell("sanctum", "Sanctum", Items.BEACON,
                150, 200, 12, 8));

        // ----- Damage / Debuffer -----------------------------------------------------
        SpellRegistry.register(new FireboltSpell("firebolt_advanced", "Advanced Firebolt", Items.FIRE_CHARGE,
                25, 40, 5, 4).replaces("firebolt")); // chosen: +2 damage, +10 mana
        SpellRegistry.register(new DamageAuraSpell("damage_aura_advanced", "Advanced Damage Aura", Items.BLAZE_POWDER,
                30, 70, 6, 4).replaces("damage_aura")); // chosen: +2 damage, +1 radius
        SpellRegistry.register(new DixikloSpell("dixiklo", "Dixiklo", Items.POISONOUS_POTATO,
                45, 75, 6.5, 3, 5)); // lifetime from doc (5–8s); radius + effect duration chosen
        SpellRegistry.register(new DemonShockwaveSpell("demon_shockwave", "Demon Shockwave", Items.ECHO_SHARD,
                // Max damage: Magic damage ignores armor points, only Protection counts.
                // Full Protection IV = 64 % less damage. 58.5 → ~20 HP (10 hearts) at 1 block
                // from the centre against full Protection IV netherite: 58.5 × 0.95 × 0.36 ≈ 20.
                90, 180, 20, 58.5f, 8)); // max debuff duration chosen
        SpellRegistry.register(new HexSpell("hex", "Hex of Corruption", Items.WITHER_ROSE,
                130, 220, 12, 10, 1)); // 10 HP = 5 hearts, never below 1 HP (half a heart)

        SpellRegistry.validate();
    }
}
