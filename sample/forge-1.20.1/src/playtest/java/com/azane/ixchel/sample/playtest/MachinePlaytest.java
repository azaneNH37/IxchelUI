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
 * waits for the server to cut; then the redstone mode and the side configuration, each a click whose
 * intent the server applies and replicates back; then vanilla slot behavior on the laid-out slots
 * (real-cursor hover, a refused slot, panel vs outside clicks). With {@code -Pslotcompat} this runs
 * next to JEI or EMI and Mouse Tweaks.
 */
public final class MachinePlaytest {
    private static final BlockPos[] at = {BlockPos.ZERO};
    private static final MachineMenu[] previous = {null};
    /** Inventory slot of the iron bars carried around in the slot assertions. */
    private static final int BARS = 3;

    private MachinePlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("machine", s -> s
                .server(p -> {
                    p.getInventory().setItem(0, new ItemStack(Items.COAL, 4));
                    p.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));
                    p.getInventory().setItem(2, new ItemStack(Items.STONE, 3));
                    p.getInventory().setItem(BARS, new ItemStack(Items.IRON_BARS, 2));
                    at[0] = MachineScreen.placeAndOpen(p);
                })
                .waitUntil("machine open, every slot placed without errors", () -> menu() != null
                        && Playtest.document().diagnostics().errorCount() == 0
                        && Playtest.query("mc-slot").size() == MachineMenu.SLOT_COUNT, Playtest.WAIT)
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
                .run(() -> Playtest.hoverAt(0, 0))
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
                .expect("empty bucket left in the water slot", () -> menu().getSlot(MachineMenu.WATER_SLOT).getItem().is(Items.BUCKET))
                .shot("machine-ready")
                .click(".recipe")
                .waitUntil("first recipe selected, working", () -> Playtest.query(".recipe.selected").size() == 1
                        && Playtest.text(".status").equals("Working"), Playtest.WAIT)
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
                        && menu() != previous[0] && Playtest.text(".status").equals("Working"), Playtest.WAIT)
                .click(".redstone")
                .waitUntil("needs a signal: stops", () -> Playtest.text(".redstone span").equals("Redstone: Needs signal")
                        && Playtest.text(".status").equals("Redstone off"), Playtest.WAIT)
                // Each click sets the mode after the replicated one, so a click before the snapshot
                // arrives repeats the last mode instead of skipping one.
                .click(".redstone")
                .waitUntil("needs no signal: works", () -> Playtest.text(".redstone span").equals("Redstone: Needs no signal")
                        && Playtest.text(".status").equals("Working"), Playtest.WAIT)
                .click(".redstone")
                .waitUntil("back to ignored: works again", () -> Playtest.text(".redstone span").equals("Redstone: Ignored")
                        && Playtest.text(".status").equals("Working"), Playtest.WAIT)
                .click(".sides-toggle")
                .waitUntil("side panel slides out to its full width", () -> Playtest.box(".sides").width() == 80, Playtest.WAIT)
                .click(".side")
                .waitUntil("down side cycles to input", () -> Playtest.text(".mode").equals("Input"), Playtest.WAIT)
                .shot("machine-sides")
                // Carrying the bars: the output slot refuses them, a click on the panel keeps them, a click
                // outside drops them.
                .run(() -> Playtest.clickSlot(MachineMenu.HOTBAR_START + BARS))
                .run(() -> Playtest.clickSlot(MachineMenu.OUTPUT))
                .expect("output slot refuses a stack", () -> menu().getCarried().is(Items.IRON_BARS))
                .click(".title")
                .expect("panel click keeps the carried stack", () -> menu().getCarried().is(Items.IRON_BARS))
                .run(() -> Playtest.clickAt(2, 2, 0))
                .waitUntilServer("outside click drops it", p -> p.getInventory().countItem(Items.IRON_BARS) == 0
                        && p.containerMenu.getCarried().isEmpty(), Playtest.WAIT)
                .run(() -> Minecraft.getInstance().player.closeContainer())
                .server(p -> {
                    // Emptied first: dropped contents would be picked up by later playtests.
                    Container machine = MachineScreen.contents(p.level(), at[0]);
                    if (machine != null) {
                        machine.clearContent();
                    }
                    p.level().setBlockAndUpdate(at[0], Blocks.AIR.defaultBlockState());
                    p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16)).forEach(Entity::discard);
                }));
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
