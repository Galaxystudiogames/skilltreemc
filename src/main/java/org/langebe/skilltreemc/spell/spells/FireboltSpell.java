package org.langebe.skilltreemc.spell.spells;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.spell.SpellFx;
import net.minecraft.world.phys.Vec3;
import org.langebe.skilltreemc.spell.Spell;
import org.langebe.skilltreemc.spell.SpellUtil;

import java.util.List;

/** Firebolt: instant bolt that damages and ignites the first living entity in sight. */
public class FireboltSpell extends Spell {

    private final Setting damage;
    private final Setting fireSeconds;
    private final Setting range;

    public FireboltSpell(String id, String name, Item icon, int mana, int cooldown, float damage, float fireSeconds) {
        super(id, name, icon, mana, cooldown);
        this.damage = param("damage", damage, name + ": damage");
        this.fireSeconds = param("fire_seconds", fireSeconds, name + ": seconds the target burns");
        this.range = param("range", 20, name + ": range in blocks");
    }

    @Override
    public List<String> getDescription() {
        return List.of(fmt(damage.get()) + " damage + " + fmt(fireSeconds.get()) + "s fire",
                "Range: " + fmt(range.get()) + " blocks");
    }

    @Override
    public CastResult cast(ServerPlayer caster) {
        LivingEntity target = SpellUtil.lookTarget(caster, range.get(), LivingEntity.class);
        if (target == null || !SpellUtil.canHarm(caster, target)) {
            return CastResult.NO_TARGET;
        }
        ServerLevel level = caster.level();
        Vec3 hit = target.position().add(0, target.getBbHeight() / 2, 0);
        boolean advanced = getReplaces() != null;
        SpellUtil.line(level, ParticleTypes.FLAME, caster.getEyePosition().add(0, -0.3, 0), hit, 0.4);
        if (advanced) {
            SpellUtil.line(level, ParticleTypes.SMALL_FLAME, caster.getEyePosition().add(0, -0.3, 0), hit, 0.25);
        }
        SpellFx.fireboltImpact(level, hit, advanced);
        // Only set the target on fire if the hit went through (PvP off, team mate, i-frames, ...).
        if (target.hurtServer(level, caster.damageSources().indirectMagic(caster, caster), (float) damage.get())) {
            target.igniteForSeconds((float) fireSeconds.get());
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 1f);
        return CastResult.SUCCESS;
    }
}
