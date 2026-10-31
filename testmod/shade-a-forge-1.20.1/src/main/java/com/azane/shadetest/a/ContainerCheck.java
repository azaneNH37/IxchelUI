package com.azane.shadetest.a;

import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.UiContainerScreen;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Slot L3 check for a production instance, run with {@code -Dixchelshadea.check=container} and a world
 * to join: opens the player inventory in a relocated, reobfuscated {@link UiContainerScreen} with
 * {@link UiContainerScreen.SlotItemRendering#IXCHEL}, so the SRG lookups of {@code Slot.x/y} and
 * {@code Screen.renderables} run, moves a stack with real clicks, then checks the slots are back in
 * place after closing. Logs {@code [ixchel-shade] container ...} lines and quits.
 */
final class ContainerCheck {
    private static final Logger LOG = LogUtils.getLogger();
    /** Menu indices in {@link InventoryMenu}: main inventory 9-35, hotbar 36-44. */
    private static final int FROM = 9;
    private static final int TO = 10;

    private record Step(int delayTicks, Runnable action) {
    }

    private static final Deque<Step> script = new ArrayDeque<>();
    private static Step next;
    private static int wait;
    private static int failures;
    private static int vanillaX;
    private static int vanillaY;

    private ContainerCheck() {
    }

    static void init() {
        if (!"container".equals(System.getProperty("ixchelshadea.check"))) {
            return;
        }
        step(100, ContainerCheck::stock);
        step(20, ContainerCheck::open);
        step(20, () -> {
            AbstractContainerScreen<?> s = screen();
            long hidden = s.getMenu().slots.stream().filter(slot -> slot.x < 0).count();
            report("all 46 slots placed, gui area " + s.getGuiLeft() + "," + s.getGuiTop() + " " + s.getXSize()
                    + "x" + s.getYSize() + " (" + hidden + " hidden)", hidden == 0 && s.getXSize() < s.width);
            shot("shade-container-open");
        });
        step(5, () -> click(FROM));
        step(5, () -> click(TO));
        step(20, () -> onServer(inv -> report("stack moved by clicks", inv.getItem(TO).is(Items.IRON_BARS)
                && inv.getItem(FROM).isEmpty())));
        step(5, () -> Minecraft.getInstance().setScreen(null));
        step(5, () -> {
            Slot slot = Minecraft.getInstance().player.inventoryMenu.slots.get(FROM);
            report("slot positions restored after close", slot.x == vanillaX && slot.y == vanillaY);
        });
        MinecraftForge.EVENT_BUS.addListener(ContainerCheck::tick);
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
                LOG.info("[ixchel-shade] container done, {} check(s) failed", failures);
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
            inv.setItem(FROM, new ItemStack(Items.IRON_BARS, 2));
            inv.setItem(0, new ItemStack(Items.STICK, 3));
        });
    }

    private static void open() {
        Minecraft mc = Minecraft.getInstance();
        InventoryMenu menu = mc.player.inventoryMenu;
        vanillaX = menu.slots.get(FROM).x;
        vanillaY = menu.slots.get(FROM).y;
        mc.setScreen(new UiContainerScreen<>(menu, mc.player.getInventory(), Component.literal("Inventory"), new Slots(),
                UiContainerScreen.SlotItemRendering.IXCHEL));
    }

    private static AbstractContainerScreen<?> screen() {
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> s) {
            return s;
        }
        throw new IllegalStateException("container screen is not open: " + Minecraft.getInstance().screen);
    }

    private static void click(int index) {
        AbstractContainerScreen<?> s = screen();
        Slot slot = s.getMenu().slots.get(index);
        double x = s.getGuiLeft() + slot.x + 8;
        double y = s.getGuiTop() + slot.y + 8;
        s.mouseMoved(x, y);
        s.mouseClicked(x, y, 0);
        s.mouseReleased(x, y, 0);
    }

    private static void report(String what, boolean pass) {
        if (!pass) {
            failures++;
        }
        LOG.info("[ixchel-shade] container {}: {}", what, pass ? "PASS" : "FAIL");
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> { });
    }

    private static void onServer(Consumer<Inventory> change) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            change.accept(p.getInventory());
        });
    }

    /** Slot index lists for the template, in menu order. */
    @UiTemplate("ixchelshadea:ui/inventory.html")
    static final class Slots extends ViewModel {
        final Value<List<Integer>> armor = state(range(5, 9));
        final Value<List<Integer>> main = state(range(9, 36));
        final Value<List<Integer>> hotbar = state(range(36, 45));
        final Value<List<Integer>> crafting = state(range(1, 5));

        private static List<Integer> range(int from, int to) {
            return IntStream.range(from, to).boxed().toList();
        }
    }
}
