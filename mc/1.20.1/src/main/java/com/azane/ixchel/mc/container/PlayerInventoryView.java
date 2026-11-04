package com.azane.ixchel.mc.container;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Param;
import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.runtime.UiHosting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code <mc-player-inventory>}: the player's main inventory and hotbar slots of the menu, in menu
 * order, as two 9-wide grids of {@code <mc-slot>}. Armor and offhand slots are left to the page.
 */
@UiTemplate("@ixchel/mc/player-inventory.html")
final class PlayerInventoryView extends ViewModel {
    private static final String FILE = "player-inventory.html";
    static final UiHosting.LibraryComponent COMPONENT = new UiHosting.LibraryComponent("mc-player-inventory",
            UiTemplate.Ids.single(PlayerInventoryView.class), PlayerInventoryView::source, PlayerInventoryView.class,
            PlayerInventoryView::new);

    final Param<AbstractContainerMenu> menu = param();
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

    private static String source() {
        try (InputStream in = PlayerInventoryView.class.getResourceAsStream(FILE)) {
            if (in == null) {
                throw new IllegalStateException(FILE + " is missing next to " + PlayerInventoryView.class.getName());
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + FILE, e);
        }
    }
}
