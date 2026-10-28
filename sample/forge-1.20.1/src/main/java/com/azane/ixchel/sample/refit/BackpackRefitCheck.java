package com.azane.ixchel.sample.refit;

import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.Script;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * M5 check, {@code -Pcheck=backpack}: opens the backpack refit and drives it with real mouse events
 * at the laid-out slot positions, then checks the server's inventory after each vanilla transaction
 * (place, quick move, locked gun slot, click on the panel vs outside).
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
        Checks.register("backpack", s -> s
                .server(BackpackRefitCheck::stock)
                .waitUntil("stock reaches the client", () -> Minecraft.getInstance().player.getInventory()
                        .getItem(THIRD_ROW_ITEM).is(Items.COBBLESTONE), Script.WAIT)
                .run(RefitNetwork::openBackpack)
                .waitUntil("backpack refit opens", BackpackRefitCheck::isOpen, Script.WAIT)
                .run(() -> checkLayout(s))
                .shot("backpack-open")
                // Hover through the real cursor, as vanilla, JEI and EMI read it during render: the sticks slot.
                .run(() -> hover(hotbar(STICKS)))
                .waitUntil("sticks slot is hovered", () -> screen().getSlotUnderMouse() == slot(hotbar(STICKS)), Script.WAIT)
                .shot("backpack-hover")
                .run(() -> Script.hoverAt(0, 0))
                // The inventory list shows two and a half rows; the wheel scrolls it to the end (9px), and
                // the cut third row moves up with it.
                .run(() -> thirdRowY = slot(THIRD_ROW).y)
                .scroll(".grid.scroll", -1)
                .waitUntil("wheel scrolls the inventory list", () -> slot(THIRD_ROW).y == thirdRowY - 9, Script.WAIT)
                .shot("backpack-scrolled")
                .scroll(".grid.scroll", 1)
                // Place one iron bars from the hotbar into the muzzle slot, put the rest back.
                .run(() -> click(hotbar(BARS)))
                .run(() -> click(MUZZLE))
                .run(() -> click(hotbar(BARS)))
                .waitUntilServer("bars placed in muzzle", p -> "bars".equals(Attachments.installed(p.getInventory().getItem(GUN), "muzzle"))
                        && p.getInventory().getItem(BARS).is(Items.IRON_BARS) && p.getInventory().getItem(BARS).getCount() == 1, Script.WAIT)
                // Quick move the installed spyglass out, and sticks into the grip slot.
                .run(() -> shiftClick(0))
                .run(() -> shiftClick(hotbar(STICKS)))
                .waitUntilServer("scope out, stick in grip", p -> Attachments.installed(p.getInventory().getItem(GUN), "scope").isEmpty()
                        && count(p.getInventory(), Items.SPYGLASS) == 1
                        && "stick".equals(Attachments.installed(p.getInventory().getItem(GUN), "grip"))
                        && count(p.getInventory(), Items.STICK) == 2, Script.WAIT)
                // The gun's own slot is locked.
                .run(() -> click(hotbar(GUN)))
                .expect("gun slot cannot be picked up", () -> screen().getMenu().getCarried().isEmpty())
                // Carrying the spyglass: a click on the panel keeps it, a click outside drops it.
                .waitUntil("spyglass back on the client", () -> hasSlotWith(Items.SPYGLASS), Script.WAIT)
                .run(() -> click(menuIndexOf(Items.SPYGLASS)))
                .click(".title")
                .expect("panel click keeps the carried stack", () -> screen().getMenu().getCarried().is(Items.SPYGLASS))
                .run(() -> Script.clickAt(2, 2, 0))
                .waitUntilServer("outside click drops it", p -> count(p.getInventory(), Items.SPYGLASS) == 0
                        && p.containerMenu.getCarried().isEmpty(), Script.WAIT)
                .shot("backpack-after")
                .run(() -> Minecraft.getInstance().player.closeContainer()));
    }

    private static void stock(ServerPlayer p) {
        Inventory inv = p.getInventory();
        ItemStack gun = new ItemStack(Items.CROSSBOW);
        Attachments.setInstalled(gun, "scope", "spyglass");
        inv.setItem(GUN, gun);
        inv.setItem(BARS, new ItemStack(Items.IRON_BARS, 2));
        inv.setItem(STICKS, new ItemStack(Items.STICK, 3));
        inv.setItem(THIRD_ROW_ITEM, new ItemStack(Items.COBBLESTONE, 5));
    }

    private static boolean isOpen() {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> s
                && s.getMenu() instanceof BackpackRefitMenu;
    }

    private static boolean hasSlotWith(Item item) {
        return screen().getMenu().slots.stream().anyMatch(slot -> slot.getItem().is(item));
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
}
