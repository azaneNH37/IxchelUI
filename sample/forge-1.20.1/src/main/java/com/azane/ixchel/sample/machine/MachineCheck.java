package com.azane.ixchel.sample.machine;

import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.Script;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code -Pcheck=machine}: places a powered stonecutter, fills it by quick moves, picks a recipe and
 * waits for the server to cut; then the redstone mode and the side configuration, each only through
 * {@code clickMenuButton} and menu data; then vanilla slot behavior on the laid-out slots (real-cursor
 * hover, a refused slot, panel vs outside clicks). With {@code -Pslotcompat} this runs next to JEI or
 * EMI and Mouse Tweaks.
 */
final class MachineCheck {
    private static final BlockPos[] at = {BlockPos.ZERO};
    /** Inventory slot of the iron bars carried around in the slot checks. */
    private static final int BARS = 3;

    private MachineCheck() {
    }

    static void init() {
        Checks.register(MachineClient.ID, s -> s
                .server(p -> {
                    p.getInventory().setItem(0, new ItemStack(Items.COAL, 4));
                    p.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));
                    p.getInventory().setItem(2, new ItemStack(Items.STONE, 3));
                    p.getInventory().setItem(BARS, new ItemStack(Items.IRON_BARS, 2));
                    at[0] = p.blockPosition().east(2);
                    MachineClient.placeAndOpen(p);
                })
                .waitUntil("machine open, every slot placed without errors", () -> menu() != null
                        && Script.document().diagnostics().errorCount() == 0
                        && Script.query(".slot").size() == MachineMenu.SLOT_COUNT, Script.WAIT)
                .expect("idle without input", () -> text(".status").equals("Idle: no input"))
                .run(() -> {
                    // The GUI area other mods avoid is the panel, not the whole screen.
                    AbstractContainerScreen<?> c = screen();
                    s.report("gui area is the panel: " + c.getGuiLeft() + "," + c.getGuiTop() + " " + c.getXSize() + "x"
                            + c.getYSize(), c.getXSize() > 0 && c.getXSize() < c.width && c.getYSize() < c.height);
                })
                // Hover through the real cursor, as vanilla, JEI and EMI read it during render.
                .run(() -> hover(MachineMenu.HOTBAR_START + BARS))
                .waitUntil("bars slot is hovered", () -> screen().getSlotUnderMouse() == slot(MachineMenu.HOTBAR_START + BARS),
                        Script.WAIT)
                .shot("machine-hover")
                .run(() -> Script.hoverAt(0, 0))
                // Fails if quickMoveStack sends a stack to the wrong machine slot.
                .run(() -> {
                    quickMove(MachineMenu.HOTBAR_START);
                    quickMove(MachineMenu.HOTBAR_START + 1);
                    quickMove(MachineMenu.HOTBAR_START + 2);
                })
                .waitUntil("fuel burns, the bucket fills the tank, recipes for stone listed", () ->
                        text(".status").equals("Pick a recipe") && !text(".energy", "title").startsWith("0 ")
                                && text(".tank", "title").equals("1000 / 4000 mB")
                                && Script.query(".recipe").size() >= 4, Script.WAIT)
                .expect("empty bucket left in the water slot", () -> menu().getSlot(MachineMenu.WATER_SLOT).getItem().is(Items.BUCKET))
                .shot("machine-ready")
                .click(".recipe")
                .waitUntil("first recipe selected, working", () -> Script.query(".recipe.selected").size() == 1
                        && text(".status").equals("Working"), Script.WAIT)
                .hover(".tank")
                .shot("machine-working")
                .waitUntilServer("the server cuts a stone", p -> p.level().getBlockEntity(at[0]) instanceof MachineBlockEntity be
                        && !be.items.getItem(MachineMenu.OUTPUT).isEmpty()
                        && be.items.getItem(MachineMenu.INPUT).getCount() == 2, 100)
                .click(".redstone")
                .waitUntil("needs a signal: stops", () -> text(".redstone span").equals("Redstone: Needs signal")
                        && text(".status").equals("Redstone off"), Script.WAIT)
                .click(".redstone")
                .click(".redstone")
                .waitUntil("back to ignored: works again", () -> text(".redstone span").equals("Redstone: Ignored")
                        && text(".status").equals("Working"), Script.WAIT)
                .click(".sides-toggle")
                .waitUntil("side panel slides out to its full width", () -> box(".sides").width() == 80, Script.WAIT)
                .click(".side")
                .waitUntil("down side cycles to input", () -> text(".mode").equals("Input"), Script.WAIT)
                .shot("machine-sides")
                // Carrying the bars: the output slot refuses them, a click on the panel keeps them, a click
                // outside drops them.
                .run(() -> click(MachineMenu.HOTBAR_START + BARS))
                .run(() -> click(MachineMenu.OUTPUT))
                .expect("output slot refuses a stack", () -> screen().getMenu().getCarried().is(Items.IRON_BARS))
                .click(".title")
                .expect("panel click keeps the carried stack", () -> screen().getMenu().getCarried().is(Items.IRON_BARS))
                .run(() -> Script.clickAt(2, 2, 0))
                .waitUntilServer("outside click drops it", p -> p.getInventory().countItem(Items.IRON_BARS) == 0
                        && p.containerMenu.getCarried().isEmpty(), Script.WAIT)
                .run(() -> Minecraft.getInstance().player.closeContainer())
                .server(p -> {
                    // Emptied first: dropped contents would be picked up by later checks.
                    if (p.level().getBlockEntity(at[0]) instanceof MachineBlockEntity be) {
                        be.items.clearContent();
                    }
                    p.level().setBlockAndUpdate(at[0], Blocks.AIR.defaultBlockState());
                    p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16)).forEach(Entity::discard);
                }));
    }

    private static MachineMenu menu() {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> c
                && c.getMenu() instanceof MachineMenu m ? m : null;
    }

    private static AbstractContainerScreen<?> screen() {
        return (AbstractContainerScreen<?>) Minecraft.getInstance().screen;
    }

    private static Slot slot(int index) {
        return screen().getMenu().slots.get(index);
    }

    private static void click(int index) {
        Slot slot = slot(index);
        Script.clickAt(screen().getGuiLeft() + slot.x + 8, screen().getGuiTop() + slot.y + 8, 0);
    }

    private static void hover(int index) {
        Slot slot = slot(index);
        Script.hoverAt(screen().getGuiLeft() + slot.x + 8, screen().getGuiTop() + slot.y + 8);
    }

    /** Sent as a packet: vanilla reads Shift from the window, which an unattended run cannot hold. */
    private static void quickMove(int slot) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(menu().containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
    }

    private static String text(String selector) {
        return Script.query(selector).get(0).textContent();
    }

    private static String text(String selector, String attribute) {
        String v = Script.query(selector).get(0).attribute(attribute);
        return v == null ? "" : v;
    }

    private static LayoutBox box(String selector) {
        return Script.query(selector).get(0).box();
    }
}
