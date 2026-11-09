package org.langebe.skilltreemc.spell.spells;

import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Item;
import org.langebe.skilltreemc.SkillTreeConfig;
import org.langebe.skilltreemc.gui.HexTargetMenu;
import org.langebe.skilltreemc.mechanics.CombatTracker;
import org.langebe.skilltreemc.mechanics.Feedback;
import org.langebe.skilltreemc.mechanics.Hex;
import org.langebe.skilltreemc.config.Setting;
import org.langebe.skilltreemc.spell.SpellCaster;
import org.langebe.skilltreemc.spell.SpellFx;
import org.langebe.skilltreemc.spell.SpellUtil;
import org.langebe.skilltreemc.spell.Spell;

import java.util.List;

/**
 * Hex of Corruption (Damage ultimate).
 *
 * <p>Casting opens a menu with the heads of all players in range; clicking a head
 * confirms the target. The target is struck by lightning (visual only, no fire) and for
 * {@code seconds} every heal hurts instead (see {@link Hex}), plus Weakness II and
 * Slowness II.
 *
 * <p>The design document asks for a Book &amp; Quill to type the name. Without a client
 * mod the server cannot open a book editor, so a player-head menu is used instead.
 */
public class HexSpell extends Spell {

    private final Setting seconds;
    private final Setting damage;
    private final Setting minHealth;

    public HexSpell(String id, String name, Item icon, int mana, int cooldown, int seconds,
                    double damage, double minHealth) {
        super(id, name, icon, mana, cooldown);
        this.seconds = param("seconds", seconds, name + ": duration of the hex in seconds");
        this.damage = param("damage", damage, name + ": true damage on impact in HP (2 HP = 1 heart), ignores armor");
        this.minHealth = param("min_health", minHealth,
                name + ": the impact damage never takes the target below this many HP (1 = half a heart)");
    }

    @Override
    public List<String> getDescription() {
        return List.of("Choose a player: lightning strike for " + fmt(damage.get() / 2) + " hearts",
                "(never lethal – leaves at least " + fmt(minHealth.get() / 2) + " heart),",
                "heals hurt instead for " + fmt(seconds.get()) + "s", "+ Weakness II + Slowness II");
    }

    @Override
    public CastResult cast(ServerPlayer caster) {
        List<ServerPlayer> targets = candidates(caster);
        if (targets.isEmpty()) {
            return CastResult.NO_TARGET;
        }
        HexTargetMenu.open(caster, this, targets);
        return CastResult.PENDING;
    }

    /** Players the caster can hex right now. */
    public List<ServerPlayer> candidates(ServerPlayer caster) {
        return caster.level().getPlayers(player -> SpellUtil.canHarm(caster, player)
                && player.distanceTo(caster) <= SkillTreeConfig.HEX_RANGE.get());
    }

    /** Applies the hex to the chosen target. */
    public void apply(ServerPlayer caster, ServerPlayer target) {
        // Vortex at the caster, a curse orb flies to the target, then the hex hits.
        SpellFx.hexCurse(caster, target, () -> strike(caster, target), () -> SpellCaster.refund(caster, this));
    }

    private void strike(ServerPlayer caster, ServerPlayer target) {
        if (target.isRemoved() || !SpellUtil.canHarm(caster, target)) {
            return;
        }
        ServerLevel level = target.level();
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.snapTo(target.position());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        dealImpactDamage(level, target);
        int ticks = seconds.getInt() * 20;
        Hex.apply(target, seconds.get());
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1));
        Feedback.chat(target, "You have been hexed by " + caster.getName().getString()
                + " – healing hurts you for " + fmt(seconds.get()) + "s!", ChatFormatting.DARK_PURPLE);
    }

    /**
     * True damage (ignores armor, Protection and absorption hearts):
     * health is lowered directly, but never below {@code min_health}, so the hit itself can
     * never kill. A target already at or below that health takes nothing.
     */
    private void dealImpactDamage(ServerLevel level, ServerPlayer target) {
        // True damage, but still respect real immunity (Creative, invulnerable flag, spawn invulnerability).
        if (target.isCreative() || target.isInvulnerableTo(level, level.damageSources().magic())) {
            return;
        }
        float floor = (float) Math.max(0.5, minHealth.get());
        float health = target.getHealth();
        float newHealth = Math.max(floor, health - (float) damage.get());
        if (newHealth >= health) {
            return;
        }
        target.setHealth(newHealth);
        // Hurt feedback without a real damage event: red flash + hurt sound for everyone nearby.
        level.getChunkSource().sendToTrackingPlayersAndSelf(target, new ClientboundHurtAnimationPacket(target));
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 0.8f);
        CombatTracker.tag(target);
    }
}
