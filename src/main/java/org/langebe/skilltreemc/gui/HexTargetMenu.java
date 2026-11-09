package org.langebe.skilltreemc.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.langebe.skilltreemc.mechanics.Feedback;
import org.langebe.skilltreemc.spell.SpellCaster;
import org.langebe.skilltreemc.spell.spells.HexSpell;

import java.util.List;

/**
 * Target selection for Hex of Corruption: one player head per possible target.
 * Clicking a head casts the hex (mana and cooldown are charged only then).
 */
public class HexTargetMenu extends ServerMenu {

    private static final int MAX_TARGETS = 54;

    private final HexSpell spell;
    private final List<ServerPlayer> targets;

    private HexTargetMenu(int containerId, Inventory playerInventory, HexSpell spell, List<ServerPlayer> targets) {
        super(MenuType.GENERIC_9x6, 6, containerId, playerInventory);
        this.spell = spell;
        this.targets = targets;
        refresh();
    }

    public static void open(ServerPlayer caster, HexSpell spell, List<ServerPlayer> targets) {
        caster.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, menuPlayer) -> new HexTargetMenu(containerId, playerInventory, spell, targets),
                Component.literal("Hex of Corruption – choose target")
        ));
    }

    @Override
    protected void draw() {
        for (int i = 0; i < Math.min(targets.size(), MAX_TARGETS); i++) {
            ServerPlayer target = targets.get(i);
            ItemStack head = ItemBuilder.of(Items.PLAYER_HEAD)
                    .name(target.getName().getString(), ChatFormatting.DARK_PURPLE)
                    .lore((int) target.distanceTo(player) + " blocks away")
                    .lore("Click to hex", ChatFormatting.YELLOW)
                    .build();
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(target.getGameProfile()));
            setButton(i, head, () -> onTargetClicked(target));
        }
    }

    private void onTargetClicked(ServerPlayer target) {
        player.closeContainer();
        // The target may have left or moved away while the menu was open.
        if (!spell.candidates(player).contains(target)) {
            Feedback.deny(player, "Target is no longer in range.");
            return;
        }
        SpellCaster.completePending(player, spell, () -> spell.apply(player, target));
    }
}
