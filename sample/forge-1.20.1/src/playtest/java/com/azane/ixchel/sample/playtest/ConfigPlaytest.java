package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.event.Keys;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.config.ConfigEntries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * {@code -Pplaytest=config}: opens the panel the way the mod list's config button does, over the title
 * screen, and edits one value of every kind: a toggle, a bounded number (letters dropped, out of
 * range rejected, the slider following the field), an enum through the select's list in the top
 * layer, a color, and an item list through the list dialog. Then undo, save (the stored config
 * changes), and Escape with unsaved changes (the confirmation dialog; discard returns to the title
 * screen). Every value is put back to its default before and after.
 */
public final class ConfigPlaytest {
    private static final List<String> DEFAULT_BLOCKED = List.of("minecraft:rotten_flesh", "minecraft:poisonous_potato");

    private ConfigPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("config", s -> {
            s.run(() -> {
                resetConfig();
                Minecraft mc = Minecraft.getInstance();
                Screen title = new TitleScreen();
                mc.setScreen(title);
                mc.setScreen(ModList.get().getModContainerById(IxchelSampleMod.MOD_ID).orElseThrow()
                        .getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class).orElseThrow()
                        .screenFunction().apply(mc, title));
            });
            s.waitUntil("the mod list factory opens the panel, General first", () -> Playtest.isOpen()
                    && Playtest.texts(".category").equals(List.of("General", "Hud", "Compass", "Loot"))
                    && Playtest.text(".category.active").equals("General"), Playtest.WAIT);
            s.expect("nothing modified: Save and Undo disabled", () -> disabled(".save") && disabled(".undo"));
            s.shot("config-general");
            // Fails if a row's <ix-tooltip> does not show over its label.
            s.hover(".entry .name");
            s.waitUntil("a hovered setting shows its tooltip", () -> {
                Element tip = Playtest.document().topLayer().tooltip();
                return tip != null && tip.parent() == Playtest.query(".entry").get(0);
            }, Playtest.WAIT);
            s.shot("config-tooltip");

            s.clickText(".category", "Compass");
            s.waitUntil("a category click shows its rows", () -> Playtest.texts(".name").contains("Compass range")
                    && Playtest.text(".category.active").equals("Compass"), Playtest.WAIT);
            s.click(".check");
            s.waitUntil("a toggle marks its row modified and enables Save", () -> Playtest.query(".entry.modified").size() == 1
                    && !disabled(".save") && !disabled(".undo"), Playtest.WAIT);

            // Number: the field, then the slider next to it.
            search(s, "compass range", "Compass range");
            s.click(".num");
            s.key(GLFW.GLFW_KEY_A, Keys.MOD_CTRL);
            s.type("x9999");
            s.waitUntil("a number field drops letters; past the range the row is invalid and Save off",
                    () -> value(".num").equals("9999") && Playtest.query(".entry.invalid").size() == 1
                            && !Playtest.text(".error").isEmpty() && disabled(".save"), Playtest.WAIT);
            s.shot("config-invalid");
            s.key(GLFW.GLFW_KEY_A, Keys.MOD_CTRL);
            s.type("100");
            s.waitUntil("a valid number clears the error and moves the slider", () -> value(".num").equals("100")
                    && Playtest.query(".entry.invalid").isEmpty() && !disabled(".save")
                    && Playtest.query(".slider").get(0).control().number() == 100, Playtest.WAIT);

            // Enum: the select's list opens in the top layer, outside the clipped entry list.
            search(s, "theme", "Theme");
            s.click(".choice");
            s.waitUntil("the select opens its list", () -> Playtest.query("select:open option").size() == 4, Playtest.WAIT);
            s.shot("config-select");
            s.clickText("select:open option", "HIGH_CONTRAST");
            s.waitUntil("picking an option closes the list and sets the value", () -> Playtest.query("select:open").isEmpty()
                    && value(".choice").equals("HIGH_CONTRAST"), Playtest.WAIT);

            // Color.
            search(s, "accent", "Accent color");
            s.click(".hex");
            s.key(GLFW.GLFW_KEY_A, Keys.MOD_CTRL);
            s.type("#zz");
            s.waitUntil("a malformed color is invalid", () -> Playtest.query(".entry.invalid").size() == 1, Playtest.WAIT);
            s.key(GLFW.GLFW_KEY_A, Keys.MOD_CTRL);
            s.type("#ff0000");
            s.waitUntil("a well-formed color is valid", () -> Playtest.query(".entry.invalid").isEmpty()
                    && Playtest.query(".entry.modified").size() == 1, Playtest.WAIT);

            // List: the dialog edits a copy; Done writes it back.
            search(s, "blocked", "Blocked items");
            s.click(".edit");
            s.waitUntil("the list dialog opens with both defaults", () -> Playtest.query("dialog:open").size() == 1
                    && Playtest.query(".item").size() == 2, Playtest.WAIT);
            s.click(".remove");
            s.click(".preset");
            s.waitUntil("a select inside the dialog opens above it", () -> Playtest.query("select:open option").size() == 5, Playtest.WAIT);
            s.shot("config-list");
            s.clickText("select:open option", "Spider Eye");
            s.waitUntil("a preset adds a line", () -> Playtest.query(".item").size() == 2, Playtest.WAIT);
            s.click(".list-done");
            s.waitUntil("Done closes the dialog and writes the list", () -> Playtest.query("dialog:open").isEmpty()
                    && Playtest.text(".summary").equals("minecraft:poisonous_potato, minecraft:spider_eye"), Playtest.WAIT);
            s.click(".undo");
            s.waitUntil("Undo steps back the last change", () -> Playtest.text(".summary")
                    .equals(String.join(", ", DEFAULT_BLOCKED)), Playtest.WAIT);

            s.click(".save");
            s.waitUntil("Save stores the drafts and clears the marks", () -> Playtest.text(".status").equals("Saved")
                    && Playtest.query(".entry.modified").isEmpty() && disabled(".save"), Playtest.WAIT);
            s.expect("the stored config has the saved values", () -> stored("compass.showCompass").equals(false)
                    && stored("compass.compassRange").equals(100) && stored("general.theme").equals("HIGH_CONTRAST")
                    && stored("hud.accentColor").equals(0xFF0000) && stored("loot.blockedItems").equals(DEFAULT_BLOCKED));

            // An unsaved change, then Escape: confirm first.
            search(s, "accent", "Accent color");
            s.click(".reset");
            s.waitUntil("Reset restores the default as a draft", () -> Playtest.query(".entry.modified").size() == 1, Playtest.WAIT);
            s.key(GLFW.GLFW_KEY_ESCAPE);
            s.waitUntil("Escape with unsaved changes asks first", () -> Playtest.query("dialog.confirm:open").size() == 1, Playtest.WAIT);
            s.shot("config-confirm");
            s.click(".keep");
            s.waitUntil("Keep editing closes only the dialog", () -> Playtest.isOpen() && Playtest.query("dialog:open").isEmpty(), Playtest.WAIT);
            s.key(GLFW.GLFW_KEY_ESCAPE);
            s.waitUntil("the confirmation again", () -> Playtest.query("dialog.confirm:open").size() == 1, Playtest.WAIT);
            s.click(".discard");
            s.waitUntil("Discard returns to the screen the panel was opened from",
                    () -> Minecraft.getInstance().screen instanceof TitleScreen, Playtest.WAIT);
            s.expect("discarded drafts do not reach the config", () -> stored("hud.accentColor").equals(0xFF0000));
            s.run(() -> {
                resetConfig();
                Minecraft.getInstance().setScreen(null);
            });
        });
    }

    /** Replaces the search text by typing and waits for the single row {@code label}. */
    private static void search(Playtest s, String text, String label) {
        s.click(".search");
        s.key(GLFW.GLFW_KEY_A, Keys.MOD_CTRL);
        s.type(text);
        s.waitUntil("search \"" + text + "\" leaves one row", () -> Playtest.texts(".name").equals(List.of(label)), Playtest.WAIT);
    }

    private static String value(String selector) {
        return Playtest.query(selector).get(0).control().value();
    }

    private static boolean disabled(String selector) {
        return Playtest.query(selector).get(0).hasState(Element.DISABLED);
    }

    private static Object stored(String path) {
        return ConfigEntries.load().categories().stream().flatMap(c -> c.entries().stream())
                .filter(e -> e.path().equals(path)).findFirst().orElseThrow().value();
    }

    private static void resetConfig() {
        ConfigEntries entries = ConfigEntries.load();
        entries.categories().forEach(c -> c.entries().forEach(e -> e.set(e.defaultValue())));
        entries.save();
    }
}
