package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.sample.machine.MachineMenu;
import com.azane.ixchel.sample.machine.MachineScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code -Pplaytest=machine}: places a powered stonecutter, fills it by quick moves, picks a recipe and
 * waits for the server to cut; then the config page (redstone mode, auto-output, priority, sides),
 * whose intents the server applies: the status follows the mode, a reopened menu starts from the
 * settings; then vanilla slot behavior on the laid-out slots (real-cursor hover, a refused slot,
 * panel vs outside clicks). With {@code -Pslotcompat} this runs next to JEI or EMI and Mouse Tweaks.
 * {@code -Precord=machine} records the run page working, the config page and the upgrades.
 */
public final class MachinePlaytest {
    private static final BlockPos[] at = {BlockPos.ZERO};
    private static final MachineMenu[] previous = {null};
    /** Inventory slot of the iron bars carried around in the slot assertions. */
    private static final int BARS = 3;

    private MachinePlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("machine", s -> {
            setUp(s);
            s
                .expect("idle without input", () -> Playtest.text(".status").equals("Idle: no input"))
                .run(() -> {
                    // The GUI area other mods avoid is the panel, not the whole screen.
                    AbstractContainerScreen<?> c = Playtest.containerScreen();
                    s.record("gui area is the panel: " + c.getGuiLeft() + "," + c.getGuiTop() + " " + c.getXSize() + "x"
                            + c.getYSize(), c.getXSize() > 0 && c.getXSize() < c.width && c.getYSize() < c.height);
                })
                // Hover through the real cursor, as vanilla, JEI and EMI read it during render.
                .run(() -> Playtest.hoverSlot(MachineMenu.HOTBAR_START + BARS))
                .waitUntil("bars slot is hovered", () -> Playtest.containerScreen().getSlotUnderMouse()
                        == Playtest.slot(MachineMenu.HOTBAR_START + BARS), Playtest.WAIT)
                .shot("machine-hover")
                .run(() -> Playtest.hoverAt(0, 0));
            fill(s);
            s.shot("machine-ready")
                    // Recorded: the machine working, its config page and upgrades, and a jam.
                    .take("machine", MachinePlaytest::showcase)
                    // Carrying the bars: the output slot refuses them, a click on the panel keeps them, a click
                    // outside drops them.
                    .run(() -> Playtest.clickSlot(MachineMenu.HOTBAR_START + BARS))
                    .run(() -> Playtest.clickSlot(MachineMenu.OUTPUT))
                    .expect("output slot refuses a stack", () -> menu().getCarried().is(Items.IRON_BARS))
                    .click(".ix-title")
                    .expect("panel click keeps the carried stack", () -> menu().getCarried().is(Items.IRON_BARS))
                    .run(() -> Playtest.clickAt(2, 2, 0))
                    .waitUntilServer("outside click drops it", p -> p.getInventory().countItem(Items.IRON_BARS) == 0
                            && p.containerMenu.getCarried().isEmpty(), Playtest.WAIT);
            tearDown(s);
        });
    }

    /** Setup, showcase and teardown, for the reel. */
    static void scene(Playtest s) {
        setUp(s);
        fill(s);
        showcase(s);
        tearDown(s);
    }

    /** The machine placed and open, the player carrying its inputs. */
    private static void setUp(Playtest s) {
        s
            .server(p -> {
                p.getInventory().setItem(0, new ItemStack(Items.COAL, 4));
                p.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));
                p.getInventory().setItem(2, new ItemStack(Items.STONE, 3));
                p.getInventory().setItem(BARS, new ItemStack(Items.IRON_BARS, 2));
                at[0] = MachineScreen.placeAndOpen(p);
            })
            .waitUntil("machine open, every slot placed without errors", () -> menu() != null
                    && Playtest.document().diagnostics().errorCount() == 0
                    && Playtest.query("mc-slot").size() == MachineMenu.SLOT_COUNT, Playtest.WAIT);
    }

    /** The inputs shift-clicked in: fuel burning, the tank filled, recipes listed. */
    private static void fill(Playtest s) {
        s
            // Fails if quickMoveStack sends a stack to the wrong machine slot.
            .run(() -> {
                quickMove(MachineMenu.HOTBAR_START);
                quickMove(MachineMenu.HOTBAR_START + 1);
                quickMove(MachineMenu.HOTBAR_START + 2);
            })
            .waitUntil("fuel burns, the bucket fills the tank, recipes for stone listed", () ->
                    Playtest.text(".status").equals("Pick a recipe") && !Playtest.attribute(".energy", "title").startsWith("0 ")
                            && Playtest.attribute(".tank", "title").equals("1000 / 4000 mB")
                            && Playtest.query(".recipe").size() >= 4, Playtest.WAIT)
            .expect("empty bucket left in the water slot", () -> menu().getSlot(MachineMenu.WATER_SLOT).getItem().is(Items.BUCKET));
    }

    private static void showcase(Playtest t) {
        t
                .hold(600)
                .click(".recipe")
                .waitUntil("first recipe selected, working", () -> Playtest.query(".recipe.ix-selected").size() == 1
                        && Playtest.text(".status").equals("Working"), Playtest.WAIT)
                .hover(".tank")
                .shot("machine-working")
                .hold(1500)
                .waitUntilServer("the server cuts a stone", p -> {
                    Container machine = MachineScreen.contents(p.level(), at[0]);
                    return machine != null && !machine.getItem(MachineMenu.OUTPUT).isEmpty()
                            && machine.getItem(MachineMenu.INPUT).getCount() == 2;
                }, 100)
                // The server reopens the machine over its open screen: the new screen's model watches
                // before the old one's closes, and the old close must not end the new watch.
                .run(() -> previous[0] = menu())
                .server(MachineScreen::placeAndOpen)
                .waitUntil("reopened over itself: the new screen still receives state", () -> menu() != null
                        && menu() != previous[0] && Playtest.text(".status").equals("Working"), Playtest.WAIT)
                .hold(800)
                .click(".tab-config .ix-tab-button")
                .waitUntil("config page shown, run page hidden", () -> Playtest.query(".config.shown").size() == 1
                        && Playtest.query(".run.shown").isEmpty(), Playtest.WAIT)
                .click(".redstone .ix-cycle-button")
                .waitUntil("needs a signal: stops", () -> Playtest.text(".redstone span").equals("Needs signal")
                        && Playtest.text(".status").equals("Redstone off"), Playtest.WAIT)
                .hold(700)
                .click(".redstone .ix-cycle-button")
                .waitUntil("needs no signal: works", () -> Playtest.text(".redstone span").equals("Needs no signal")
                        && Playtest.text(".status").equals("Working"), Playtest.WAIT)
                .hold(700)
                // A right click steps back.
                .click(".redstone .ix-cycle-button", 1)
                .click(".redstone .ix-cycle-button", 1)
                .waitUntil("back to ignored: works again", () -> Playtest.text(".redstone span").equals("Ignored")
                        && Playtest.text(".status").equals("Working"), Playtest.WAIT)
                .click(".auto-output")
                .waitUntil("auto-output switched on", () -> Playtest.query(".auto-output").get(0).control().checked(), Playtest.WAIT)
                .click(".priority .ix-stepper-inc")
                .click(".priority .ix-stepper-inc")
                .click(".side")
                .waitUntil("down side cycles to input", () -> Playtest.text(".mode").equals("Input"), Playtest.WAIT)
                .hold(700)
                // Fails if a setting never reaches the server: the reopened menu starts from the server's.
                .run(() -> previous[0] = menu())
                .server(MachineScreen::placeAndOpen)
                .waitUntil("reopened with auto-output on and priority 2", () -> menu() != null && menu() != previous[0]
                        && Playtest.query(".auto-output").get(0).control().checked()
                        && Playtest.query(".priority .ix-stepper-input").get(0).control().value().equals("2"), Playtest.WAIT)
                .click(".tab-config .ix-tab-button")
                .shot("machine-config")
                .hold(1000)
                .click(".tab-upgrades .ix-tab-button")
                .waitUntil("upgrades page shown", () -> Playtest.query(".upgrades.shown .upgrade").size() == 3, Playtest.WAIT)
                .hold(1800)
                .click(".tab-run .ix-tab-button")
                // Another item in the output jams the machine; the status glitches. The input is topped
                // up and the recipe picked again first: a recorded run holds long enough to cut it all.
                .server(p -> MachineScreen.contents(p.level(), at[0]).setItem(MachineMenu.INPUT, new ItemStack(Items.STONE, 16)))
                .click(".recipe")
                .server(p -> MachineScreen.contents(p.level(), at[0]).setItem(MachineMenu.OUTPUT, new ItemStack(Items.DIRT)))
                .waitUntil("output full: jammed", () -> Playtest.text(".status").equals("Output full")
                        && Playtest.query(".status.jammed").size() == 1, Playtest.WAIT)
                .hold(1500)
                .server(p -> MachineScreen.contents(p.level(), at[0]).setItem(MachineMenu.OUTPUT, ItemStack.EMPTY))
                .waitUntil("output cleared: works", () -> Playtest.text(".status").equals("Working"), Playtest.WAIT)
                .hold(800);
    }

    /** Closed, the machine emptied and broken, its drops gone. */
    private static void tearDown(Playtest s) {
        s
            .run(() -> Minecraft.getInstance().player.closeContainer())
            .server(p -> {
                // Emptied first: dropped contents would be picked up by later playtests.
                Container machine = MachineScreen.contents(p.level(), at[0]);
                if (machine != null) {
                    machine.clearContent();
                }
                p.level().setBlockAndUpdate(at[0], Blocks.AIR.defaultBlockState());
                p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16)).forEach(Entity::discard);
            });
    }

    private static MachineMenu menu() {
        return Playtest.menu(MachineMenu.class);
    }

    /** Sent as a packet: vanilla reads Shift from the window, which an unattended run cannot hold. */
    private static void quickMove(int slot) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(menu().containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
    }
}
