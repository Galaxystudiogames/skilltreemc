package org.langebe.skilltreemc.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class for the skill-tree menus.
 *
 * <p>These menus are purely server-side: they reuse vanilla chest menus, so players do
 * <b>not</b> need the mod on their client. The items inside are only "buttons":
 * <ul>
 *   <li>every interaction with the menu's own slots is blocked (nothing can be taken
 *       out, swapped or shift-clicked in),</li>
 *   <li>a normal left/right click on a slot with a button runs its action,</li>
 *   <li>the player's own inventory below keeps working normally.</li>
 * </ul>
 *
 * <p>Subclasses implement {@link #draw()} and must call {@link #refresh()} at the end of
 * their constructor (not earlier, otherwise their own fields are not set yet).
 */
public abstract class ServerMenu extends ChestMenu {

    protected final ServerPlayer player;
    private final Map<Integer, Consumer<Boolean>> buttons = new HashMap<>();

    protected ServerMenu(MenuType<?> type, int rows, int containerId, Inventory playerInventory) {
        super(type, containerId, playerInventory, new SimpleContainer(9 * rows), rows);
        this.player = (ServerPlayer) playerInventory.player;
    }

    /** Places all items and buttons. Called by {@link #refresh()} on an empty background. */
    protected abstract void draw();

    /** Redraws the whole menu, e.g. after the player unlocked something. */
    public void refresh() {
        buttons.clear();
        for (int i = 0; i < getContainer().getContainerSize(); i++) {
            setItem(i, MenuItems.filler());
        }
        draw();
        broadcastChanges();
    }

    /** Puts a decorative item (without click action) into a slot. */
    protected void setItem(int slot, ItemStack stack) {
        getContainer().setItem(slot, stack);
    }

    /** Puts an item into a slot that runs {@code action} when clicked. */
    protected void setButton(int slot, ItemStack stack, Runnable action) {
        setButton(slot, stack, rightClick -> action.run());
    }

    /** Like {@link #setButton(int, ItemStack, Runnable)}, but the action gets {@code true} for a right-click. */
    protected void setButton(int slot, ItemStack stack, Consumer<Boolean> action) {
        setItem(slot, stack);
        buttons.put(slot, action);
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player clickingPlayer) {
        boolean inMenu = slotIndex >= 0 && slotIndex < getContainer().getContainerSize();
        if (inMenu) {
            Consumer<Boolean> action = buttons.get(slotIndex);
            // Only plain clicks trigger buttons (no shift, number keys, double click, ...).
            // buttonNum: 0 = left click, 1 = right click.
            if (action != null && containerInput == ContainerInput.PICKUP) {
                action.accept(buttonNum == 1);
            }
            return;
        }
        // Shift-clicking in the player inventory would try to move items into the menu, and a
        // double click (PICKUP_ALL) collects matching items from *all* slots – including the menu's.
        if (containerInput == ContainerInput.QUICK_MOVE || containerInput == ContainerInput.PICKUP_ALL) {
            return;
        }
        super.clicked(slotIndex, buttonNum, containerInput, clickingPlayer);
    }

    @Override
    public boolean stillValid(Player player) {
        // Not bound to a block in the world, so the menu never becomes invalid.
        return true;
    }
}
