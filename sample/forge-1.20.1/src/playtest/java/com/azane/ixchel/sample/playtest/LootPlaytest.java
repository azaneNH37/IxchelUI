package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Transform;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.playtest.TakeOptions;
import com.azane.ixchel.mc.FrameTimer;
import com.azane.ixchel.mc.UiHud;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.sample.loot.LootScreens;
import com.azane.ixchel.sample.loot.PickUpDropPacket;
import com.azane.ixchel.sample.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;

/**
 * {@code -Pplaytest=loot}: drops four items of the four rarities around the player standing at the
 * origin facing north (three ahead, one behind), asserts the HUD's brackets and nearby list, then the
 * list screen: order, filter, pick-up in and out of range, and the pickup toasts merging. Last, with two dozen drops in a row, it times the
 * list while the player walks along the row and the order changes every tick.
 */
public final class LootPlaytest {
    private static final int TIMED_TICKS = 150;
    /*
     * Budgets are about twice the p95 on the dev machine (AMD iGPU, 2026-10-28): still median 3.6 ms /
     * p95 6.9 ms, reordering 4.0 / 6.6 ms. Reordering adds little; the time is in drawing the rows,
     * the ones scrolled out of the list included (clipping is a scissor, commands are not culled).
     */
    private static final double STILL_BUDGET_US = 14000;
    private static final double BUDGET_US = 14000;
    private static final Item[] ROW = {Items.STONE, Items.DIRT, Items.APPLE, Items.BONE, Items.STRING, Items.FEATHER};

