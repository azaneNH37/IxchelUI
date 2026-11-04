package com.azane.ixchel.mc.container;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code <mc-player-inventory>}: the player's main inventory and hotbar slots of the open menu, in
 * menu order, as two 9-wide grids of {@code <mc-slot>}. Armor and offhand slots are left to the page.
 * The open menu is the player's {@code containerMenu}: vanilla ignores clicks in any other, so it is
 * the screen's menu whenever slots work. A container screen builds its document before
 * {@code MenuScreens} sets it, so the first read can see the previous menu; the first frame reads again.
 * Public for {@link com.azane.ixchel.mc.McLibrary}, which declares it.
 */
@UiTemplate("@ixchel/mc/container/player-inventory.html")
public final class PlayerInventoryView extends ViewModel {
    private final Cell<AbstractContainerMenu> menu = source(ClientSources.player(p -> p.containerMenu, null));
    final Cell<List<Integer>> main = derive(menu, m -> slots(m, Inventory.getSelectionSize(), Inventory.INVENTORY_SIZE));
    final Cell<List<Integer>> hotbar = derive(menu, m -> slots(m, 0, Inventory.getSelectionSize()));

    /** The menu slot indices holding player inventory slots {@code [from, to)}. */
    private static List<Integer> slots(AbstractContainerMenu menu, int from, int to) {
        List<Integer> out = new ArrayList<>();
        if (menu != null) {
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                if (slot.container instanceof Inventory && slot.getContainerSlot() >= from && slot.getContainerSlot() < to) {
                    out.add(i);
                }
            }
        }
        return List.copyOf(out);
    }
}
