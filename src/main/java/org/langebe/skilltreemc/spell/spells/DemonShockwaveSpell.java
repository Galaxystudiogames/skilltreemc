package org.langebe.skilltreemc.spell.spells;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.spell.SpellCaster;
import org.langebe.skilltreemc.spell.SpellFx;
import org.langebe.skilltreemc.spell.Spell;
import org.langebe.skilltreemc.spell.SpellUtil;

import java.util.List;

/**
 * Demon Shockwave: hits everything around the caster (friendly fire on, only the caster
 * is safe). Damage and debuff duration scale with closeness to the epicentre.
 */
public class DemonShockwaveSpell extends Spell {

    private final Setting radius;
    private final Setting maxDamage;
    private final Setting maxDebuffSeconds;

    public DemonShockwaveSpell(String id, String name, Item icon, int mana, int cooldown,
                               double radius, float maxDamage, int maxDebuffSeconds) {
        super(id, name, icon, mana, cooldown);
        this.radius = param("radius", radius, name + ": radius in blocks");
        this.maxDamage = param("max_damage", maxDamage, name + ": damage at the centre");
        this.maxDebuffSeconds = param("max_debuff_seconds", maxDebuffSeconds, name + ": debuff duration at the centre");
    }

    @Override
    public List<String> getDescription() {
        return List.of("Up to " + fmt(maxDamage.get()) + " damage, radius " + fmt(radius.get()) + " blocks",
                "Slowness + Weakness + Blindness", "Stronger near the centre – hits allies too!");
    }

    @Override
    public CastResult cast(ServerPlayer caster) {
        double radius = this.radius.get();
        // Wind-up of 0.75 s (energy is sucked in), then the blast hits from the caster's position.
        SpellFx.demonShockwave(caster, radius, () -> blast(caster, radius), () -> SpellCaster.refund(caster, this));
        return CastResult.SUCCESS;
    }

    private void blast(ServerPlayer caster, double radius) {
        ServerLevel level = caster.level();
        Vec3 center = caster.position();
        for (LivingEntity target : SpellUtil.livingAround(level, center, radius)) {
            // Team mates are hit too (friendly fire permitting), but never with PvP off.
            if (!SpellUtil.canHarm(caster, target)) {
                continue;
            }
            double closeness = 1 - target.position().distanceTo(center) / radius; // 1 = centre, 0 = edge
            target.hurtServer(level, caster.damageSources().indirectMagic(caster, caster), (float) (maxDamage.get() * closeness));
            int ticks = Math.max(20, (int) (maxDebuffSeconds.get() * 20 * closeness));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 0));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
        }
    }
}