    private LootPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("loot", s -> {
            UiHud hud = LootScreens.hud();
            setUp(s);
            s.expect("brackets tagged with name and count", () -> Playtest.texts(hud, ".mark .tag")
                    .containsAll(List.of("Cobblestone x12", "Bottle o' Enchanting", "Golden Apple x2")));
            s.expect("nearby distances", () -> Playtest.texts(hud, ".near-distance")
                    .equals(List.of("4.0 m", "5.0 m", "6.0 m", "10.0 m")));
            // Fails if a bracket is not centered over its drop (straight ahead: the middle of the screen).
            s.expect("bracket centered over the drop ahead", () -> {
                Element e = tagged(hud, "Cobblestone x12");
                float mid = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2f;
                return e != null && Math.abs(e.box().x() + e.box().width() / 2 - mid) < 2;
            });
            s.shot("loot-hud");
            // Recorded: the player looks round (brackets slide, the nearby arrows turn), then picks things
            // up; the toasts are gone again before the list's assertions.
            s.take("loot", LootPlaytest::showcase);
            s.run(() -> place(Minecraft.getInstance().player.getX(), 180));
            s.waitUntil("no toasts left", () -> Playtest.query(hud, ".toast").isEmpty(), 100);
            s.run(LootScreens::openList);
            // Fails if the list is not sorted by distance or the direction arrows are turned wrong.
            s.waitUntil("list nearest first, arrows ahead and behind", () -> Playtest.isOpen()
                    && Playtest.texts(".row .name").equals(List.of(
                    "Cobblestone", "Enchanted Golden Apple", "Bottle o' Enchanting", "Golden Apple"))
                    && sectors(Playtest.query(".row .arrow")).equals(List.of(0, 4, 0, 0)), Playtest.WAIT);
            // Fails if the button is not disabled out of range.
            s.expect("only the golden apple at 10 m is out of pick-up range", () -> Playtest.query(".pick:disabled").size() == 1);
            s.shot("loot-list");
            s.take("loot-list", TakeOptions.window(), u -> u.hold(2500));
            s.clickText(".filter", "Rare+");
            // Fails if the filter does not apply.
            s.waitUntil("Rare+ leaves the two apples", () -> Playtest.query(".row").size() == 2
                    && Playtest.text(".filter.ix-selected").equals("Rare+"), Playtest.WAIT);
            s.clickText(".filter", "All");
            s.waitUntil("All shows four again", () -> Playtest.query(".row").size() == 4, Playtest.WAIT);
            s.click(".pick");
            // Fails if the click does not reach the server or the pickup is not reflected back.
            s.waitUntilServer("pick-up gives the 12 cobblestone", p -> p.getInventory().countItem(Items.COBBLESTONE) == 12,
                    Playtest.WAIT);
            s.waitUntil("its row goes, a toast shows it", () -> Playtest.query(".row").size() == 3
                    && Playtest.texts(hud, ".toast-name").equals(List.of("Cobblestone"))
                    && Playtest.texts(hud, ".toast-count").equals(List.of("+12")), Playtest.WAIT);
            // Fails if a second gain of the same item adds a toast instead of merging into the first.
            s.server(p -> p.getInventory().add(new ItemStack(Items.COBBLESTONE, 5)));
            s.waitUntil("a second gain merges: one toast, +17", () -> Playtest.texts(hud, ".toast-count").equals(List.of("+17")),
                    Playtest.WAIT);
            // Fails if more than five toasts stay: the oldest goes first.
            s.server(p -> List.of(Items.STICK, Items.FLINT, Items.COAL, Items.CLAY_BALL, Items.BRICK)
                    .forEach(i -> p.getInventory().add(new ItemStack(i))));
            // Gained in one read, the five come in no particular order; the cobblestone may still be sliding out.
            s.waitUntil("six item kinds gained, five toasts, the cobblestone gone", () -> {
                List<String> names = Playtest.texts(hud, ".toast:not(:-ix-exiting) .toast-name");
                return names.size() == 5 && Set.copyOf(names).equals(Set.of("Stick", "Flint", "Coal", "Clay Ball", "Brick"));
            }, Playtest.WAIT);
            // Asked directly, past the disabled button: fails if the server does not check the range.
            s.run(() -> Network.sendToServer(new PickUpDropPacket(entityOf(Items.GOLDEN_APPLE))));
            s.pause(10);
            s.waitUntilServer("the server refuses a drop 10 m away", p -> p.getInventory().countItem(Items.GOLDEN_APPLE) == 0
                    && !p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12),
                    e -> e.getItem().is(Items.GOLDEN_APPLE)).isEmpty(), Playtest.WAIT);
            s.server(p -> {
                for (int i = 0; i < 24; i++) {
                    drop(p.serverLevel(), i - 11.5, -3.5, new ItemStack(ROW[i % ROW.length]));
                }
            });
            s.waitUntil("27 rows", () -> Playtest.query(".row").size() == 27, Playtest.WAIT);
            // Standing still nothing changes: the baseline for drawing 27 rows (and their items).
            s.run(() -> FrameTimer.screen(4096));
            s.pause(100);
            s.run(() -> FrameTimer.expect(s, "list standing still", STILL_BUDGET_US));
            // Walking along the row reorders the list every tick; rows are keyed by entity id. Fails if
            // that costs more than the budget at p95, or the list ends unsorted.
            s.run(() -> FrameTimer.screen(4096));
            s.take("loot-moving", TakeOptions.window(), u -> {
                for (int i = 0; i < TIMED_TICKS; i++) {
                    double x = -11.5 + 23.0 * i / TIMED_TICKS;
                    u.run(() -> place(x, 180));
                }
            });
            s.run(() -> FrameTimer.expect(s, "list reordering every tick", BUDGET_US));
            s.expect("list still nearest first", LootPlaytest::sorted);
            s.shot("loot-moving");
            tearDown(s);
        });
    }

    /** Setup, showcase and teardown, for the reel: the HUD, then a look at the list. */
    static void scene(Playtest s) {
        setUp(s);
        showcase(s);
        s.run(() -> place(Minecraft.getInstance().player.getX(), 180));
        s.run(LootScreens::openList);
        s.waitUntil("the list opens", Playtest::isOpen, Playtest.WAIT);
        s.hold(2500);
        tearDown(s);
    }

    /** Four drops round the player in a dark spot, the HUD shown and tracking them. */
    private static void setUp(Playtest s) {
        UiHud hud = LootScreens.hud();
        s.server(p -> {
            ServerLevel l = p.serverLevel();
            l.getEntitiesOfClass(ItemEntity.class, new AABB(-40, -70, -40, 40, -40, 40)).forEach(Entity::discard);
            drop(l, 0.5, -3.5, new ItemStack(Items.COBBLESTONE, 12));
            drop(l, 0.5, -5.5, new ItemStack(Items.EXPERIENCE_BOTTLE));
            drop(l, 0.5, -9.5, new ItemStack(Items.GOLDEN_APPLE, 2));
            drop(l, 0.5, 5.5, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
            p.teleportTo(l, 0.5, -60, 0.5, 180, 0);
        });
        s.run(() -> hud.show(true));
        // Fails if drops are not scanned or projected, or the nearby arrows do not point at them.
        s.waitUntil("three brackets in view; nearby list nearest first, the one behind pointed back",
                () -> Playtest.query(hud, ".mark").size() == 3
                && Playtest.texts(hud, ".near-name").equals(List.of(
                "Cobblestone x12", "Enchanted Golden Apple", "Bottle o' Enchanting", "Golden Apple x2"))
                && sectors(Playtest.query(hud, ".near-arrow")).equals(List.of(0, 4, 0, 0)), Playtest.WAIT);
    }

    /** The player looks round (brackets slide, the nearby arrows turn), then picks things up. */
    private static void showcase(Playtest t) {
        t.hold(1000);
        for (int i = 1; i <= 80; i++) {
            float yaw = 180 + (float) (35 * Math.sin(i * Math.PI / 40));
            t.run(() -> place(Minecraft.getInstance().player.getX(), yaw));
        }
        t.server(p -> p.getInventory().add(new ItemStack(Items.IRON_INGOT, 3)));
        t.hold(500);
        t.server(p -> p.getInventory().add(new ItemStack(Items.GOLD_INGOT, 2)));
        t.hold(500);
        t.server(p -> p.getInventory().add(new ItemStack(Items.IRON_INGOT, 4)));
        t.hold(400);
        t.server(p -> p.getInventory().add(new ItemStack(Items.DIAMOND)));
        t.hold(400);
        t.server(p -> p.getInventory().add(new ItemStack(Items.IRON_INGOT, 2)));
        t.hold(3600);
    }

    /** The HUD hidden, the drops and the items gained gone. */
    private static void tearDown(Playtest s) {
        s.run(() -> {
            Minecraft.getInstance().setScreen(null);
            LootScreens.hud().show(false);
        });
        s.server(p -> {
            p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(-40, -70, -40, 40, -40, 40))
                    .forEach(Entity::discard);
            // The toast items, so later playtests start from the inventory they expect.
            List<Item> gained = List.of(Items.STICK, Items.FLINT, Items.COAL, Items.CLAY_BALL, Items.BRICK,
                    Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND);
            p.getInventory().clearOrCountMatchingItems(st -> gained.contains(st.getItem()), -1, p.inventoryMenu.getCraftSlots());
        });
    }

    /**
     * Puts the local player at {@code x} (on the row's line) facing {@code yaw}. One a step, a step a
     * tick: the tick keeps the old values, so frames between ticks glide from one step to the next.
     */
    private static void place(double x, float yaw) {
        LocalPlayer p = Minecraft.getInstance().player;
        p.setPos(x, -60, 0.5);
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
    }

    private static void drop(ServerLevel l, double x, double z, ItemStack stack) {
        ItemEntity e = new ItemEntity(l, x, -60, z, stack, 0, 0, 0);
        // Only the radar picks it up: the playtest walks past the drops.
        e.setNeverPickUp();
        l.addFreshEntity(e);
    }

    /** The bracket whose tag reads {@code text}. */
    private static Element tagged(UiHud hud, String text) {
        return Playtest.query(hud, ".mark .tag").stream().filter(e -> e.textContent().equals(text))
                .map(Element::parent).findFirst().orElse(null);
    }

    /** The direction each arrow points, read from its {@code rotate()}, as a sector: 0 ahead, 4 behind. */
    private static List<Integer> sectors(List<Element> arrows) {
        return arrows.stream().map(a -> {
            Transform t = a.computed().get(CssProperties.TRANSFORM);
            float pointing = t.functions().get(0).x().value() + 45;
            return Math.floorMod(Math.round(pointing / 45), 8);
        }).toList();
    }

    private static int entityOf(Item item) {
        for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
            if (e instanceof ItemEntity i && i.getItem().is(item)) {
                return e.getId();
            }
        }
        throw new IllegalStateException("no drop of " + item);
    }

    private static boolean sorted() {
        int last = -1;
        for (Element e : Playtest.query(".row .distance")) {
            int d = Integer.parseInt(e.textContent().replace(" m", ""));
            if (d < last) {
                return false;
            }
            last = d;
        }
        return last >= 0;
    }
}
