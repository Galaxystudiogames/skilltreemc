package org.langebe.skilltreemc.input;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.langebe.skilltreemc.gui.SkillTreeMenus;
import org.langebe.skilltreemc.mechanics.ActiveAbilities;
import org.langebe.skilltreemc.mechanics.Feedback;
import org.langebe.skilltreemc.mechanics.UtilityActives;
import org.langebe.skilltreemc.spell.Mana;
import org.langebe.skilltreemc.spell.SpellCaster;
import org.langebe.skilltreemc.spell.SpellOrb;

/**
 * Player input for the active parts of the mod. A vanilla client has no extra keys, so
 * existing actions are reused:
 *
 * <table>
 *   <tr><th>Input</th><th>Spell Orb</th><th>Utility Compass</th><th>anything else</th></tr>
 *   <tr><td>Right-click</td><td>cast selected fast spell</td><td>use selected Utils perk</td><td>–</td></tr>
 *   <tr><td>Sneak + right-click</td><td>next fast slot</td><td>next Utils perk</td><td>–</td></tr>
 *   <tr><td>Sneak + offhand key (F)</td><td>spell menu</td><td colspan="2">toggle Rage / Shield</td></tr>
 * </table>
 *
 * The offhand key is caught in {@code mixin/ServerGamePacketListenerMixin}; it only
 * cancels the normal hand swap when this class actually used the key press.
 */
public final class InputHandler {

    private InputHandler() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (SpellOrb.isOrb(player.getMainHandItem())) {
                if (player.isShiftKeyDown()) {
                    SpellCaster.cycle(serverPlayer);
                } else {
                    SpellCaster.castSelected(serverPlayer);
                }
                return InteractionResult.SUCCESS;
            }
            if (UtilityActives.isCompass(player.getMainHandItem())) {
                if (player.isShiftKeyDown()) {
                    UtilityActives.cycle(serverPlayer);
                } else {
                    UtilityActives.use(serverPlayer);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    /**
     * Called by the mixin when the player presses the offhand key while sneaking.
     *
     * @return {@code true} if the key press was used (the item swap is cancelled)
     */
    public static boolean onSneakSwapHands(ServerPlayer player) {
        if (SpellOrb.isOrb(player.getMainHandItem())) {
            if (Mana.max(player) <= 0) {
                Feedback.deny(player, "You need the Support path to use spells.");
            } else {
                SkillTreeMenus.openSpells(player);
            }
            return true;
        }
        return ActiveAbilities.toggle(player);
    }
}
