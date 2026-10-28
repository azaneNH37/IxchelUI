package com.azane.ixchel.sample.check.scenario;

import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.Script;
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
 * {@code -Pcheck=machine}: places a powered stonecutter, fills it by quick moves, picks a recipe and
 * waits for the server to cut; then the redstone mode and the side configuration, each a click whose
 * intent the server applies and replicates back; then vanilla slot behavior on the laid-out slots
 * (real-cursor hover, a refused slot, panel vs outside clicks). With {@code -Pslotcompat} this runs
 * next to JEI or EMI and Mouse Tweaks.
 */
public final class MachineCheck {
    private static final BlockPos[] at = {BlockPos.ZERO};
    private static final MachineMenu[] previous = {null};
    /** Inventory slot of the iron bars carried around in the slot checks. */
    private static final int BARS = 3;

    private MachineCheck() {
    }

    public static void register() {
        Checks.register("machine", s -> s
                .server(p -> {
                    p.getInventory().setItem(0, new ItemStack(Items.COAL, 4));
                    p.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));
                    p.getInventory().setItem(2, new ItemStack(Items.STONE, 3));
                    p.getInventory().setItem(BARS, new ItemStack(Items.IRON_BARS, 2));
                    at[0] = MachineScreen.placeAndOpen(p);
                })
                .waitUntil("machine open, every slot placed without errors", () -> menu() != null
                        && Script.document().diagnostics().errorCount() == 0
                        && Script.query(".slot").size() == MachineMenu.SLOT_COUNT, Script.WAIT)
                .expect("idle without input", () -> Script.text(".status").equals("Idle: no input"))
                .run(() -> {
                    // The GUI area other mods avoid is the panel, not the whole screen.
                    AbstractContainerScreen<?> c = Script.containerScreen();
                    s.report("gui area is the panel: " + c.getGuiLeft() + "," + c.getGuiTop() + " " + c.getXSize() + "x"
                            + c.getYSize(), c.getXSize() > 0 && c.getXSize() < c.width && c.getYSize() < c.height);
                })
                // Hover through the real cursor, as vanilla, JEI and EMI read it during render.
                .run(() -> Script.hoverSlot(MachineMenu.HOTBAR_START + BARS))
                .waitUntil("bars slot is hovered", () -> Script.containerScreen().getSlotUnderMouse()
                        == Script.slot(MachineMenu.HOTBAR_START + BARS), Script.WAIT)
                .shot("machine-hover")
                .run(() -> Script.hoverAt(0, 0))
                // Fails if quickMoveStack sends a stack to the wrong machine slot.
                .run(() -> {
                    quickMove(MachineMenu.HOTBAR_START);
                    quickMove(MachineMenu.HOTBAR_START + 1);
                    quickMove(MachineMenu.HOTBAR_START + 2);
                })
                .waitUntil("fuel burns, the bucket fills the tank, recipes for stone listed", () ->
                        Script.text(".status").equals("Pick a recipe") && !Script.attribute(".energy", "title").startsWith("0 ")
                                && Script.attribute(".tank", "title").equals("1000 / 4000 mB")
                                && Script.query(".recipe").size() >= 4, Script.WAIT)
                .expect("empty bucket left in the water slot", () -> menu().getSlot(MachineMenu.WATER_SLOT).getItem().is(Items.BUCKET))
                .shot("machine-ready")
                .click(".recipe")
                .waitUntil("first recipe selected, working", () -> Script.query(".recipe.selected").size() == 1
                        && Script.text(".status").equals("Working"), Script.WAIT)
                .hover(".tank")
                .shot("machine-working")
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
                        && menu() != previous[0] && Script.text(".status").equals("Working"), Script.WAIT)
                .click(".redstone")
                .waitUntil("needs a signal: stops", () -> Script.text(".redstone span").equals("Redstone: Needs signal")
                        && Script.text(".status").equals("Redstone off"), Script.WAIT)
                // Each click sets the mode after the replicated one, so a click before the snapshot
                // arrives repeats the last mode instead of skipping one.
                .click(".redstone")
                .waitUntil("needs no signal: works", () -> Script.text(".redstone span").equals("Redstone: Needs no signal")
                        && Script.text(".status").equals("Working"), Script.WAIT)
                .click(".redstone")
                .waitUntil("back to ignored: works again", () -> Script.text(".redstone span").equals("Redstone: Ignored")
                        && Script.text(".status").equals("Working"), Script.WAIT)
                .click(".sides-toggle")
                .waitUntil("side panel slides out to its full width", () -> Script.box(".sides").width() == 80, Script.WAIT)
                .click(".side")
                .waitUntil("down side cycles to input", () -> Script.text(".mode").equals("Input"), Script.WAIT)
                .shot("machine-sides")
                // Carrying the bars: the output slot refuses them, a click on the panel keeps them, a click
                // outside drops them.
                .run(() -> Script.clickSlot(MachineMenu.HOTBAR_START + BARS))
                .run(() -> Script.clickSlot(MachineMenu.OUTPUT))
                .expect("output slot refuses a stack", () -> menu().getCarried().is(Items.IRON_BARS))
                .click(".title")
                .expect("panel click keeps the carried stack", () -> menu().getCarried().is(Items.IRON_BARS))
                .run(() -> Script.clickAt(2, 2, 0))
                .waitUntilServer("outside click drops it", p -> p.getInventory().countItem(Items.IRON_BARS) == 0
                        && p.containerMenu.getCarried().isEmpty(), Script.WAIT)
                .run(() -> Minecraft.getInstance().player.closeContainer())
                .server(p -> {
                    // Emptied first: dropped contents would be picked up by later checks.
                    Container machine = MachineScreen.contents(p.level(), at[0]);
                    if (machine != null) {
                        machine.clearContent();
                    }
                    p.level().setBlockAndUpdate(at[0], Blocks.AIR.defaultBlockState());
                    p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16)).forEach(Entity::discard);
                }));
    }

    private static MachineMenu menu() {
        return Script.menu(MachineMenu.class);
    }

    /** Sent as a packet: vanilla reads Shift from the window, which an unattended run cannot hold. */
    private static void quickMove(int slot) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(menu().containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
    }
}
