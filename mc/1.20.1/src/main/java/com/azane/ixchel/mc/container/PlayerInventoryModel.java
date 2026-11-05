package com.azane.ixchel.mc.container;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Param;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code <mc-player-inventory bind:menu="...">}: the player's main inventory and hotbar slots of
 * {@code menu}, in menu order, as two 9-wide grids of {@code <mc-slot>}. Armor and offhand slots are
 * left to the page. Public for {@link ContainerElements}, which registers it.
 */
@UiView("@ixchel/mc/player-inventory.html")
public final class PlayerInventoryModel extends ViewModel {
    final Param<AbstractContainerMenu> menu = param();
    final Cell<List<Slot>> main = derive(menu, m -> slots(m, Inventory.getSelectionSize(), Inventory.INVENTORY_SIZE));
    final Cell<List<Slot>> hotbar = derive(menu, m -> slots(m, 0, Inventory.getSelectionSize()));

    /** The slots of {@code menu} holding player inventory slots {@code [from, to)}. */
    private static List<Slot> slots(AbstractContainerMenu menu, int from, int to) {
        List<Slot> out = new ArrayList<>();
        if (menu != null) {
            for (Slot slot : menu.slots) {
                if (slot.container instanceof Inventory && slot.getContainerSlot() >= from && slot.getContainerSlot() < to) {
                    out.add(slot);
                }
            }
        }
        return List.copyOf(out);
    }
}
