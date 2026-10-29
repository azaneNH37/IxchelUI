package com.azane.ixchel.sample.check.scenario;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Transform;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.mc.UiHud;
import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.FrameTimer;
import com.azane.ixchel.sample.check.Script;
import com.azane.ixchel.sample.loot.LootScreens;
import com.azane.ixchel.sample.loot.PickUpDropPacket;
import com.azane.ixchel.sample.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * {@code -Pcheck=loot}: drops four items of the four rarities around the player standing at the
 * origin facing north (three ahead, one behind), checks the HUD's labels and edge arrow, then the
 * list: order, filter, pick-up in and out of range. Last, with two dozen drops in a row, it times the
 * list while the player walks along the row and the order changes every tick.
 */
public final class LootCheck {
    private static final int TIMED_TICKS = 150;
    /*
     * Budgets are about twice the p95 on the dev machine (AMD iGPU, 2026-10-28): still median 3.6 ms /
     * p95 6.9 ms, reordering 4.0 / 6.6 ms. Reordering adds little; the time is in drawing the rows,
     * the ones scrolled out of the list included (clipping is a scissor, commands are not culled).
     */
    private static final double STILL_BUDGET_US = 14000;
    private static final double BUDGET_US = 14000;
    private static final Item[] ROW = {Items.STONE, Items.DIRT, Items.APPLE, Items.BONE, Items.STRING, Items.FEATHER};

    private LootCheck() {
    }

    public static void register() {
        Checks.register("loot", s -> {
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
            // Fails if drops are not scanned or projected, or the one behind is not an arrow turned to point back.
            s.waitUntil("three labels in view, the drop behind as an arrow at the bottom", () -> Script.query(hud, ".label").size() == 3
                    && Script.texts(hud, ".edge .count").equals(List.of("1"))
                    && sectors(Script.query(hud, ".edge .arrow")).equals(List.of(4)), Script.WAIT);
            s.expect("labels name, count and distance", () -> Script.texts(hud, ".label")
                    .containsAll(List.of("Cobblestone x12 4 m", "Bottle o' Enchanting 6 m", "Golden Apple x2 10 m")));
            // Fails if a label is not centered over its drop (straight ahead: the middle of the screen).
            s.expect("label centered over the drop ahead", () -> {
                Element e = labeled(hud, "Cobblestone x12 4 m");
                float mid = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2f;
                return e != null && Math.abs(e.box().x() + e.box().width() / 2 - mid) < 2;
            });
            s.shot("loot-hud");
            s.run(LootScreens::openList);
            // Fails if the list is not sorted by distance or the direction arrows are turned wrong.
            s.waitUntil("list nearest first, arrows ahead and behind", () -> Script.isOpen()
                    && Script.texts(".row .name").equals(List.of(
                    "Cobblestone", "Enchanted Golden Apple", "Bottle o' Enchanting", "Golden Apple"))
                    && sectors(Script.query(".row .arrow")).equals(List.of(0, 4, 0, 0)), Script.WAIT);
            // Fails if the button is not disabled out of range.
            s.expect("only the golden apple at 10 m is out of pick-up range", () -> Script.query(".pick:disabled").size() == 1);
            s.shot("loot-list");
            s.clickText(".filter", "Rare+");
            // Fails if the filter does not apply.
            s.waitUntil("Rare+ leaves the two apples", () -> Script.query(".row").size() == 2
                    && Script.text(".filter.selected").equals("Rare+"), Script.WAIT);
            s.clickText(".filter", "All");
            s.waitUntil("All shows four again", () -> Script.query(".row").size() == 4, Script.WAIT);
            s.click(".pick");
            // Fails if the click does not reach the server or the pickup is not reflected back.
            s.waitUntilServer("pick-up gives the 12 cobblestone", p -> p.getInventory().countItem(Items.COBBLESTONE) == 12,
                    Script.WAIT);
            s.waitUntil("its row goes, a toast shows it", () -> Script.query(".row").size() == 3
                    && Script.texts(hud, ".toast .text").equals(List.of("+12 Cobblestone")), Script.WAIT);
            // Asked directly, past the disabled button: fails if the server does not check the range.
            s.run(() -> Network.sendToServer(new PickUpDropPacket(entityOf(Items.GOLDEN_APPLE))));
            s.pause(10);
            s.waitUntilServer("the server refuses a drop 10 m away", p -> p.getInventory().countItem(Items.GOLDEN_APPLE) == 0
                    && !p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12),
                    e -> e.getItem().is(Items.GOLDEN_APPLE)).isEmpty(), Script.WAIT);
            s.server(p -> {
                for (int i = 0; i < 24; i++) {
                    drop(p.serverLevel(), i - 11.5, -3.5, new ItemStack(ROW[i % ROW.length]));
                }
            });
            s.waitUntil("27 rows", () -> Script.query(".row").size() == 27, Script.WAIT);
            // Standing still nothing changes: the baseline for drawing 27 rows (and their items).
            s.run(() -> FrameTimer.screen(4096));
            s.pause(100);
            s.run(() -> FrameTimer.report(s, "list standing still", STILL_BUDGET_US));
            // Walking along the row reorders the list every tick; rows are keyed by entity id. Fails if
            // that costs more than the budget at p95, or the list ends unsorted.
            s.run(() -> FrameTimer.screen(4096));
            for (int i = 0; i < TIMED_TICKS; i++) {
                double x = -11.5 + 23.0 * i / TIMED_TICKS;
                s.run(() -> Minecraft.getInstance().player.setPos(x, -60, 0.5));
            }
            s.run(() -> FrameTimer.report(s, "list reordering every tick", BUDGET_US));
            s.expect("list still nearest first", LootCheck::sorted);
            s.shot("loot-moving");
            s.run(() -> {
                Minecraft.getInstance().setScreen(null);
                hud.show(false);
            });
            s.server(p -> p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(-40, -70, -40, 40, -40, 40))
                    .forEach(Entity::discard));
        });
    }

    private static void drop(ServerLevel l, double x, double z, ItemStack stack) {
        ItemEntity e = new ItemEntity(l, x, -60, z, stack, 0, 0, 0);
        // Only the radar picks it up: the check walks past the drops.
        e.setNeverPickUp();
        l.addFreshEntity(e);
    }

    private static Element labeled(UiHud hud, String text) {
        return Script.query(hud, ".label").stream().filter(e -> e.textContent().equals(text)).findFirst().orElse(null);
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
        for (Element e : Script.query(".row .distance")) {
            int d = Integer.parseInt(e.textContent().replace(" m", ""));
            if (d < last) {
                return false;
            }
            last = d;
        }
        return last >= 0;
    }
}
