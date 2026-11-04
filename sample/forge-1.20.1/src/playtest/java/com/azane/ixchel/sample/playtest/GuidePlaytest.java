package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.mc.FrameTimer;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.sample.guide.Guide;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * {@code -Pplaytest=guide}: the contents list the entry views; a link inside a paragraph (hit on its
 * line) opens an entry, Back and Backspace walk the history back to the spread left; the page arrows
 * and Left/Right turn spreads, sliding the pages in view; a crafting grid cycles a cell through the
 * items it accepts; the long entry holds the frame budget standing still and turning every tick.
 */
public final class GuidePlaytest {
    /** About twice the worst p95 measured on the development machine (1.3 ms still, 1.6 ms turning). */
    private static final double STILL_BUDGET_US = 3000;
    private static final double TURNING_BUDGET_US = 3500;
    private static final int TIMED_TICKS = 60;

    private static Object firstCell;

    private GuidePlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("guide", s -> {
            s.run(Guide::open);
            s.waitUntil("the contents list every entry view, sorted", () -> Playtest.isOpen()
                    && Playtest.texts(".toc a").equals(List.of("Circuits", "Pistons", "Storage", "Typesetting"))
                    && Playtest.text(".folio").equals("1 / 1"), Playtest.WAIT);
            s.expect("the pages load without errors", GuidePlaytest::clean);
            s.expect("nothing to go back to: Back disabled", () -> disabled(".back"));
            s.shot("guide-contents");

            // The intro paragraph's "link": an inline <a> in the middle of a line.
            s.click(".text a");
            s.waitUntil("a link inside a paragraph opens its entry", () -> title().equals("Circuits")
                    && Playtest.text(".folio").equals("1 / 2") && !disabled(".back"), Playtest.WAIT);
            s.expect("the entry loads without errors", GuidePlaytest::clean);
            s.shot("guide-circuits");

            s.click(".next");
            s.waitUntil("the arrow turns to the last spread: its pages slide into view, no next arrow",
                    () -> Playtest.text(".folio").equals("2 / 2") && Playtest.query(".next").isEmpty()
                            && inView(2) && !inView(0), Playtest.WAIT);
            s.key(GLFW.GLFW_KEY_LEFT);
            s.waitUntil("Left turns back", () -> Playtest.text(".folio").equals("1 / 2") && inView(0), Playtest.WAIT);
            s.key(GLFW.GLFW_KEY_RIGHT);
            s.waitUntil("Right turns forward", () -> Playtest.text(".folio").equals("2 / 2"), Playtest.WAIT);

            s.clickText(".text a", "storage");
            s.waitUntil("a link on the second spread opens its entry", () -> title().equals("Storage"), Playtest.WAIT);
            s.run(() -> firstCell = Playtest.query(".grid mc-item").get(0).payload());
            s.waitUntil("a recipe cell cycles through the planks it accepts",
                    () -> Playtest.query(".grid mc-item").get(0).payload() != firstCell, 60);
            s.shot("guide-storage");

            s.click(".back");
            s.waitUntil("Back returns to the spread the link was on", () -> title().equals("Circuits")
                    && Playtest.text(".folio").equals("2 / 2"), Playtest.WAIT);
            s.key(GLFW.GLFW_KEY_BACKSPACE);
            s.waitUntil("Backspace returns to the contents, the history empty", () -> Playtest.query(".toc").size() == 4
                    && disabled(".back"), Playtest.WAIT);

            s.clickText(".toc a", "Typesetting");
            s.waitUntil("the long entry opens", () -> title().equals("Typesetting")
                    && Playtest.text(".folio").equals("1 / 3"), Playtest.WAIT);
            s.expect("the long entry loads without errors", GuidePlaytest::clean);
            s.shot("guide-typesetting");
            s.key(GLFW.GLFW_KEY_RIGHT);
            s.waitUntil("the Chinese spread", () -> Playtest.text(".folio").equals("2 / 3"), Playtest.WAIT);
            s.shot("guide-typesetting-cjk");

            s.run(() -> FrameTimer.screen(4096));
            s.pause(100);
            s.run(() -> FrameTimer.expect(s, "long entry standing still", STILL_BUDGET_US));
            s.run(() -> FrameTimer.screen(4096));
            for (int i = 0; i < TIMED_TICKS; i++) {
                s.key(i % 4 < 2 ? GLFW.GLFW_KEY_RIGHT : GLFW.GLFW_KEY_LEFT);
            }
            s.run(() -> FrameTimer.expect(s, "long entry turning every tick", TURNING_BUDGET_US));
            s.run(() -> Minecraft.getInstance().setScreen(null));
        });
    }

    private static String title() {
        return Playtest.text(".heading");
    }

    private static boolean disabled(String selector) {
        return Playtest.query(selector).get(0).hasState(Element.DISABLED);
    }

    private static boolean clean() {
        return Playtest.document().diagnostics().entries().isEmpty();
    }

    /** Whether page {@code index} is drawn inside the book's window. */
    private static boolean inView(int index) {
        float[] p = EventDispatcher.paintedCenter(Playtest.query(".page").get(index));
        LayoutBox w = Playtest.box(".window");
        return p[0] >= w.x() && p[0] < w.x() + w.width() && p[1] >= w.y() && p[1] < w.y() + w.height();
    }
}
