package com.azane.ixchel.sample.check.scenario;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.mc.check.Checks;
import com.azane.ixchel.mc.check.Script;
import com.azane.ixchel.sample.character.CharacterScreen;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@code -Pcheck=character}: opens the panel on the player's inventory menu, equips a helmet with
 * real clicks (container 0, no open packet), then walks the tabs: attributes with a tooltip, effects
 * that blink as they end, statistics fetched from the server.
 */
public final class CharacterCheck {
    private static final int HELMET = InventoryMenu.ARMOR_SLOT_START;
    private static final int FIRST_HOTBAR = InventoryMenu.USE_ROW_SLOT_START;
    private static final String[] stat = {""};

    private CharacterCheck() {
    }

    public static void register(Checks checks) {
        checks.register("character", s -> s
                .server(p -> p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_HELMET)))
                .run(CharacterScreen::open)
                .waitUntil("panel open, every slot placed without errors", () -> isOpen()
                        && Script.document().diagnostics().errorCount() == 0
                        && Script.query(".slot").size() == Script.containerScreen().getMenu().slots.size(), Script.WAIT)
                .run(() -> {
                    LayoutBox b = Script.box(".doll");
                    s.report("doll sized by css (" + b.width() + "x" + b.height() + ")", b.width() == 50 && b.height() == 72);
                })
                .hover(".doll")
                .shot("character-equipment")
                // Fails if clicks on the laid-out slots do not reach the server's inventory menu.
                .run(() -> Script.clickSlot(FIRST_HOTBAR))
                .run(() -> Script.clickSlot(HELMET))
                .waitUntilServer("helmet equipped by two clicks", p -> p.getInventory().getArmor(3).is(Items.DIAMOND_HELMET)
                        && p.getInventory().getItem(0).isEmpty(), Script.WAIT)
                .clickNth(".tab", 1)
                .waitUntil("attributes tab: seven rows, armor slots hidden", () -> Script.query(".attribute").size() == 7
                        && isHidden(HELMET) && !isHidden(FIRST_HOTBAR), Script.WAIT)
                // Fails if slots on a hidden page are reported as too small (their box is 0x0).
                .expect("hidden page reports no errors", () -> Script.document().diagnostics().errorCount() == 0)
                .expect("diamond helmet's 3 armor shows", () -> value(".attribute", "Armor").equals("3"))
                .hover(".attribute")
                // Fails if the row's <ix-tooltip> does not show in the top layer under the pointer.
                .waitUntil("the hovered attribute shows its tooltip", () -> {
                    Element tip = Script.document().topLayer().tooltip();
                    return tip != null && tip.parent() == Script.query(".attribute").get(0);
                }, Script.WAIT)
                .shot("character-attributes")
                .server(p -> {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 150, 0));
                })
                .clickNth(".tab", 2)
                .waitUntil("effects tab: two rows, night vision ending", () -> Script.query(".effect").size() == 2
                        && Script.query(".effect.ending").size() == 1, Script.WAIT)
                // The ending row dims for half a second, then lights up again.
                .waitUntil("ending row dims", () -> Script.query(".effect.dim").size() == 1, Script.WAIT)
                .shot("character-effects")
                .waitUntil("and lights up again", () -> Script.query(".effect.dim").isEmpty(), Script.WAIT)
                .run(() -> stat[0] = value(".stat", "Time played"))
                .clickNth(".tab", 3)
                // Fails if the stats request is not sent: the client counter stays as it was.
                .waitUntil("stats tab: play time arrives from the server",
                        () -> !value(".stat", "Time played").equals(stat[0]), 60)
                .shot("character-stats")
                .clickNth(".tab", 0)
                .waitUntil("equipment tab shows the armor slots again", () -> !isHidden(HELMET), Script.WAIT));
    }

    private static boolean isOpen() {
        return Script.menu(InventoryMenu.class) != null;
    }

    /** Hidden slots are parked at {@code x = -2000} (see the container slot integration notes). */
    private static boolean isHidden(int index) {
        return Script.slot(index).x == -2000;
    }

    /** The {@code .value} of the {@code rows} row whose {@code .name} is {@code name}, or an empty string. */
    private static String value(String rows, String name) {
        return Script.valueOf(Script.query(rows + " .name"), Script.query(rows + " .value"), name);
    }
}
