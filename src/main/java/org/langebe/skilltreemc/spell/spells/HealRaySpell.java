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

/** Heal Ray: heals the first player in a line in front of the caster. */
public class HealRaySpell extends Spell {

    private final Setting hearts;
    private final Setting range;

    public HealRaySpell(String id, String name, Item icon, int mana, int cooldown, float hearts, double range) {
        super(id, name, icon, mana, cooldown);
        this.hearts = param("hearts", hearts, name + ": hearts healed");
        this.range = param("range", range, name + ": range in blocks");
    }

    @Override
    public List<String> getDescription() {
        return List.of("Heals the player you look at by " + fmt(hearts.get()) + " hearts",
                "Range: " + fmt(range.get()) + " blocks");
    }

    @Override
    public CastResult cast(ServerPlayer caster) {
        ServerPlayer target = SpellUtil.lookTarget(caster, range.get(), ServerPlayer.class);
        if (target == null || !SpellUtil.isAlly(caster, target)) {
            return CastResult.NO_TARGET;
        }
        ServerLevel level = caster.level();
        target.heal((float) hearts.get() * 2);
        SpellUtil.line(level, ParticleTypes.END_ROD, caster.getEyePosition().add(0, -0.3, 0),
                target.position().add(0, 1, 0), 0.3);
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + 1.2, target.getZ(), 5, 0.3, 0.3, 0.3, 0);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.5f);
        return CastResult.SUCCESS;
    }
}
