package com.azane.ixchel.sample.check.scenario;

import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.Script;
import com.azane.ixchel.sample.waypoints.Waypoints;
import com.azane.ixchel.sample.waypoints.WaypointsScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * {@code -Pcheck=waypoints}: opens the screen, adds waypoints by clicking the button, moves the
 * selection with the keys, removes with Delete, scrolls the list past six rows.
 */
public final class WaypointsCheck {
    private WaypointsCheck() {
    }

    public static void register() {
        Checks.register("waypoints", s -> {
            s.run(() -> {
                Waypoints.clear();
                WaypointsScreen.open();
            });
            s.waitUntil("empty list shows its message", () -> Script.query(".empty").size() == 1, Script.WAIT);
            s.shot("waypoints-empty");
            // The button's title shows as a vanilla tooltip (screenshot only: nothing to query).
            s.hover(".button");
            s.shot("waypoints-title");
            for (int i = 0; i < 8; i++) {
                s.click(".button");
            }
            s.waitUntil("eight rows, the last one selected", () -> Script.query(".row").size() == 8
                    && Script.query(".row.selected").size() == 1 && selectedName().equals("Waypoint 8"), Script.WAIT);
            s.expect("list scrolls: six rows fit", () -> Script.query(".list").get(0).maxScrollTop() > 0);
            // The keys are template filters on the screen's top-level element: document-level, no focus needed.
            s.key(GLFW.GLFW_KEY_UP);
            s.key(GLFW.GLFW_KEY_UP);
            s.waitUntil("Up twice selects waypoint 6", () -> selectedName().equals("Waypoint 6"), Script.WAIT);
            s.key(GLFW.GLFW_KEY_DELETE);
            s.waitUntil("Delete removes it and selects the next", () -> Script.query(".row").size() == 7
                    && selectedName().equals("Waypoint 7"), Script.WAIT);
            s.click(".row");
            s.waitUntil("click selects the first row", () -> selectedName().equals("Waypoint 1"), Script.WAIT);
            s.shot("waypoints-list");
            s.run(() -> Minecraft.getInstance().setScreen(null));
        });
    }

    private static String selectedName() {
        return Script.text(".row.selected .name");
    }
}
