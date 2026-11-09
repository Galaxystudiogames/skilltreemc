package org.langebe.skilltreemc.spell.spells;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.spell.Spell;
import org.langebe.skilltreemc.spell.SpellUtil;

import java.util.List;

/** Damage Aura: damages all players around the caster (not the caster). */
public class DamageAuraSpell extends Spell {

    private final Setting damage;
    private final Setting radius;

    public DamageAuraSpell(String id, String name, Item icon, int mana, int cooldown, float damage, double radius) {
        super(id, name, icon, mana, cooldown);
        this.damage = param("damage", damage, name + ": damage");
        this.radius = param("radius", radius, name + ": radius in blocks");
    }

    @Override
    public List<String> getDescription() {
        return List.of(fmt(damage.get()) + " damage to all players around you",
                "Radius: " + fmt(radius.get()) + " blocks");
    }

    @Override
    public CastResult cast(ServerPlayer caster) {
        ServerLevel level = caster.level();
        List<ServerPlayer> targets = SpellUtil.playersAround(level, caster.position(), radius.get()).stream()
                .filter(player -> SpellUtil.canHarm(caster, player)).toList();
        if (targets.isEmpty()) {
            return CastResult.NO_TARGET;
        }
        for (ServerPlayer target : targets) {
            target.hurtServer(level, caster.damageSources().indirectMagic(caster, caster), (float) damage.get());
        }
        SpellUtil.circle(level, ParticleTypes.SOUL_FIRE_FLAME, caster.position(), radius.get(), 40);
        level.playSound(null, caster.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, 0.8f);
        return CastResult.SUCCESS;
    }
}
