package com.azane.ixchel.sample.refit;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * M5 check, run unattended with {@code -Pbench=backpack}: opens the backpack refit and drives it
 * with real mouse events at the laid-out slot positions, then checks the server's inventory after
 * each vanilla transaction (place, quick move, locked gun slot, click on the panel vs outside).
 */
final class BackpackRefitCheck {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int GUN = 0;
    private static final int BARS = 1;
    private static final int STICKS = 2;
    /** Menu index of the muzzle attachment slot, see {@link Attachments#TYPES}. */
    private static final int MUZZLE = 1;
    /** Menu index of the first slot in the inventory list's third row, cut by the list's edge. */
    private static final int THIRD_ROW = BackpackRefitMenu.INVENTORY_START + 18;
    /** Player inventory slot shown there (the main inventory starts at 9). */
    private static final int THIRD_ROW_ITEM = 9 + 18;

    private record Step(int delayTicks, Runnable action) {
    }

    private static final Deque<Step> script = new ArrayDeque<>();
    private static final AtomicInteger failures = new AtomicInteger();
    private static Step next;
    private static int wait;
    private static int thirdRowY;

    private BackpackRefitCheck() {
    }

    static void init() {
        if (!"backpack".equals(System.getProperty("ixchelsample.bench"))) {
            return;
        }
        step(40, BackpackRefitCheck::stock);
        step(20, RefitNetwork::openBackpack);
        step(20, () -> {
            checkLayout();
            shot("backpack-open");
        });
        // Hover through the real cursor, as vanilla, JEI and EMI read it during render: the sticks slot.
        step(5, () -> hover(hotbar(STICKS)));
        step(10, () -> {
            expectClient("sticks slot is hovered", s -> s.getSlotUnderMouse() == s.getMenu().slots.get(hotbar(STICKS)));
            shot("backpack-hover");
        });
        step(5, () -> hoverAt(0, 0));
        // The inventory list shows two and a half rows; the wheel scrolls it to the end (9px), and the
        // cut third row moves up with it.
        step(5, () -> {
            thirdRowY = slot(THIRD_ROW).y;
            scrollInventory(-1);
        });
        step(5, () -> {
            expectClient("wheel scrolls the inventory list", s -> slot(THIRD_ROW).y == thirdRowY - 9);
            shot("backpack-scrolled");
        });
        step(5, () -> scrollInventory(1));
        // Place one iron bars from the hotbar into the muzzle slot, put the rest back.
        step(5, () -> click(hotbar(BARS), 0));
        step(5, () -> click(MUZZLE, 0));
        step(5, () -> click(hotbar(BARS), 0));
        step(20, () -> expectServer("bars placed in muzzle", inv -> "bars".equals(Attachments.installed(inv.getItem(GUN), "muzzle"))
                && inv.getItem(BARS).is(Items.IRON_BARS) && inv.getItem(BARS).getCount() == 1));
        // Quick move the installed spyglass out, and sticks into the grip slot.
        step(5, () -> shiftClick(0));
        step(5, () -> shiftClick(hotbar(STICKS)));
        step(20, () -> expectServer("scope out, stick in grip", inv -> Attachments.installed(inv.getItem(GUN), "scope").isEmpty()
                && count(inv, Items.SPYGLASS) == 1 && "stick".equals(Attachments.installed(inv.getItem(GUN), "grip"))
                && count(inv, Items.STICK) == 2));
        // The gun's own slot is locked.
        step(5, () -> click(hotbar(GUN), 0));
        step(5, () -> expectClient("gun slot cannot be picked up", s -> s.getMenu().getCarried().isEmpty()));
        // Carrying the spyglass: a click on the panel keeps it, a click outside drops it.
        step(5, () -> click(menuIndexOf(Items.SPYGLASS), 0));
        step(5, () -> clickAt(left() + 20, top() + 3));
        step(5, () -> expectClient("panel click keeps the carried stack", s -> s.getMenu().getCarried().is(Items.SPYGLASS)));
        step(5, () -> clickAt(2, 2));
        step(20, () -> expectServer("outside click drops it", inv -> count(inv, Items.SPYGLASS) == 0
                && inv.player.containerMenu.getCarried().isEmpty()));
        step(5, () -> shot("backpack-after"));
        step(5, () -> Minecraft.getInstance().player.closeContainer());
        MinecraftForge.EVENT_BUS.addListener(BackpackRefitCheck::tick);
    }

