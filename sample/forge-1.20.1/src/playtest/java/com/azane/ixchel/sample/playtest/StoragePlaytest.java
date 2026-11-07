package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.event.Keys;
import com.azane.ixchel.mc.FrameTimer;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.sample.registry.ModItems;
import com.azane.ixchel.sample.storage.TerminalMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code -Pplaytest=storage}: chests under the player hold one full stack of every registered item
 * (about 1300 kinds); the terminal opens from the hand. Then the virtual grid (only the rows in view
 * built, scrolling, frame time while scrolling), the search box (typing, Ctrl+A/C/V, {@code @mod}),
 * sorting, take / half / shift-take / store / store one / quick move into the storage, and the
 * inventory's scrolled slots drawn and hidden by Ixchel.
 */
public final class StoragePlaytest {
    private static final int COLUMNS = 9;
    /** Six rows in view and two spare. */
    private static final int BUILT_CELLS = 8 * COLUMNS;
    private static final int CELL = 18;
    private static final int TIMED_ROWS = 60;
    /*
     * About twice the worst p95 on the dev machine (AMD iGPU, 2026-10-29): scrolling a row per tick,
     * median 1.6-2.8 ms / p95 2.8-8.4 ms over four runs. Each step rebuilds the 72 built cells' rows and
     * lays them out again.
     */
    private static final double SCROLL_BUDGET_US = 14000;
    private static final int HIDDEN_BELOW = -1000;
    /** Stored in more stacks than the one every kind has, most first: the side column's list. */
    private static final Item[] EXTRA = {Items.REDSTONE, Items.IRON_INGOT, Items.OAK_LOG, Items.GLOWSTONE_DUST};
    private static final int[] EXTRA_STACKS = {40, 25, 14, 8};
    private static final List<String> MOST = List.of("Redstone Dust", "Iron Ingot", "Oak Log", "Glowstone Dust");

    private static final List<BlockPos> chests = new ArrayList<>();
    /** Kinds placed in the chests, and how many of them are from minecraft. */
    private static final int[] kinds = {0, 0};

