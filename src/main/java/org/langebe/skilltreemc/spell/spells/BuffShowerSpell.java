package org.langebe.skilltreemc.spell.spells;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.langebe.skilltreemc.mechanics.Scheduler;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.spell.SpellFx;
import org.langebe.skilltreemc.spell.Spell;
import org.langebe.skilltreemc.spell.SpellUtil;

import java.util.List;

/**
 * Buff Shower: a zone at the cast position that stays for a few seconds. Every player
 * inside gets Speed + Strength and Fire Resistance.
 */
public class BuffShowerSpell extends Spell {

    private final Setting zoneSeconds;
    private final Setting radius;
    private final Setting buffSeconds;
    private final Setting fireResSeconds;

    public BuffShowerSpell(String id, String name, Item icon, int mana, int cooldown,
                           int zoneSeconds, double radius, int buffSeconds, int fireResSeconds) {
        super(id, name, icon, mana, cooldown);
        this.zoneSeconds = param("zone_seconds", zoneSeconds, name + ": how long the zone stays");
        this.radius = param("radius", radius, name + ": radius in blocks");
        this.buffSeconds = param("buff_seconds", buffSeconds, name + ": Speed + Strength duration in seconds");
        this.fireResSeconds = param("fire_res_seconds", fireResSeconds, name + ": Fire Resistance duration in seconds");
    }

    @Override
    public List<String> getDescription() {
        return List.of("Zone for " + fmt(zoneSeconds.get()) + "s, radius " + fmt(radius.get()) + " blocks",
                "Speed + Strength: " + duration(buffSeconds.getInt()),
                "Fire Resistance: " + duration(fireResSeconds.getInt()));
    }

    @Override
    public CastResult cast(ServerPlayer caster) {
        ServerLevel level = caster.level();
        Vec3 center = caster.position();
        level.playSound(null, caster.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1f, 1f);
        double r = radius.get();
        int buffTicks = buffSeconds.getInt() * 20;
        int fireResTicks = fireResSeconds.getInt() * 20;
        Scheduler.repeat(20, zoneSeconds.getInt(), run -> {
            for (ServerPlayer target : SpellUtil.alliesAround(caster, level, center, r)) {
                target.addEffect(new MobEffectInstance(MobEffects.SPEED, buffTicks, 0));
                target.addEffect(new MobEffectInstance(MobEffects.STRENGTH, buffTicks, 0));
                target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, fireResTicks, 0));
            }
            SpellUtil.circle(level, ParticleTypes.SPLASH, center, r, 48);
            SpellFx.buffShowerPulse(level, center, r, getReplaces() != null);
            level.sendParticles(ParticleTypes.FALLING_WATER, center.x, center.y + 4, center.z, 60, r / 2, 0.5, r / 2, 0);
        });
        return CastResult.SUCCESS;
    }

    private static String duration(int seconds) {
        return seconds >= 60 && seconds % 60 == 0 ? seconds / 60 + " min" : seconds + "s";
    }
}
