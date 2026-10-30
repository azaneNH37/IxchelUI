package com.azane.ixchel.sample.check.scenario;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.FrameTimer;
import com.azane.ixchel.sample.check.Script;
import com.azane.ixchel.sample.guide.Guide;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * {@code -Pcheck=guide}: the contents list the entry templates; a link inside a paragraph (hit on its
 * line) opens an entry, Back and Backspace walk the history back to the spread left; the page arrows
 * and Left/Right turn spreads, sliding the pages in view; a crafting grid cycles a cell through the
 * items it accepts; the long entry holds the frame budget standing still and turning every tick.
 */
public final class GuideCheck {
    /** About twice the worst p95 measured on the development machine (1.3 ms still, 1.6 ms turning). */
    private static final double STILL_BUDGET_US = 3000;
    private static final double TURNING_BUDGET_US = 3500;
    private static final int TIMED_TICKS = 60;

    private static Object firstCell;

    private GuideCheck() {
    }

    public static void register() {
        Checks.register("guide", s -> {
            s.run(Guide::open);
            s.waitUntil("the contents list every entry template, sorted", () -> Script.isOpen()
                    && Script.texts(".toc a").equals(List.of("Circuits", "Pistons", "Storage", "Typesetting"))
                    && Script.text(".folio").equals("1 / 1"), Script.WAIT);
            s.expect("the pages load without errors", GuideCheck::clean);
            s.expect("nothing to go back to: Back disabled", () -> disabled(".back"));
            s.shot("guide-contents");

            // The intro paragraph's "link": an inline <a> in the middle of a line.
            s.click(".text a");
            s.waitUntil("a link inside a paragraph opens its entry", () -> title().equals("Circuits")
                    && Script.text(".folio").equals("1 / 2") && !disabled(".back"), Script.WAIT);
            s.expect("the entry loads without errors", GuideCheck::clean);
            s.shot("guide-circuits");

            s.click(".next");
            s.waitUntil("the arrow turns to the last spread: its pages slide into view, no next arrow",
                    () -> Script.text(".folio").equals("2 / 2") && Script.query(".next").isEmpty()
                            && inView(2) && !inView(0), Script.WAIT);
            s.key(GLFW.GLFW_KEY_LEFT);
            s.waitUntil("Left turns back", () -> Script.text(".folio").equals("1 / 2") && inView(0), Script.WAIT);
            s.key(GLFW.GLFW_KEY_RIGHT);
            s.waitUntil("Right turns forward", () -> Script.text(".folio").equals("2 / 2"), Script.WAIT);

            s.clickText(".text a", "storage");
            s.waitUntil("a link on the second spread opens its entry", () -> title().equals("Storage"), Script.WAIT);
            s.run(() -> firstCell = Script.query(".grid mc-item").get(0).payload());
            s.waitUntil("a recipe cell cycles through the planks it accepts",
                    () -> Script.query(".grid mc-item").get(0).payload() != firstCell, 60);
            s.shot("guide-storage");

            s.click(".back");
            s.waitUntil("Back returns to the spread the link was on", () -> title().equals("Circuits")
                    && Script.text(".folio").equals("2 / 2"), Script.WAIT);
            s.key(GLFW.GLFW_KEY_BACKSPACE);
            s.waitUntil("Backspace returns to the contents, the history empty", () -> Script.query(".toc").size() == 4
                    && disabled(".back"), Script.WAIT);

            s.clickText(".toc a", "Typesetting");
            s.waitUntil("the long entry opens", () -> title().equals("Typesetting")
                    && Script.text(".folio").equals("1 / 3"), Script.WAIT);
            s.expect("the long entry loads without errors", GuideCheck::clean);
            s.shot("guide-typesetting");
            s.key(GLFW.GLFW_KEY_RIGHT);
            s.waitUntil("the Chinese spread", () -> Script.text(".folio").equals("2 / 3"), Script.WAIT);
            s.shot("guide-typesetting-cjk");

            s.run(() -> FrameTimer.screen(4096));
            s.pause(100);
            s.run(() -> FrameTimer.report(s, "long entry standing still", STILL_BUDGET_US));
            s.run(() -> FrameTimer.screen(4096));
            for (int i = 0; i < TIMED_TICKS; i++) {
                s.key(i % 4 < 2 ? GLFW.GLFW_KEY_RIGHT : GLFW.GLFW_KEY_LEFT);
            }
            s.run(() -> FrameTimer.report(s, "long entry turning every tick", TURNING_BUDGET_US));
            s.run(() -> Minecraft.getInstance().setScreen(null));
        });
    }

    private static String title() {
        return Script.text(".heading");
    }

    private static boolean disabled(String selector) {
        return Script.query(selector).get(0).hasState(Element.DISABLED);
    }

    private static boolean clean() {
        return Script.document().diagnostics().entries().isEmpty();
    }

    /** Whether page {@code index} is drawn inside the book's window. */
    private static boolean inView(int index) {
        float[] p = Script.paintedCenter(Script.query(".page").get(index));
        LayoutBox w = Script.box(".window");
        return p[0] >= w.x() && p[0] < w.x() + w.width() && p[1] >= w.y() && p[1] < w.y() + w.height();
    }
}
