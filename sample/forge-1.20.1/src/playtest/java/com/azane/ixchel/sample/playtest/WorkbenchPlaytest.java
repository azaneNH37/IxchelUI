package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.sample.workbench.Workbench;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@code -Pplaytest=workbench}: with materials for two diamond pickaxes and none for a beacon, the
 * workbench lists the pickaxe as craftable and the beacon as short, marks the beacon's missing
 * materials invalid and disables Craft; the search narrows the list; crafting two pickaxes reaches
 * the server's inventory and uses the diamonds up. {@code -Precord=workbench} records the walk.
 */
public final class WorkbenchPlaytest {
    private WorkbenchPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("workbench", s -> {
            setUp(s);
            s.expect("the workbench reports no errors", () -> Playtest.document().diagnostics().errorCount() == 0);
            s.take("workbench", WorkbenchPlaytest::showcase);
        });
    }

    /** Setup, showcase and teardown, for the reel. */
    static void scene(Playtest s) {
        setUp(s);
        showcase(s);
        s.run(() -> Minecraft.getInstance().setScreen(null));
        s.server(p -> p.getInventory().clearContent());
    }

    /** Materials for the pickaxe and short of the beacon; the workbench open. */
    private static void setUp(Playtest s) {
        s.server(p -> {
            p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(Items.DIAMOND, 6));
            p.getInventory().add(new ItemStack(Items.STICK, 8));
            p.getInventory().add(new ItemStack(Items.GLASS, 3));
            p.getInventory().add(new ItemStack(Items.IRON_INGOT, 12));
        });
        s.pause(5);
        s.run(Workbench::open);
        s.waitUntil("the workbench opens", Playtest::isOpen, Playtest.WAIT);
    }

    private static void showcase(Playtest t) {
        t.hold(800);
        t.click(".cat-wonders .ix-tab-button");
        t.hold(600);
        t.waitUntil("the wonders tab lists its six recipes", () -> Playtest.query(".recipe").size() == 6, Playtest.WAIT);
        t.clickText(".recipe .name", Items.BEACON.getDescription().getString());
        t.pause(2);
        // Fails if the craftability does not follow the inventory.
        t.expect("the beacon is short and Craft is disabled", () ->
                Playtest.query(".recipe.ix-selected.short").size() == 1
                        && Playtest.query(".mat.ix-invalid").size() == 3
                        && Playtest.query(".craft:disabled").size() == 1);
        t.shot("workbench-short");
        t.hold(1500);
        t.click(".cat-tools .ix-tab-button");
        t.pause(2);
        t.expect("the tools tab lists only tools", () -> Playtest.query(".recipe").size() == 6);
        t.hold(600);
        t.click(".search .ix-search-input");
        t.type("pick");
        t.pause(2);
        t.expect("the search narrows the list to the pickaxe", () -> Playtest.query(".recipe").size() == 1);
        t.hold(600);
        t.click(".recipe");
        t.pause(2);
        t.expect("the pickaxe is craftable twice", () -> Playtest.query(".recipe.ix-selected.short").isEmpty()
                && Playtest.query(".mat.ix-invalid").isEmpty() && Playtest.query(".craft:disabled").isEmpty());
        t.hold(1200);
        t.click(".times .ix-stepper-inc");
        t.hold(500);
        t.shot("workbench-ready");
        t.click(".craft");
        t.pause(5);
        t.expect("Craft shows the assembly", () -> Playtest.query(".craft.ix-busy").size() == 1);
        // Fails if the packet does not reach the server or the server does not take the diamonds.
        t.waitUntilServer("the server crafts two pickaxes from the diamonds", p ->
                p.getInventory().countItem(Items.DIAMOND_PICKAXE) == 2
                        && p.getInventory().countItem(Items.DIAMOND) == 0, Playtest.WAIT);
        t.hold(400);
        t.shot("workbench-crafted");
        t.hold(2000);
    }
}