    private static void step(int delay, Runnable r) {
        script.add(new Step(delay, r));
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.player == null) {
            return;
        }
        if (next == null) {
            next = script.poll();
            if (next == null) {
                LOG.info("[ixchel-bench] backpack done, {} check(s) failed", failures.get());
                mc.stop();
                return;
            }
            wait = next.delayTicks;
        }
        if (wait-- > 0) {
            return;
        }
        Step s = next;
        next = null;
        s.action.run();
    }

    private static void stock() {
        onServer(inv -> {
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

    private static void checkLayout() {
        AbstractContainerScreen<?> s = screen();
        int hidden = 0;
        for (Slot slot : s.getMenu().slots) {
            if (slot.x < 0 || slot.y < 0) {
                hidden++;
            }
        }
        // The GUI area other mods avoid is the panel, not the whole screen.
        boolean pass = hidden == 0 && s.getXSize() > 0 && s.getYSize() > 0 && s.getXSize() < s.width
                && s.getYSize() < s.height;
        report("every slot placed inside the gui area " + s.getGuiLeft() + "," + s.getGuiTop() + " "
                + s.getXSize() + "x" + s.getYSize() + " (" + hidden + " hidden)", pass);
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

    private static void click(int slotIndex, int button) {
        Slot slot = screen().getMenu().slots.get(slotIndex);
        clickAt(left() + slot.x + 8, top() + slot.y + 8, button);
    }

    private static void shiftClick(int slotIndex) {
        // Sent as a packet: vanilla reads Shift from the window, which an unattended run cannot hold.
        Slot slot = screen().getMenu().slots.get(slotIndex);
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(screen().getMenu().containerId, slot.index, 0,
                net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
    }

    private static Slot slot(int index) {
        return screen().getMenu().slots.get(index);
    }

    /** Wheel over the second row of the inventory list, which stays inside it at either end. */
    private static void scrollInventory(double delta) {
        Slot row = slot(BackpackRefitMenu.INVENTORY_START + 9);
        screen().mouseScrolled(left() + row.x + 8, top() + row.y + 8, delta);
    }

    private static void hover(int slotIndex) {
        Slot slot = screen().getMenu().slots.get(slotIndex);
        hoverAt(left() + slot.x + 8, top() + slot.y + 8);
    }

    /**
     * Sets the cursor position vanilla hands to {@code render}. The window is not focused in an
     * unattended run, so moving the OS cursor does not reach the game; the dev run has mojmap names.
     */
    private static void hoverAt(double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        try {
            Field xpos = MouseHandler.class.getDeclaredField("xpos");
            Field ypos = MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(mc.mouseHandler, x * scale);
            ypos.setDouble(mc.mouseHandler, y * scale);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void clickAt(double x, double y) {
        clickAt(x, y, 0);
    }

    private static void clickAt(double x, double y, int button) {
        AbstractContainerScreen<?> s = screen();
        s.mouseMoved(x, y);
        s.mouseClicked(x, y, button);
        s.mouseReleased(x, y, button);
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

    private static void expectClient(String what, Predicate<AbstractContainerScreen<?>> check) {
        report(what, check.test(screen()));
    }

    private static void expectServer(String what, Predicate<Inventory> check) {
        onServer(inv -> report(what, check.test(inv)));
    }

    private static void report(String what, boolean pass) {
        if (!pass) {
            failures.incrementAndGet();
        }
        LOG.info("[ixchel-bench] backpack check {}: {}", what, pass ? "PASS" : "FAIL");
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> { });
    }

    private static void onServer(java.util.function.Consumer<Inventory> change) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            change.accept(p.getInventory());
        });
    }
}
