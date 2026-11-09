package org.langebe.skilltreemc.mechanics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.langebe.skilltreemc.SkillTreeMC;
import org.langebe.skilltreemc.core.Cooldowns;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.langebe.skilltreemc.SkillTreeConfig.*;

/**
 * Passive Utils choice perks. Some of them are called from mixins
 * (potions, hunger, durability), the rest runs on events and the server tick.
 *
 * <ul>
 *   <li><b>Ghost Walk</b> (L6): silent steps, faster sneaking, invisibility when starting to sneak.</li>
 *   <li><b>Potion Mastery</b> (L7): longer potions, full-strength splash, free potion once a minute.</li>
 *   <li><b>Iron Gut</b> (L7): slower hunger, more saturation, regeneration at full hunger.</li>
 *   <li><b>Midas Touch</b> (L8): double ore / rare mob drops, Fortune on all tools, XP bottles.</li>
 *   <li><b>Fortify</b> (L9): damage reduction while standing still.</li>
 *   <li><b>Ambush</b> (L10): bonus damage on the first hit after sneaking.</li>
 *   <li><b>Tinker</b> (L10): tools and armour lose less durability.</li>
 * </ul>
 */
public final class UtilityPassives {

    private static final Identifier GHOST_SPEED_ID = SkillTreeMC.id("ghost_walk_sneak_speed");
    private static final String GHOST_INVIS_COOLDOWN_KEY = "ghost_walk_invis";
    private static final String POTION_REFUND_KEY = "potion_refund";
    private static final String AUTO_FORTIFY_KEY = "auto_fortify";

    /** Per-player movement/sneak tracking. Not saved. */
    private static final Map<UUID, PlayerTrack> TRACK = new HashMap<>();
    /** Players this class made silent (so we only undo our own change). */
    private static final Set<UUID> SILENCED = new HashSet<>();

    // Potion context: set while a potion is drunk / a splash potion hits (server thread only).
    // Only valid in the tick it was set in, so an exception between begin and end (which would
    // skip the end hook) cannot leave it switched on for later, unrelated effects.
    private static ServerPlayer drinking;
    private static ItemStack drinkingStack = ItemStack.EMPTY;
    private static int drinkingTick = -1;
    private static PotionContents splashContents;
    private static int splashTick = -1;

    // Midas Touch: the tool that currently has a temporary Fortune enchantment.
    private static ItemStack fortuneTool;
    private static Holder<Enchantment> fortuneHolder;

