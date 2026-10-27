package com.azane.ixchel.sample.refit;

import com.azane.ixchel.sample.check.Script;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * M5 check, run unattended with {@code -Pbench=backpack}: opens the backpack refit and drives it
 * with real mouse events at the laid-out slot positions, then checks the server's inventory after
 * each vanilla transaction (place, quick move, locked gun slot, click on the panel vs outside).
 */
final class BackpackRefitCheck {
    private static final int GUN = 0;
    private static final int BARS = 1;
    private static final int STICKS = 2;
    /** Menu index of the muzzle attachment slot, see {@link Attachments#TYPES}. */
    private static final int MUZZLE = 1;
    /** Menu index of the first slot in the inventory list's third row, cut by the list's edge. */
    private static final int THIRD_ROW = BackpackRefitMenu.INVENTORY_START + 18;
    /** Player inventory slot shown there (the main inventory starts at 9). */
    private static final int THIRD_ROW_ITEM = 9 + 18;

    private static int thirdRowY;

    private BackpackRefitCheck() {
    }

    static void init() {
        Script s = Script.forBench("backpack");
        if (s == null) {
            return;
        }
        s.step(40, BackpackRefitCheck::stock);
        s.step(20, RefitNetwork::openBackpack);
        s.step(20, () -> {
            checkLayout(s);
            Script.shot("backpack-open");
        });
        // Hover through the real cursor, as vanilla, JEI and EMI read it during render: the sticks slot.
        s.step(5, () -> hover(hotbar(STICKS)));
        s.step(10, () -> {
            s.expect("sticks slot is hovered", () -> screen().getSlotUnderMouse() == slot(hotbar(STICKS)));
            Script.shot("backpack-hover");
        });
        s.step(5, () -> Script.hoverAt(0, 0));
        // The inventory list shows two and a half rows; the wheel scrolls it to the end (9px), and the
        // cut third row moves up with it.
        s.step(5, () -> {
            thirdRowY = slot(THIRD_ROW).y;
            scrollInventory(-1);
        });
        s.step(5, () -> {
            s.expect("wheel scrolls the inventory list", () -> slot(THIRD_ROW).y == thirdRowY - 9);
            Script.shot("backpack-scrolled");
        });
        s.step(5, () -> scrollInventory(1));
        // Place one iron bars from the hotbar into the muzzle slot, put the rest back.
        s.step(5, () -> click(hotbar(BARS)));
        s.step(5, () -> click(MUZZLE));
        s.step(5, () -> click(hotbar(BARS)));
        s.step(20, () -> expectServer(s, "bars placed in muzzle", inv -> "bars".equals(Attachments.installed(inv.getItem(GUN), "muzzle"))
                && inv.getItem(BARS).is(Items.IRON_BARS) && inv.getItem(BARS).getCount() == 1));
        // Quick move the installed spyglass out, and sticks into the grip slot.
        s.step(5, () -> shiftClick(0));
        s.step(5, () -> shiftClick(hotbar(STICKS)));
        s.step(20, () -> expectServer(s, "scope out, stick in grip", inv -> Attachments.installed(inv.getItem(GUN), "scope").isEmpty()
                && count(inv, Items.SPYGLASS) == 1 && "stick".equals(Attachments.installed(inv.getItem(GUN), "grip"))
                && count(inv, Items.STICK) == 2));
        // The gun's own slot is locked.
        s.step(5, () -> click(hotbar(GUN)));
        s.step(5, () -> s.expect("gun slot cannot be picked up", () -> screen().getMenu().getCarried().isEmpty()));
        // Carrying the spyglass: a click on the panel keeps it, a click outside drops it.
        s.step(5, () -> click(menuIndexOf(Items.SPYGLASS)));
        s.step(5, () -> Script.clickAt(left() + 20, top() + 3, 0));
        s.step(5, () -> s.expect("panel click keeps the carried stack", () -> screen().getMenu().getCarried().is(Items.SPYGLASS)));
        s.step(5, () -> Script.clickAt(2, 2, 0));
        s.step(20, () -> expectServer(s, "outside click drops it", inv -> count(inv, Items.SPYGLASS) == 0
                && inv.player.containerMenu.getCarried().isEmpty()));
        s.step(5, () -> Script.shot("backpack-after"));
        s.step(5, () -> Minecraft.getInstance().player.closeContainer());
        s.start();
    }

    private static void stock() {
        Script.onServer(p -> {
            Inventory inv = p.getInventory();
            inv.clearContent();
            ItemStack gun = new ItemStack(Items.CROSSBOW);
            Attachments.setInstalled(gun, "scope", "spyglass");
            inv.setItem(GUN, gun);
            inv.setItem(BARS, new ItemStack(Items.IRON_BARS, 2));
            inv.setItem(STICKS, new ItemStack(Items.STICK, 3));
            inv.setItem(THIRD_ROW_ITEM, new ItemStack(Items.COBBLESTONE, 5));
        });
        Minecraft.getInstance().player.getInventory().selected = GUN;
    }

    private static AbstractContainerScreen<?> screen() {
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> s
                && s.getMenu() instanceof BackpackRefitMenu) {
            return s;
        }
        throw new IllegalStateException("backpack refit screen is not open: " + Minecraft.getInstance().screen);
    }

    private static void checkLayout(Script s) {
        AbstractContainerScreen<?> screen = screen();
        int hidden = 0;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.x < 0 || slot.y < 0) {
                hidden++;
            }
        }
        // The GUI area other mods avoid is the panel, not the whole screen.
        boolean pass = hidden == 0 && screen.getXSize() > 0 && screen.getYSize() > 0 && screen.getXSize() < screen.width
                && screen.getYSize() < screen.height;
        s.report("every slot placed inside the gui area " + screen.getGuiLeft() + "," + screen.getGuiTop() + " "
                + screen.getXSize() + "x" + screen.getYSize() + " (" + hidden + " hidden)", pass);
    }

    private static int hotbar(int inventorySlot) {
        return BackpackRefitMenu.HOTBAR_START + inventorySlot;
    }

    private static int menuIndexOf(Item item) {
        for (Slot slot : screen().getMenu().slots) {
            if (slot.getItem().is(item)) {
                return slot.index;
            }
        }
        throw new IllegalStateException("no slot holds " + item);
    }

    private static int left() {
        return screen().getGuiLeft();
    }

    private static int top() {
        return screen().getGuiTop();
    }

    private static Slot slot(int index) {
        return screen().getMenu().slots.get(index);
    }

    private static void click(int slotIndex) {
        Slot slot = slot(slotIndex);
        Script.clickAt(left() + slot.x + 8, top() + slot.y + 8, 0);
    }

    private static void shiftClick(int slotIndex) {
        // Sent as a packet: vanilla reads Shift from the window, which an unattended run cannot hold.
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(screen().getMenu().containerId, slotIndex, 0, ClickType.QUICK_MOVE, mc.player);
    }

    /** Wheel over the second row of the inventory list, which stays inside it at either end. */
    private static void scrollInventory(double delta) {
        Slot row = slot(BackpackRefitMenu.INVENTORY_START + 9);
        screen().mouseScrolled(left() + row.x + 8, top() + row.y + 8, delta);
    }

    private static void hover(int slotIndex) {
        Slot slot = slot(slotIndex);
        Script.hoverAt(left() + slot.x + 8, top() + slot.y + 8);
    }

    private static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    private static void expectServer(Script s, String what, Predicate<Inventory> check) {
        s.expectServer(what, p -> check.test(p.getInventory()));
    }
}
