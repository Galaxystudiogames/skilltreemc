package org.langebe.skilltreemc.mechanics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.langebe.skilltreemc.core.Cooldowns;
import org.langebe.skilltreemc.core.MarkedItems;
import org.langebe.skilltreemc.gui.ItemBuilder;
import org.langebe.skilltreemc.spell.SpellUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.langebe.skilltreemc.SkillTreeConfig.*;

/**
 * Active Utils perks, used with the <b>Utility Compass</b> (a vanilla compass with a marker):
 * <ul>
 *   <li>Right-click – use the selected active perk</li>
 *   <li>Sneak + right-click – select the next active perk</li>
 * </ul>
 * Eagle Eye and Tracker also turn the compass needle towards the tracked player.
 *
 * <p>Perks: Grapple (L6), Eagle Eye / Smoke Screen (L8), Phase Dash (L9), Tracker (L10).
 */
public final class UtilityActives {

    private static final String MARKER = "skilltreemc_utility_compass";

    /** Selected active perk per player (index into {@link #ownedActives}). Not saved. */
    private static final Map<UUID, Integer> SELECTED = new HashMap<>();
    /** Running Eagle Eye / Tracker per player. Not saved. */
    private static final Map<UUID, Tracking> TRACKING = new HashMap<>();

    private UtilityActives() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UtilityActives::tick);
    }

    // ---------------------------------------------------------------------------------
    // Compass item
    // ---------------------------------------------------------------------------------

    public static ItemStack createCompass() {
        ItemStack stack = ItemBuilder.of(Items.COMPASS)
                .name("Utility Compass", ChatFormatting.YELLOW, ChatFormatting.BOLD)
                .lore("Right-click: use selected perk")
                .lore("Sneak + right-click: next perk")
                .glint()
                .build();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isCompass(ItemStack stack) {
        if (!stack.is(Items.COMPASS)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(MARKER, false);
    }

    /**
     * Gives the player a compass unless they already carry one.
     *
     * @param dropIfFull drop it at the player's feet if the inventory is full (otherwise nothing is given)
     */
    public static MarkedItems.GiveResult giveCompass(ServerPlayer player, boolean dropIfFull) {
        return MarkedItems.giveOnce(player, UtilityActives::createCompass, UtilityActives::isCompass, dropIfFull);
    }

    /** A dropped compass must not keep pointing at the last tracked player. */
    public static void onDrop(ItemStack stack) {
        if (isCompass(stack)) {
            stack.remove(DataComponents.LODESTONE_TRACKER);
        }
    }

    // ---------------------------------------------------------------------------------
    // Selecting & using
    // ---------------------------------------------------------------------------------

    public static List<UtilityPerks> ownedActives(ServerPlayer player) {
        List<UtilityPerks> result = new ArrayList<>();
        for (UtilityPerks perk : UtilityPerks.values()) {
            if (perk.isActive() && perk.has(player)) {
                result.add(perk);
            }
        }
        return result;
    }

    private static UtilityPerks selected(ServerPlayer player) {
        List<UtilityPerks> owned = ownedActives(player);
        if (owned.isEmpty()) {
            return null;
        }
        return owned.get(Math.floorMod(SELECTED.getOrDefault(player.getUUID(), 0), owned.size()));
    }

    public static void cycle(ServerPlayer player) {
        List<UtilityPerks> owned = ownedActives(player);
        if (owned.isEmpty()) {
            Feedback.deny(player, "You have no active Utils perk.");
            return;
        }
        SELECTED.merge(player.getUUID(), 1, Integer::sum);
        Feedback.actionBar(player, "Selected: " + name(selected(player)), ChatFormatting.YELLOW);
        Feedback.sound(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.5f);
    }

    public static void use(ServerPlayer player) {
        UtilityPerks perk = selected(player);
        if (perk == null) {
            Feedback.deny(player, "You have no active Utils perk.");
            return;
        }
        if (Stun.isStunned(player)) {
            Feedback.deny(player, "You are stunned!");
            return;
        }
        int tier = perk.tier(player);
        switch (perk) {
            case GRAPPLE -> grapple(player, tier);
            case EAGLE_EYE -> eagleEye(player, tier);
            case SMOKE_SCREEN -> smokeScreen(player, tier);
            case PHASE_DASH -> phaseDash(player, tier);
            case TRACKER -> tracker(player, tier);
            default -> { }
        }
    }

    // ---------------------------------------------------------------------------------
    // Grapple
    // ---------------------------------------------------------------------------------

    private static void grapple(ServerPlayer player, int tier) {
        int charges = tier >= 3 ? 2 : 1;
        if (!useCharge(player, "grapple", charges, GRAPPLE_COOLDOWN.tierInt(tier), "Grapple")) {
            return;
        }
        Vec3 velocity = player.getLookAngle().scale(GRAPPLE_STRENGTH.get()).add(0, GRAPPLE_LIFT.get(), 0);
        player.setDeltaMovement(velocity);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        Fx.grapple(player);
    }

    /** Grapple tier 5: hitting a player gives back one charge. */
    public static void onPlayerHit(ServerPlayer attacker) {
        if (UtilityPerks.GRAPPLE.tier(attacker) < 5) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            String key = "grapple." + i;
            if (!Cooldowns.isReady(attacker, key)) {
                Cooldowns.start(attacker, key, 0);
                Feedback.proc(attacker, "↺ GRAPPLE +1", ChatFormatting.YELLOW);
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Phase Dash
    // ---------------------------------------------------------------------------------

    private static void phaseDash(ServerPlayer player, int tier) {
        int charges = tier >= 3 ? 2 : 1;
        if (!useCharge(player, "phase_dash", charges, DASH_COOLDOWN.getInt(), "Phase Dash")) {
            return;
        }
        ServerLevel level = player.level();
        Vec3 start = player.position();
        Vec3 direction = player.getLookAngle();
        double distance = DASH_DISTANCE.tier(tier);

        // Walk the line in small steps and keep the last position where the player fits.
        Vec3 target = start;
        for (double d = 0.25; d <= distance; d += 0.25) {
            Vec3 candidate = start.add(direction.scale(d));
            AABB box = player.getBoundingBox().move(candidate.subtract(start));
            if (!level.noCollision(player, box)) {
                break;
            }
            target = candidate;
        }

        if (tier >= 4) {
            Vec3 from = start;
            Vec3 to = target;
            for (ServerPlayer other : SpellUtil.playersAround(level, start.lerp(to, 0.5), from.distanceTo(to) / 2 + 1.5)) {
                if (SpellUtil.canHarm(player, other) && distanceToSegment(other.position(), from, to) < 1.2) {
                    Stun.apply(other, DASH_STUN_SECONDS.get());
                }
            }
        }

        SpellUtil.line(level, ParticleTypes.PORTAL, start.add(0, 1, 0), target.add(0, 1, 0), 0.3);
        player.teleportTo(target.x, target.y, target.z);
        Fx.phaseDash(level, start, target);
    }

    private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lengthSqr = ab.lengthSqr();
        double t = lengthSqr == 0 ? 0 : Math.max(0, Math.min(1, point.subtract(a).dot(ab) / lengthSqr));
        return point.distanceTo(a.add(ab.scale(t)));
    }

    // ---------------------------------------------------------------------------------
    // Smoke Screen
    // ---------------------------------------------------------------------------------

    private static void smokeScreen(ServerPlayer caster, int tier) {
        if (!checkCooldown(caster, "smoke_screen", "Smoke Screen")) {
            return;
        }
        Cooldowns.start(caster, "smoke_screen", SMOKE_COOLDOWN.tier(tier));
        ServerLevel level = caster.level();
        Vec3 center = caster.position();
        int blindTicks = SMOKE_BLIND_SECONDS.tierInt(tier) * 20;
        // Tier 3+: the cloud stays and also hits players walking in.
        int pulses = tier >= 3 ? SMOKE_LINGER_SECONDS.getInt() * 2 : 1;

        Feedback.sound(caster, SoundEvents.FIRECHARGE_USE, 0.6f);
        level.sendParticles(ParticleTypes.WHITE_SMOKE, center.x, center.y + 1, center.z, 40, SMOKE_RADIUS.get() / 2, 0.6,
                SMOKE_RADIUS.get() / 2, 0.05);
        Scheduler.repeat(10, pulses, run -> {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 1, center.z,
                    60, SMOKE_RADIUS.get() / 2, 0.8, SMOKE_RADIUS.get() / 2, 0.01);
            for (ServerPlayer target : SpellUtil.playersAround(level, center, SMOKE_RADIUS.get())) {
                if (target == caster) {
                    if (tier >= 4) {
                        target.addEffect(new MobEffectInstance(MobEffects.SPEED, 15, 0, true, false, true));
                    }
                    continue;
                }
                if (!SpellUtil.canHarm(caster, target)) {
                    continue;
                }
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindTicks, 0));
                if (tier >= 2) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, blindTicks, 0));
                }
            }
        });
    }

    // ---------------------------------------------------------------------------------
    // Eagle Eye & Tracker
    // ---------------------------------------------------------------------------------

    private static void eagleEye(ServerPlayer player, int tier) {
        if (!checkCooldown(player, "eagle_eye", "Eagle Eye")) {
            return;
        }
        Cooldowns.start(player, "eagle_eye", EAGLE_COOLDOWN.tier(tier));
        startTracking(player, UtilityPerks.EAGLE_EYE, tier, EAGLE_SECONDS.tierInt(tier));
    }

    private static void tracker(ServerPlayer player, int tier) {
        if (!checkCooldown(player, "tracker", "Tracker")) {
            return;
        }
        if (findTargets(player, UtilityPerks.TRACKER, tier).isEmpty()) {
            Feedback.deny(player, "No player found.");
            return;
        }
        Cooldowns.start(player, "tracker", TRACKER_COOLDOWN.tier(tier));
        startTracking(player, UtilityPerks.TRACKER, tier, TRACKER_SECONDS.getInt());
    }

    private static void startTracking(ServerPlayer player, UtilityPerks perk, int tier, int seconds) {
        TRACKING.put(player.getUUID(), new Tracking(perk, tier, Cooldowns.now(player) + seconds * 20L));
        Fx.tracking(player);
        updateTracking(player, TRACKING.get(player.getUUID()));
    }

    /**
     * Eagle Eye: all players in the radius (invisible ones only from tier 4).
     * Tracker: the nearest player, from tier 3 the nearest three.
     */
    private static List<ServerPlayer> findTargets(ServerPlayer player, UtilityPerks perk, int tier) {
        double radius = perk == UtilityPerks.EAGLE_EYE ? EAGLE_RADIUS.tier(tier) : Double.MAX_VALUE;
        boolean seeInvisible = perk == UtilityPerks.EAGLE_EYE && tier >= 4;
        List<ServerPlayer> targets = new ArrayList<>(player.level().getPlayers(other -> other != player
                && other.isAlive() && !other.isSpectator()
                && (seeInvisible || !other.isInvisible())
                && other.distanceTo(player) <= radius));
        targets.sort(Comparator.comparingDouble(other -> other.distanceTo(player)));
        if (perk == UtilityPerks.TRACKER) {
            return targets.subList(0, Math.min(targets.size(), tier >= 3 ? 3 : 1));
        }
        return targets;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        long now = Cooldowns.now(server);
        for (UUID id : new ArrayList<>(TRACKING.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            Tracking tracking = TRACKING.get(id);
            if (player == null || now >= tracking.endTick) {
                TRACKING.remove(id);
                if (player != null) {
                    pointCompasses(player, null);
                    Feedback.actionBar(player, name(tracking.perk) + " ended", ChatFormatting.GRAY);
                }
                continue;
            }
            updateTracking(player, tracking);
        }
    }

    private static void updateTracking(ServerPlayer player, Tracking tracking) {
        List<ServerPlayer> targets = findTargets(player, tracking.perk, tracking.tier);
        pointCompasses(player, targets.isEmpty() ? null : targets.get(0));

        // Eagle Eye tier 3 and Tracker tier 2 show HP; Tracker tier 2 also distance.
        boolean showHp = tracking.perk == UtilityPerks.EAGLE_EYE ? tracking.tier >= 3 : tracking.tier >= 2;
        StringBuilder text = new StringBuilder(name(tracking.perk) + ": ");
        if (targets.isEmpty()) {
            text.append("nobody in range");
        }
        for (int i = 0; i < Math.min(targets.size(), 5); i++) {
            ServerPlayer target = targets.get(i);
            if (i > 0) {
                text.append(" | ");
            }
            text.append(target.getName().getString()).append(' ').append(direction(player, target));
            if (tracking.perk == UtilityPerks.EAGLE_EYE || tracking.tier >= 2) {
                text.append(' ').append((int) target.distanceTo(player)).append('m');
            }
            if (showHp) {
                text.append(" ❤").append((int) Math.ceil(target.getHealth()));
            }
        }
        Feedback.actionBar(player, text.toString(), ChatFormatting.YELLOW);

        // Tracker tier 4: particle trail at the target, only visible for the tracking player.
        if (tracking.perk == UtilityPerks.TRACKER && tracking.tier >= 4) {
            for (ServerPlayer target : targets) {
                player.level().sendParticles(player, ParticleTypes.END_ROD, true, false,
                        target.getX(), target.getY() + 0.2, target.getZ(), 6, 0.2, 0.1, 0.2, 0.01);
            }
        }
    }

    /** Points every Utility Compass in the inventory at the target (or resets it). */
    private static void pointCompasses(ServerPlayer player, ServerPlayer target) {
        Optional<GlobalPos> pos = target == null ? Optional.empty()
                : Optional.of(GlobalPos.of(target.level().dimension(), target.blockPosition()));
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isCompass(stack)) {
                if (pos.isPresent()) {
                    stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(pos, false));
                } else {
                    stack.remove(DataComponents.LODESTONE_TRACKER);
                }
            }
        }
    }

    /** Compass direction from player to target, e.g. "NE". */
    private static String direction(ServerPlayer player, ServerPlayer target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[Math.floorMod((int) Math.round(angle / 45), 8)];
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private static boolean checkCooldown(ServerPlayer player, String key, String name) {
        if (!Cooldowns.isReady(player, key)) {
            Feedback.deny(player, name + " is on cooldown (" + Cooldowns.remainingSeconds(player, key) + "s)");
            return false;
        }
        return true;
    }

    /** Uses one of {@code charges} charges (each with its own cooldown). */
    private static boolean useCharge(ServerPlayer player, String key, int charges, int cooldownSeconds, String name) {
        long shortest = Long.MAX_VALUE;
        for (int i = 0; i < charges; i++) {
            String chargeKey = key + "." + i;
            if (Cooldowns.isReady(player, chargeKey)) {
                Cooldowns.start(player, chargeKey, cooldownSeconds);
                return true;
            }
            shortest = Math.min(shortest, Cooldowns.remainingSeconds(player, chargeKey));
        }
        Feedback.deny(player, name + " is on cooldown (" + shortest + "s)");
        return false;
    }

    private static String name(UtilityPerks perk) {
        return switch (perk) {
            case GRAPPLE -> "Grapple";
            case EAGLE_EYE -> "Eagle Eye";
            case SMOKE_SCREEN -> "Smoke Screen";
            case PHASE_DASH -> "Phase Dash";
            case TRACKER -> "Tracker";
            default -> perk.name();
        };
    }

    private record Tracking(UtilityPerks perk, int tier, long endTick) {
    }
}