    private UtilityPassives() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UtilityPassives::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            TRACK.remove(handler.player.getUUID());
            if (SILENCED.remove(handler.player.getUUID())) {
                handler.player.setSilent(false);
            }
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                addTemporaryFortune(serverPlayer);
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            removeTemporaryFortune();
            if (player instanceof ServerPlayer serverPlayer) {
                midasXpBottle(serverPlayer, pos);
            }
        });
        PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, blockEntity) -> removeTemporaryFortune());
    }

    // =================================================================================
    // Tick: Ghost Walk, Fortify / Ambush tracking, Iron Gut regeneration
    // =================================================================================

    private static void tick(MinecraftServer server) {
        long now = Cooldowns.now(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerTrack track = TRACK.computeIfAbsent(player.getUUID(), id -> new PlayerTrack());

            // Standing still (Fortify)
            Vec3 pos = player.position();
            if (track.lastPos != null && pos.distanceToSqr(track.lastPos) < 1.0E-4) {
                track.stillTicks++;
            } else {
                track.stillTicks = 0;
            }
            track.lastPos = pos;

            // Sneaking (Ghost Walk, Ambush)
            boolean sneaking = player.isShiftKeyDown();
            if (sneaking) {
                if (track.sneakTicks == 0) {
                    onStartSneaking(player);
                }
                track.sneakTicks++;
                if (track.sneakTicks >= AMBUSH_ARM_TICKS.getInt()) {
                    track.ambushArmedUntil = now + AMBUSH_WINDOW_TICKS.getInt();
                }
            } else {
                track.sneakTicks = 0;
            }

            if (server.getTickCount() % 20 == 0) {
                updateGhostWalk(player);
                ironGutRegen(player, server.getTickCount() / 20);
            }

            // Fortify animation: ring when it turns on, a few particles every second while active.
            if (server.getTickCount() % 5 == 0) {
                boolean fortified = isFortified(player);
                if (fortified && !track.fortified) {
                    Fx.fortifyOn(player);
                } else if (fortified && server.getTickCount() % 20 == 0) {
                    Fx.fortifyTick(player);
                }
                track.fortified = fortified;
            }
        }
    }

    // =================================================================================
    // Ghost Walk
    // =================================================================================

    private static void updateGhostWalk(ServerPlayer player) {
        int tier = UtilityPerks.GHOST_WALK.tier(player);
        AttributeInstance sneakSpeed = player.getAttribute(Attributes.SNEAKING_SPEED);
        if (tier > 0) {
            // Always remember the player, even if they were already silent: the flag is saved in
            // the player file, so after a crash it would otherwise never be cleared again.
            player.setSilent(true);
            SILENCED.add(player.getUUID());
            if (sneakSpeed != null) {
                sneakSpeed.addOrUpdateTransientModifier(new AttributeModifier(GHOST_SPEED_ID,
                        GHOST_SNEAK_SPEED.tier(tier), AttributeModifier.Operation.ADD_VALUE));
            }
        } else {
            if (SILENCED.remove(player.getUUID())) {
                player.setSilent(false);
            }
            if (sneakSpeed != null) {
                sneakSpeed.removeModifier(GHOST_SPEED_ID);
            }
        }
    }

    private static void onStartSneaking(ServerPlayer player) {
        int tier = UtilityPerks.GHOST_WALK.tier(player);
        int seconds = tier > 0 ? GHOST_INVIS_SECONDS.tierInt(tier) : 0;
        if (seconds > 0 && Cooldowns.isReady(player, GHOST_INVIS_COOLDOWN_KEY)) {
            giveGhostInvisibility(player, seconds * 20);
            Feedback.proc(player, "☁ GHOST WALK", ChatFormatting.WHITE);
            Cooldowns.start(player, GHOST_INVIS_COOLDOWN_KEY, GHOST_INVIS_COOLDOWN.get());
        }
    }

    private static void giveGhostInvisibility(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, true, false, true));
        Fx.ghostVanish(player);
        TRACK.computeIfAbsent(player.getUUID(), id -> new PlayerTrack()).ghostInvisUntil = Cooldowns.now(player) + ticks;
    }

    /** Attacking breaks Ghost Walk invisibility – at tier 5 only after 1 second. */
    static void onAttack(ServerPlayer attacker) {
        PlayerTrack track = TRACK.get(attacker.getUUID());
        if (track == null || track.ghostInvisUntil <= Cooldowns.now(attacker)) {
            return;
        }
        track.ghostInvisUntil = 0;
        Runnable remove = () -> {
            MobEffectInstance invis = attacker.getEffect(MobEffects.INVISIBILITY);
            if (invis != null && invis.isAmbient()) {
                attacker.removeEffect(MobEffects.INVISIBILITY);
                Fx.ghostVanish(attacker);
            }
        };
        if (UtilityPerks.GHOST_WALK.tier(attacker) >= 5) {
            Scheduler.later((int) Math.round(GHOST_BREAK_DELAY.get() * 20), remove);
        } else {
            remove.run();
        }
    }

    // =================================================================================
    // Fortify & Ambush (called from CombatMechanics)
    // =================================================================================

    static boolean isFortified(ServerPlayer player) {
        int tier = UtilityPerks.FORTIFY.tier(player);
        if (tier == 0) {
            return false;
        }
        PlayerTrack track = TRACK.get(player.getUUID());
        if (track == null) {
            return false;
        }
        return track.stillTicks >= FORTIFY_STILL_TICKS.getInt()
                || (tier >= 2 && player.isShiftKeyDown())
                || track.autoFortifyUntil > Cooldowns.now(player);
    }

    /** Damage reduction from Fortify (0 if not fortified). */
    static double fortifyReduction(ServerPlayer player) {
        return isFortified(player) ? FORTIFY_REDUCTION.tier(UtilityPerks.FORTIFY.tier(player)) : 0;
    }

    /** Fortify tier 3: extra damage while fortified. */
    static double fortifyDamageBonus(ServerPlayer player) {
        return UtilityPerks.FORTIFY.tier(player) >= 3 && isFortified(player) ? FORTIFY_DAMAGE_BONUS.get() : 0;
    }

    /** Fortify tier 4: taking a crit fortifies automatically. */
    static void onCritReceived(ServerPlayer victim) {
        if (UtilityPerks.FORTIFY.tier(victim) >= 4 && Cooldowns.isReady(victim, AUTO_FORTIFY_KEY)) {
            TRACK.computeIfAbsent(victim.getUUID(), id -> new PlayerTrack()).autoFortifyUntil =
                    Cooldowns.now(victim) + Math.round(FORTIFY_AUTO_SECONDS.get() * 20);
            Cooldowns.start(victim, AUTO_FORTIFY_KEY, FORTIFY_AUTO_COOLDOWN.get());
            Feedback.proc(victim, "⛨ FORTIFIED", ChatFormatting.GRAY);
        }
    }

    /**
     * Bonus of an armed Ambush, without using it up (the hit may still be blocked).
     *
     * @return the bonus damage fraction (0 if Ambush is not armed)
     */
    static double ambushBonus(ServerPlayer attacker, boolean crit) {
        int tier = UtilityPerks.AMBUSH.tier(attacker);
        PlayerTrack track = TRACK.get(attacker.getUUID());
        if (tier == 0 || track == null || track.ambushArmedUntil <= Cooldowns.now(attacker)) {
            return 0;
        }
        double bonus = AMBUSH_BONUS.tier(tier);
        if (tier >= 4 && crit) {
            bonus *= 2; // crits from Ambush double the bonus damage
        }
        return bonus;
    }

    /** Uses up the Ambush after the hit actually landed: poison, re-invisibility, feedback. */
    static void consumeAmbush(ServerPlayer attacker, LivingEntity victim, double bonus) {
        int tier = UtilityPerks.AMBUSH.tier(attacker);
        PlayerTrack track = TRACK.get(attacker.getUUID());
        if (track == null) {
            return;
        }
        track.ambushArmedUntil = 0;
        if (tier >= 2) {
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, (int) Math.round(AMBUSH_POISON_SECONDS.get() * 20), 0));
        }
        if (tier >= 3 && UtilityPerks.GHOST_WALK.has(attacker)) {
            Scheduler.later(1, () -> giveGhostInvisibility(attacker, AMBUSH_REINVIS_TICKS.getInt()));
        }
        Feedback.proc(attacker, "⚔ AMBUSH +" + Math.round(bonus * 100) + "%", ChatFormatting.DARK_RED);
        Fx.ambush(attacker, victim);
    }

    // =================================================================================
    // Potion Mastery (called from mixins)
    // =================================================================================

    public static void beginDrink(LivingEntity entity, ItemStack stack) {
        if (entity instanceof ServerPlayer player) {
            drinking = player;
            drinkingStack = stack.copyWithCount(1);
            drinkingTick = player.level().getServer().getTickCount();
        }
    }

    public static void endDrink(LivingEntity entity) {
        ServerPlayer player = drinking;
        ItemStack potion = drinkingStack;
        drinking = null;
        drinkingStack = ItemStack.EMPTY;
        if (player == null || player != entity) {
            return;
        }
        // Tier 4: once per minute the potion is not used up.
        if (UtilityPerks.POTION_MASTERY.tier(player) >= 4 && !potion.isEmpty()
                && Cooldowns.isReady(player, POTION_REFUND_KEY)) {
            Cooldowns.start(player, POTION_REFUND_KEY, POTION_REFUND_COOLDOWN.get());
            Scheduler.later(1, () -> {
                if (!player.getInventory().add(potion)) {
                    player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), potion));
                }
                Feedback.proc(player, "⚗ POTION KEPT", ChatFormatting.LIGHT_PURPLE);
                Fx.potionRefund(player);
            });
        }
    }

    public static void beginSplash(MinecraftServer server, ItemStack stack) {
        splashContents = stack.get(DataComponents.POTION_CONTENTS);
        splashTick = server.getTickCount();
    }

    public static void endSplash() {
        splashContents = null;
    }

    /** Called for every effect a living entity receives. Extends potion effects for Potion Mastery. */
    public static MobEffectInstance modifyPotionEffect(LivingEntity entity, MobEffectInstance effect) {
        if (!(entity instanceof ServerPlayer player) || effect.getEffect().value().isInstantaneous()
                || effect.isInfiniteDuration()) {
            return effect;
        }
        int tick = player.level().getServer().getTickCount();
        boolean fromDrink = drinking == player && drinkingTick == tick;
        boolean fromSplash = splashContents != null && splashTick == tick;
        int tier = UtilityPerks.POTION_MASTERY.tier(player);
        if (tier == 0 || (!fromDrink && !fromSplash)) {
            return effect;
        }
        int duration = effect.getDuration();
        if (fromSplash && tier >= 3) {
            // Full strength: use the potion's own duration instead of the distance-scaled one.
            for (MobEffectInstance original : splashContents.getAllEffects()) {
                if (original.getEffect().equals(effect.getEffect()) && original.getAmplifier() == effect.getAmplifier()) {
                    duration = Math.max(duration, original.getDuration());
                }
            }
        }
        duration = (int) Math.round(duration * (1 + POTION_DURATION_BONUS.tier(tier)));
        return new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(),
                effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }

    // =================================================================================
    // Iron Gut (called from mixins)
    // =================================================================================

    public static float modifyExhaustion(LivingEntity entity, float exhaustion) {
        if (entity instanceof ServerPlayer player) {
            int tier = UtilityPerks.IRON_GUT.tier(player);
            if (tier > 0) {
                return (float) (exhaustion * (1 - HUNGER_REDUCTION.tier(tier)));
            }
        }
        return exhaustion;
    }

    /** Tier 3: food gives 50 % more saturation. */
    public static void onFoodEaten(LivingEntity entity, float saturationGained) {
        if (entity instanceof ServerPlayer player && UtilityPerks.IRON_GUT.tier(player) >= 3) {
            var food = player.getFoodData();
            float extra = (float) (saturationGained * SATURATION_BONUS.get());
            food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel() + extra));
        }
    }

    private static void ironGutRegen(ServerPlayer player, long second) {
        if (UtilityPerks.IRON_GUT.tier(player) >= 4 && second % Math.max(1, IRON_GUT_REGEN_SECONDS.getInt()) == 0
                && player.getFoodData().getFoodLevel() >= 20 && player.getHealth() < player.getMaxHealth()) {
            player.heal(1.0f);
        }
    }

    // =================================================================================
    // Midas Touch
    // =================================================================================

    /** Doubles ore drops (and from tier 2 rare mob drops). Called from the loot event. */
    static void midasDrops(ServerPlayer player, BlockState block, boolean mobLoot, List<ItemStack> drops, Vec3 origin) {
        int tier = UtilityPerks.MIDAS_TOUCH.tier(player);
        if (tier == 0) {
            return;
        }
        boolean ore = block != null && block.is(ConventionalBlockTags.ORES);
        if (!ore && !(mobLoot && tier >= 2)) {
            return;
        }
        double chance = MIDAS_CHANCE.tier(tier);
        boolean doubled = false;
        for (ItemStack stack : List.copyOf(drops)) {
            // Never double an ore *block* (Silk Touch, Ancient Debris, ...): it could be placed and
            // mined again forever, every round with a chance of +1 block.
            boolean eligible = ore ? !isOreBlockItem(stack, block) : stack.getRarity() != Rarity.COMMON;
            if (eligible && player.getRandom().nextDouble() < chance) {
                drops.add(stack.copy());
                doubled = true;
            }
        }
        if (doubled && origin != null) {
            Fx.midas(player.level(), origin);
            Feedback.proc(player, "✪ MIDAS x2", ChatFormatting.GOLD);
        }
    }

    private static boolean isOreBlockItem(ItemStack stack, BlockState block) {
        return stack.is(block.getBlock().asItem())
                || (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock().defaultBlockState().is(ConventionalBlockTags.ORES));
    }

    /** Tier 3: tools without Fortune get Fortune while breaking the block. */
    private static void addTemporaryFortune(ServerPlayer player) {
        if (UtilityPerks.MIDAS_TOUCH.tier(player) < 3) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) {
            return;
        }
        Holder<Enchantment> fortune = player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        if (EnchantmentHelper.getItemEnchantmentLevel(fortune, tool) > 0) {
            return;
        }
        removeTemporaryFortune();
        EnchantmentHelper.updateEnchantments(tool, enchantments -> enchantments.set(fortune, MIDAS_FORTUNE_LEVEL.getInt()));
        fortuneTool = tool;
        fortuneHolder = fortune;
        // Safety net in case neither AFTER nor CANCELED fires.
        Scheduler.later(1, UtilityPassives::removeTemporaryFortune);
    }

    private static void removeTemporaryFortune() {
        if (fortuneTool != null) {
            Holder<Enchantment> fortune = fortuneHolder;
            EnchantmentHelper.updateEnchantments(fortuneTool, enchantments -> enchantments.set(fortune, 0));
            fortuneTool = null;
            fortuneHolder = null;
        }
    }

    /** Tier 4: chance for an XP bottle when mining. */
    private static void midasXpBottle(ServerPlayer player, BlockPos pos) {
        if (UtilityPerks.MIDAS_TOUCH.tier(player) >= 4 && player.getRandom().nextDouble() < MIDAS_XP_BOTTLE_CHANCE.get()) {
            player.level().addFreshEntity(new ItemEntity(player.level(), pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, new ItemStack(Items.EXPERIENCE_BOTTLE)));
        }
    }

    // =================================================================================
    // Tinker (called from mixin)
    // =================================================================================

    /** Changes how much durability an item loses. */
    public static int modifyDurabilityLoss(ItemStack stack, int amount, ServerPlayer player) {
        if (player == null || amount <= 0) {
            return amount;
        }
        int tier = UtilityPerks.TINKER.tier(player);
        if (tier == 0) {
            return amount;
        }
        if (tier >= 3 && player.getRandom().nextDouble() < TINKER_FULL_SAVE.get()) {
            return 0;
        }
        double save = TINKER_SAVE.tier(tier);
        int result = 0;
        for (int i = 0; i < amount; i++) {
            if (player.getRandom().nextDouble() >= save) {
                result++;
            }
        }
        // Tier 4: the hit that would break the item leaves it at 1 durability instead, with a warning.
        // Only once: an item already at 1 durability breaks normally, otherwise it would last forever.
        if (tier >= 4 && stack.isDamageableItem()) {
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            if (remaining > 1 && result >= remaining) {
                result = remaining - 1;
                Feedback.proc(player, "⚒ ALMOST BROKEN – repair it!", ChatFormatting.GOLD);
            }
        }
        return result;
    }

    // =================================================================================

    private static final class PlayerTrack {
        Vec3 lastPos;
        int stillTicks;
        int sneakTicks;
        long ambushArmedUntil;
        long ghostInvisUntil;
        long autoFortifyUntil;
        boolean fortified;
    }
}
