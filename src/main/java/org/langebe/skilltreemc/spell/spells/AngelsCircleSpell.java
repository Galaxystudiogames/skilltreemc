package org.langebe.skilltreemc.spell.spells;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.spell.SpellFx;
import org.langebe.skilltreemc.spell.Spell;
import org.langebe.skilltreemc.spell.SpellUtil;

import java.util.List;

/** Angels Circle: big heal plus Regeneration for everyone around the caster. */
public class AngelsCircleSpell extends Spell {

    private final Setting hearts;
    private final Setting radius;
    private final Setting regenSeconds;

    public AngelsCircleSpell(String id, String name, Item icon, int mana, int cooldown,
                             float hearts, double radius, int regenSeconds) {
        super(id, name, icon, mana, cooldown);
        this.hearts = param("hearts", hearts, name + ": hearts healed");
        this.radius = param("radius", radius, name + ": radius in blocks");
        this.regenSeconds = param("regen_seconds", regenSeconds, name + ": Regeneration I duration in seconds");
    }

    @Override
    public List<String> getDescription() {
        return List.of("Heals " + fmt(hearts.get()) + " hearts + Regeneration I for " + fmt(regenSeconds.get()) + "s",
                "Radius: " + fmt(radius.get()) + " blocks");
    }

    @Override
    public CastResult cast(ServerPlayer caster) {
        ServerLevel level = caster.level();
        for (ServerPlayer target : SpellUtil.alliesAround(caster, level, caster.position(), radius.get())) {
            target.heal((float) hearts.get() * 2);
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenSeconds.getInt() * 20, 0));
            level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + 1.2, target.getZ(), 5, 0.3, 0.3, 0.3, 0);
        }
        // Light wings, growing rings and falling light.
        SpellFx.angelsCircle(caster, radius.get());
        return CastResult.SUCCESS;
    }
}
