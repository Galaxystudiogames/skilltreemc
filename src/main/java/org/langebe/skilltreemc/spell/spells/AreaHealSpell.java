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

/** Area Heal: heals all players (including the caster) around the caster. */
public class AreaHealSpell extends Spell {

    private final Setting hearts;
    private final Setting radius;

    public AreaHealSpell(String id, String name, Item icon, int mana, int cooldown, float hearts, double radius) {
        super(id, name, icon, mana, cooldown);
        this.hearts = param("hearts", hearts, name + ": hearts healed");
        this.radius = param("radius", radius, name + ": radius in blocks");
    }

    @Override
    public List<String> getDescription() {
        return List.of("Heals " + fmt(hearts.get()) + " hearts (you + allies)", "Radius: " + fmt(radius.get()) + " blocks");
    }

    @Override
    public CastResult cast(ServerPlayer caster) {
        ServerLevel level = caster.level();
        for (ServerPlayer target : SpellUtil.alliesAround(caster, level, caster.position(), radius.get())) {
            target.heal((float) hearts.get() * 2);
            level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + 1.2, target.getZ(), 4, 0.3, 0.3, 0.3, 0);
        }
        SpellUtil.circle(level, ParticleTypes.HAPPY_VILLAGER, caster.position(), radius.get(), 32);
        level.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
        return CastResult.SUCCESS;
    }
}
