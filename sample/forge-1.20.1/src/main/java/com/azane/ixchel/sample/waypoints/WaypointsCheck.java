package com.azane.ixchel.sample.waypoints;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.sample.check.Script;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Run with {@code -Pbench=waypoints}: opens the screen, adds waypoints by clicking the button, moves
 * the selection with the keys, removes with Delete, scrolls the list past six rows.
 */
final class WaypointsCheck {
    private WaypointsCheck() {
    }

    static void init() {
        Script s = Script.forBench(WaypointsClient.ID);
        if (s == null) {
            return;
        }
        s.step(40, () -> {
            WaypointsView.SAVED.clear();
            Minecraft.getInstance().setScreen(WaypointsClient.screen());
        });
        s.step(10, () -> {
            s.expect("empty list shows its message", () -> query(".empty").size() == 1);
            Script.shot("waypoints-empty");
            for (int i = 0; i < 8; i++) {
                click(".button");
            }
        });
        s.step(10, () -> {
            s.expect("eight rows, the last one selected", () -> query(".row").size() == 8
                    && query(".row.selected").size() == 1 && "Waypoint 8".equals(query(".row.selected").get(0).textContent().substring(0, 10)));
            s.expect("list scrolls: six rows fit", () -> query(".list").get(0).maxScrollTop() > 0);
            key(GLFW.GLFW_KEY_UP);
            key(GLFW.GLFW_KEY_UP);
        });
        s.step(5, () -> {
            s.expect("Up twice selects waypoint 6", () -> selectedName().equals("Waypoint 6"));
            key(GLFW.GLFW_KEY_DELETE);
        });
        s.step(5, () -> {
            s.expect("Delete removes it and selects the next", () -> query(".row").size() == 7
                    && selectedName().equals("Waypoint 7"));
            Element first = query(".row").get(0);
            Script.clickAt(first.box().x() + 4, first.box().y() + 4, 0);
        });
        s.step(5, () -> {
            s.expect("click selects the first row", () -> selectedName().equals("Waypoint 1"));
            Script.shot("waypoints-list");
            Minecraft.getInstance().setScreen(null);
        });
        s.start();
    }

    private static List<Element> query(String selector) {
        return ((UiScreen) Minecraft.getInstance().screen).document().querySelectorAll(selector);
    }

    private static String selectedName() {
        List<Element> sel = query(".row.selected .name");
        return sel.isEmpty() ? "" : sel.get(0).textContent();
    }

    private static void click(String selector) {
        Element e = query(selector).get(0);
        Script.clickAt(e.box().x() + 2, e.box().y() + 2, 0);
    }

    private static void key(int key) {
        Minecraft.getInstance().screen.keyPressed(key, 0, 0);
    }
}
