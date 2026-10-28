package com.azane.ixchel.sample.character;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.Script;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * {@code -Pcheck=character}: opens the panel on the player's inventory menu, equips a helmet with
 * real clicks (container 0, no open packet), then walks the tabs: attributes with a tooltip, effects
 * that blink as they end, statistics fetched from the server.
 */
final class CharacterCheck {
    private static final int HELMET = InventoryMenu.ARMOR_SLOT_START;
    private static final int FIRST_HOTBAR = InventoryMenu.USE_ROW_SLOT_START;
    private static final String[] stat = {""};

    private CharacterCheck() {
    }

    static void init() {
        Checks.register(CharacterClient.ID, s -> s
                .server(p -> p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_HELMET)))
                .run(() -> Minecraft.getInstance().setScreen(CharacterClient.screen(Minecraft.getInstance().player)))
                .waitUntil("panel open, every slot placed without errors", () -> isOpen()
                        && Script.document() != null && Script.document().diagnostics().errorCount() == 0
                        && Script.query(".slot").size() == screen().getMenu().slots.size(), Script.WAIT)
                .run(() -> {
                    LayoutBox b = Script.query(".doll").get(0).box();
                    s.report("doll sized by css (" + b.width() + "x" + b.height() + ")", b.width() == 50 && b.height() == 72);
                })
                .hover(".doll")
                .shot("character-equipment")
                // Fails if clicks on the laid-out slots do not reach the server's inventory menu.
                .run(() -> click(FIRST_HOTBAR))
                .run(() -> click(HELMET))
                .waitUntilServer("helmet equipped by two clicks", p -> p.getInventory().getArmor(3).is(Items.DIAMOND_HELMET)
                        && p.getInventory().getItem(0).isEmpty(), Script.WAIT)
                .run(() -> clickTab(1))
                .waitUntil("attributes tab: seven rows, armor slots hidden", () -> Script.query(".attribute").size() == 7
                        && slot(HELMET).x == -2000 && !isHidden(FIRST_HOTBAR), Script.WAIT)
                // Fails if slots on a hidden page are reported as too small (their box is 0x0).
                .expect("hidden page reports no errors", () -> Script.document().diagnostics().errorCount() == 0)
                .expect("diamond helmet's 3 armor shows", () -> value(".attribute", "Armor").equals("3"))
                .hover(".attribute")
                .shot("character-attributes")
                .server(p -> {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 150, 0));
                })
                .run(() -> clickTab(2))
                .waitUntil("effects tab: two rows, night vision ending", () -> Script.query(".effect").size() == 2
                        && Script.query(".effect.ending").size() == 1, Script.WAIT)
                // The ending row dims for half a second, then lights up again.
                .waitUntil("ending row dims", () -> Script.query(".effect.dim").size() == 1, Script.WAIT)
                .shot("character-effects")
                .waitUntil("and lights up again", () -> Script.query(".effect.dim").isEmpty(), Script.WAIT)
                .run(() -> stat[0] = value(".stat", "Time played"))
                .run(() -> clickTab(3))
                // Fails if the stats request is not sent: the client counter stays as it was.
                .waitUntil("stats tab: play time arrives from the server",
                        () -> !value(".stat", "Time played").equals(stat[0]), 60)
                .shot("character-stats")
                .run(() -> clickTab(0))
                .waitUntil("equipment tab shows the armor slots again", () -> !isHidden(HELMET), Script.WAIT));
    }

    private static boolean isOpen() {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> c
                && c.getMenu() instanceof InventoryMenu;
    }

    private static AbstractContainerScreen<?> screen() {
        return (AbstractContainerScreen<?>) Minecraft.getInstance().screen;
    }

    private static Slot slot(int index) {
        return screen().getMenu().slots.get(index);
    }

    private static boolean isHidden(int index) {
        return slot(index).x == -2000;
    }

    private static void click(int index) {
        Slot slot = slot(index);
        Script.clickAt(screen().getGuiLeft() + slot.x + 8, screen().getGuiTop() + slot.y + 8, 0);
    }

    private static void clickTab(int index) {
        LayoutBox b = Script.query(".tab").get(index).box();
        Script.clickAt(b.x() + b.width() / 2, b.y() + b.height() / 2, 0);
    }

    /** The {@code .value} of the {@code rows} row whose {@code .name} is {@code name}, or an empty string. */
    private static String value(String rows, String name) {
        List<Element> names = Script.query(rows + " .name");
        List<Element> values = Script.query(rows + " .value");
        for (int i = 0; i < names.size() && i < values.size(); i++) {
            if (names.get(i).textContent().equals(name)) {
                return values.get(i).textContent();
            }
        }
        return "";
    }
}