    private StoragePlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("storage", s -> {
            s.server(StoragePlaytest::fill);
            s.server(p -> p.getInventory().setItem(0, new ItemStack(ModItems.STORAGE_TERMINAL.get())));
            s.waitUntil("terminal in the hand", () -> {
                var player = Minecraft.getInstance().player;
                player.getInventory().selected = 0;
                return player.getMainHandItem().is(ModItems.STORAGE_TERMINAL.get());
            }, Playtest.WAIT);
            // The real right-click path: the client uses the item, the server opens the menu.
            s.run(() -> Minecraft.getInstance().gameMode.useItem(Minecraft.getInstance().player, InteractionHand.MAIN_HAND));
            // Fails if the scan misses chests, the first sync is not a full snapshot, or the view has errors.
            s.waitUntil("terminal open without errors, every kind synced", () -> Playtest.menu(TerminalMenu.class) != null
                    && Playtest.document().diagnostics().errorCount() == 0
                    && Playtest.text(".summary").equals(kinds[0] + " / " + kinds[0]), 40);
            s.run(() -> s.record("every item fits the chests: " + kinds[0] + " kinds",
                    kinds[0] == BuiltInRegistries.ITEM.size() - 1 && kinds[0] > 1000));
            // Fails if the grid builds a cell per entry instead of only the rows in view.
            s.run(() -> s.record("only the rows in view are built: " + Playtest.query(".cell").size() + " cells, scroll range "
                    + view().maxScrollTop(), Playtest.query(".cell").size() == BUILT_CELLS
                    && view().maxScrollTop() == rows(kinds[0]) * CELL - 6 * CELL));
            s.expect("every inventory slot placed", () -> Playtest.query("mc-slot").size() == 36);
            // Fails if the side column does not rank the kinds by their total count.
            s.expect("the most stored kinds, most first, with the total", () ->
                    Playtest.texts(".top-name").equals(MOST) && Playtest.text(".top-count").equals("2.6k"));
            s.hover(".cell mc-item");
            s.shot("storage-open");
            s.take("storage", t -> {
                t.hold(1500);
                for (int i = 0; i < 24; i++) {
                    t.scroll(".view", -1);
                }
                t.hold(600);
                t.click(".search");
                t.type("diamond");
                t.hold(1500);
                t.key(GLFW.GLFW_KEY_A, Keys.MOD_CTRL);
                t.type("@ixchel_sample");
                t.hold(1500);
                t.key(GLFW.GLFW_KEY_A, Keys.MOD_CTRL);
                t.key(GLFW.GLFW_KEY_BACKSPACE);
                t.hold(800);
            });
            s.waitUntil("the showcase leaves the grid at the top, unfiltered", () -> view().scrollTop() == 0
                    && Playtest.text(".summary").equals(kinds[0] + " / " + kinds[0]), Playtest.WAIT);

            // Fails if the built rows do not follow the scroll offset (a blank or stale view).
            s.scroll(".view", -1);
            s.waitUntil("one row down: the built rows start at the first row in view", () -> view().scrollTop() == CELL
                    && drawnTop(Playtest.query(".cell").get(0)) == view().box().y(), Playtest.WAIT);
            s.run(() -> FrameTimer.screen(4096));
            for (int i = 0; i < TIMED_ROWS; i++) {
                s.scroll(".view", -1);
            }
            s.run(() -> FrameTimer.expect(s, "grid scrolling a row per tick", SCROLL_BUDGET_US));
            s.scroll(".view", -1000);
            s.waitUntil("at the end: the last rows are built and in view", () -> {
                List<Element> cells = Playtest.query(".cell");
                Element last = cells.get(cells.size() - 1);
                return view().scrollTop() == view().maxScrollTop() && !cells.isEmpty()
                        && drawnTop(last) + CELL <= view().box().y() + view().box().height();
            }, Playtest.WAIT);
            s.shot("storage-end");

            // Search: typing filters, and a new search starts at the top.
            s.click(".search");
            s.type("diamond");
            s.waitUntil("'diamond' lists only diamond items, from the top", () -> {
                int expected = (int) matching("diamond").count();
                return view().scrollTop() == 0 && Playtest.text(".summary").equals(expected + " / " + kinds[0])
                        && shownStacks().stream().allMatch(st -> name(st).contains("diamond"));
            }, Playtest.WAIT);
            s.shot("storage-search");
            // Fails if the shortcuts or the clipboard port do not reach the input.
            s.key(GLFW.GLFW_KEY_A, Keys.MOD_CTRL);
            s.key(GLFW.GLFW_KEY_C, Keys.MOD_CTRL);
            s.key(GLFW.GLFW_KEY_BACKSPACE);
            s.waitUntil("select all and delete clears the search", () ->
                    Playtest.text(".summary").equals(kinds[0] + " / " + kinds[0]), Playtest.WAIT);
            s.key(GLFW.GLFW_KEY_V, Keys.MOD_CTRL);
            s.waitUntil("paste brings 'diamond' back", () -> Minecraft.getInstance().keyboardHandler.getClipboard().equals("diamond")
                    && shownStacks().stream().allMatch(st -> name(st).contains("diamond")), Playtest.WAIT);
            s.key(GLFW.GLFW_KEY_A, Keys.MOD_CTRL);
            s.type("@minecraft");
            s.waitUntil("'@minecraft' keeps the minecraft namespace only", () ->
                    Playtest.text(".summary").equals(kinds[1] + " / " + kinds[0])
                            && shownStacks().stream().allMatch(st -> mod(st).equals("minecraft")), Playtest.WAIT);
            s.key(GLFW.GLFW_KEY_A, Keys.MOD_CTRL);
            s.type("@ixchel_sample");
            s.waitUntil("'@ixchel_sample' lists the sample's items", () ->
                    shownStacks().stream().allMatch(st -> mod(st).equals("ixchel_sample")) && shownStacks().size() == 2, Playtest.WAIT);
            s.key(GLFW.GLFW_KEY_A, Keys.MOD_CTRL);
            s.key(GLFW.GLFW_KEY_BACKSPACE);

            // Sorting. Fails if a key or the direction is ignored.
            s.click(".sort");
            s.waitUntil("by count, ascending: a single stack first", () -> Playtest.text(".sort span").equals("Count")
                    && Playtest.text(".count").equals("1"), Playtest.WAIT);
            s.click(".direction");
            s.waitUntil("by count, descending: the 41 stacks of redstone first", () -> Playtest.text(".direction span").equals("Desc")
                    && Playtest.text(".count").equals("2.6k"), Playtest.WAIT);
            s.click(".sort");
            s.waitUntil("by mod, descending: minecraft first", () -> Playtest.text(".sort span").equals("Mod")
                    && mod(shownStacks().get(0)).equals("minecraft"), Playtest.WAIT);
            s.click(".direction");
            s.waitUntil("by mod, ascending: the sample's items first", () -> mod(shownStacks().get(0)).equals("ixchel_sample"), Playtest.WAIT);
            s.click(".sort");
            s.waitUntil("back to by name", () -> Playtest.text(".sort span").equals("Name"), Playtest.WAIT);

            // Take and store, on cobblestone: the first match by name. Each waits for the server's answer.
            s.click(".search");
            s.type("cobblestone");
            s.waitUntil("cobblestone first", () -> firstIs(Items.COBBLESTONE, "64"), Playtest.WAIT);
            s.click(".cell");
            s.waitUntil("click takes the stack onto the cursor", () -> carried(Items.COBBLESTONE, 64)
                    && !shownStacks().get(0).is(Items.COBBLESTONE), Playtest.WAIT);
            s.shot("storage-carrying");
            // On the grid's empty part: the click stores. On a cell it would too, the take being refused.
            s.run(() -> clickEmptyGrid(0));
            s.waitUntil("click with a stack stores it", () -> carried(Items.AIR, 0) && firstIs(Items.COBBLESTONE, "64"), Playtest.WAIT);
            s.click(".cell", 1);
            s.waitUntil("right click takes half", () -> carried(Items.COBBLESTONE, 32) && firstIs(Items.COBBLESTONE, "32"), Playtest.WAIT);
            s.run(() -> clickEmptyGrid(1));
            s.waitUntil("right click with a stack stores one", () -> carried(Items.COBBLESTONE, 31)
                    && firstIs(Items.COBBLESTONE, "33"), Playtest.WAIT);
            s.run(() -> clickEmptyGrid(0));
            s.waitUntil("and the rest", () -> carried(Items.AIR, 0) && firstIs(Items.COBBLESTONE, "64"), Playtest.WAIT);
            s.click(".cell", 0, Keys.MOD_SHIFT);
            s.waitUntilServer("shift click moves a stack into the inventory", p ->
                    p.getInventory().countItem(Items.COBBLESTONE) == 64, Playtest.WAIT);
            s.waitUntil("and it leaves the storage", () -> !shownStacks().get(0).is(Items.COBBLESTONE), Playtest.WAIT);
            // Fails if quickMoveStack does not put inventory stacks into the storage.
            s.run(() -> {
                TerminalMenu menu = Playtest.menu(TerminalMenu.class);
                for (int i = 0; i < menu.slots.size(); i++) {
                    if (menu.slots.get(i).getItem().is(Items.COBBLESTONE)) {
                        Minecraft mc = Minecraft.getInstance();
                        mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
                        return;
                    }
                }
                throw new IllegalStateException("no cobblestone in the inventory slots");
            });
            s.waitUntilServer("shift click on the inventory stores the stack", p ->
                    p.getInventory().countItem(Items.COBBLESTONE) == 0, Playtest.WAIT);
            s.waitUntil("and the grid shows it again", () -> firstIs(Items.COBBLESTONE, "64"), Playtest.WAIT);

            // The inventory scrolls; Ixchel draws its items clipped and hides the slots out of view.
            // Fails if a clipped slot can still be hovered or clicked, or the scroll offset is not followed.
            s.expect("three inventory rows in view, the hotbar hidden", () -> Playtest.slot(0).x > HIDDEN_BELOW
                    && Playtest.slot(26).x > HIDDEN_BELOW && Playtest.slot(27).x < HIDDEN_BELOW && Playtest.slot(35).x < HIDDEN_BELOW);
            s.scroll(".inventory", -1);
            s.waitUntil("one row down: the first row hidden, the hotbar in view", () -> Playtest.slot(0).x < HIDDEN_BELOW
                    && Playtest.slot(9).x > HIDDEN_BELOW && Playtest.slot(27).x > HIDDEN_BELOW, Playtest.WAIT);
            s.run(() -> Playtest.hoverSlot(27));
            s.waitUntil("the terminal's hotbar slot is hovered where it is drawn", () ->
                    Playtest.containerScreen().getSlotUnderMouse() == Playtest.slot(27), Playtest.WAIT);
            s.shot("storage-inventory");
            s.run(() -> Playtest.hoverAt(0, 0));

            // Moving the terminal out of the hand closes the menu.
            s.server(p -> p.getInventory().setItem(0, ItemStack.EMPTY));
            s.waitUntil("terminal out of the hand: the screen closes", () -> Playtest.menu(TerminalMenu.class) == null, 40);
            s.server(StoragePlaytest::clear);
        });
    }

    /** Two layers of chests, 7 by 7, under the player, filled with a full stack of every item in registry order. */
    private static void fill(net.minecraft.server.level.ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        p.teleportTo(l, 0.5, -60, 0.5, 180, 0);
        chests.clear();
        for (int y = -62; y >= -63; y--) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    l.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
                    chests.add(pos);
                }
            }
        }
        int chest = 0;
        int slot = 0;
        kinds[0] = 0;
        kinds[1] = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            if (slot == 27) {
                slot = 0;
                chest++;
            }
            if (chest == chests.size() || !(l.getBlockEntity(chests.get(chest)) instanceof ChestBlockEntity be)) {
                break;
            }
            be.setItem(slot++, new ItemStack(item, item.getMaxStackSize()));
            kinds[0]++;
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) {
                kinds[1]++;
            }
        }
        // More stacks of a few, for the most stored list.
        for (int i = 0; i < EXTRA.length; i++) {
            for (int n = 0; n < EXTRA_STACKS[i]; n++) {
                if (slot == 27) {
                    slot = 0;
                    chest++;
                }
                if (chest < chests.size() && l.getBlockEntity(chests.get(chest)) instanceof ChestBlockEntity be) {
                    be.setItem(slot++, new ItemStack(EXTRA[i], 64));
                }
            }
        }
    }

    private static void clear(net.minecraft.server.level.ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        for (BlockPos pos : chests) {
            if (l.getBlockEntity(pos) instanceof ChestBlockEntity be) {
                be.clearContent();
            }
            l.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
        }
        chests.clear();
    }

    private static Element view() {
        return Playtest.query(".view").get(0);
    }

    /** Where an element in the grid is drawn: its box moved up by the view's scroll offset. */
    private static float drawnTop(Element e) {
        return e.box().y() - view().scrollTop();
    }

    private static int rows(int count) {
        return (count + COLUMNS - 1) / COLUMNS;
    }

    /** A click on the grid below the cells (the searches here leave less than a row). */
    private static void clickEmptyGrid(int button) {
        Element v = view();
        Playtest.clickAt(v.box().x() + v.box().width() / 2, v.box().y() + v.box().height() - 4, button);
    }

    private static List<ItemStack> shownStacks() {
        return Playtest.query(".cell mc-item").stream().map(e -> (ItemStack) e.payload()).toList();
    }

    private static boolean firstIs(Item item, String count) {
        List<ItemStack> shown = shownStacks();
        return !shown.isEmpty() && shown.get(0).is(item) && Playtest.text(".count").equals(count);
    }

    private static boolean carried(Item item, int count) {
        ItemStack c = Playtest.menu(TerminalMenu.class).getCarried();
        return item == Items.AIR ? c.isEmpty() : c.is(item) && c.getCount() == count;
    }

    private static java.util.stream.Stream<Item> matching(String word) {
        return BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR && name(new ItemStack(i)).contains(word));
    }

    private static String name(ItemStack stack) {
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT);
    }

    private static String mod(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }
}
