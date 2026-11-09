package org.langebe.skilltreemc.spell.spells;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.spell.SpellFx;
import org.langebe.skilltreemc.spell.Spell;
import org.langebe.skilltreemc.spell.SpellUtil;

import java.util.List;

/**
 * Sanctum (Healer ultimate): Absorption IV + Regeneration II for all allies around the
 * caster, with particle connections to every ally and a conduit/beacon sound.
 */
public class SanctumSpell extends Spell {

    private final Setting radius;
    private final Setting seconds;

    public SanctumSpell(String id, String name, Item icon, int mana, int cooldown, double radius, int seconds) {
        super(id, name, icon, mana, cooldown);
        this.radius = param("radius", radius, name + ": radius in blocks");
        this.seconds = param("seconds", seconds, name + ": effect duration in seconds");
    }

    @Override
    public List<String> getDescription() {
        return List.of("Absorption IV + Regeneration II for " + fmt(seconds.get()) + "s",
                "Radius: " + fmt(radius.get()) + " blocks");
    }

    @Override
    public CastResult cast(ServerPlayer caster) {
        ServerLevel level = caster.level();
        List<ServerPlayer> allies = SpellUtil.alliesAround(caster, level, caster.position(), radius.get());
        for (ServerPlayer target : allies) {
            target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, seconds.getInt() * 20, 3));
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, seconds.getInt() * 20, 1));
        }
        // Light pillar, golden rune circle, halos and pulsing connections to every ally.
        SpellFx.sanctum(caster, allies, radius.get(), seconds.getInt());
        level.playSound(null, caster.blockPosition(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1f);
        level.playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1f);
        return CastResult.SUCCESS;
    }
}
