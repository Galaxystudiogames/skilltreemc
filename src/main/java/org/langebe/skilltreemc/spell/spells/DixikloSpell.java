package org.langebe.skilltreemc.spell.spells;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.mechanics.Scheduler;
import org.langebe.skilltreemc.spell.SpellFx;
import org.langebe.skilltreemc.spell.Spell;
import org.langebe.skilltreemc.spell.SpellUtil;

import java.util.List;
import java.util.Optional;

/**
 * Dixiklo: a stationary cloud where the caster looks, applying Weakness, Slowness and
 * Poison once per second to everything inside the caster may harm (see {@link SpellUtil#canHarm}).
 *
 * <p>Open design question "stationary, drifting or following the caster?" – implemented as
 * stationary (vanilla area effect cloud).
 */
public class DixikloSpell extends Spell {

    private final Setting lifetimeSeconds;
    private final Setting radius;
    private final Setting effectSeconds;
    private final Setting range;

    public DixikloSpell(String id, String name, Item icon, int mana, int cooldown,
                        double lifetimeSeconds, double radius, int effectSeconds) {
        super(id, name, icon, mana, cooldown);
        this.lifetimeSeconds = param("lifetime_seconds", lifetimeSeconds, name + ": how long the cloud stays (doc: 5–8s)");
        this.radius = param("radius", radius, name + ": cloud radius in blocks");
        this.effectSeconds = param("effect_seconds", effectSeconds, name + ": duration of each effect in seconds");
        this.range = param("range", 15, name + ": max distance where the cloud can be placed");
    }

    @Override
    public List<String> getDescription() {
        return List.of("Places a cloud where you look (" + fmt(lifetimeSeconds.get()) + "s)",
                "Weakness + Slowness + Poison", "Radius: " + fmt(radius.get()) + " blocks");
    }

    @Override
    public CastResult cast(ServerPlayer caster) {
        ServerLevel level = caster.level();
        Vec3 point = SpellUtil.lookEnd(caster, range.get());
        AreaEffectCloud cloud = new AreaEffectCloud(level, point.x, point.y, point.z);
        cloud.setOwner(caster);
        cloud.setRadius((float) radius.get());
        cloud.setDuration((int) Math.round(lifetimeSeconds.get() * 20));
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(0);
        cloud.setRadiusOnUse(0);
        cloud.setDurationOnUse(0);
        // The cloud itself is only the visual (coloured like the effects). The effects are applied
        // by our own pulse below, so PvP-off servers, team mates and the caster are left alone –
        // a vanilla cloud would poison everyone standing inside.
        int effectTicks = effectSeconds.getInt() * 20;
        List<MobEffectInstance> effects = List.of(
                new MobEffectInstance(MobEffects.WEAKNESS, effectTicks, 0),
                new MobEffectInstance(MobEffects.SLOWNESS, effectTicks, 0),
                new MobEffectInstance(MobEffects.POISON, effectTicks, 0));
        cloud.setPotionContents(new PotionContents(Optional.empty(),
                Optional.of(PotionContents.getColorOptional(effects).orElse(0x4E9331)), List.of(), Optional.empty()));
        double r = radius.get();
        int pulses = Math.max(1, (int) Math.ceil(lifetimeSeconds.get()));
        Scheduler.repeat(20, pulses, run -> {
            if (cloud.isRemoved()) {
                return;
            }
            for (LivingEntity target : SpellUtil.livingAround(level, point, r)) {
                if (Math.abs(target.getY() - point.y) <= 2 && SpellUtil.canHarm(caster, target)) {
                    effects.forEach(effect -> target.addEffect(new MobEffectInstance(effect), cloud));
                }
            }
        });
        level.addFreshEntity(cloud);
        level.playSound(null, caster.blockPosition(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1f, 0.7f);
        SpellFx.dixiklo(level, point, radius.get(), (int) Math.round(lifetimeSeconds.get() * 20));
        return CastResult.SUCCESS;
    }
}
