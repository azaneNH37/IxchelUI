package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.sample.waypoints.Waypoints;
import com.azane.ixchel.sample.waypoints.WaypointsScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * {@code -Pplaytest=waypoints}: opens the screen, adds waypoints by clicking the button, moves the
 * selection with the keys, removes with Delete, scrolls the list past six rows.
 */
public final class WaypointsPlaytest {
    private WaypointsPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("waypoints", s -> {
            s.run(() -> {
                Waypoints.clear();
                WaypointsScreen.open();
            });
            s.waitUntil("empty list shows its message", () -> Playtest.query(".empty").size() == 1, Playtest.WAIT);
            s.shot("waypoints-empty");
            // The button's title shows as a vanilla tooltip (screenshot only: nothing to query).
            s.hover("button");
            s.shot("waypoints-title");
            for (int i = 0; i < 8; i++) {
                s.click("button");
            }
            s.waitUntil("eight rows, the last one selected", () -> Playtest.query(".row").size() == 8
                    && Playtest.query(".row.selected").size() == 1 && selectedName().equals("Waypoint 8"), Playtest.WAIT);
            s.expect("list scrolls: six rows fit", () -> Playtest.query(".list").get(0).maxScrollTop() > 0);
            // The keys are template filters on the screen's top-level element: document-level, no focus needed.
            s.key(GLFW.GLFW_KEY_UP);
            s.key(GLFW.GLFW_KEY_UP);
            s.waitUntil("Up twice selects waypoint 6", () -> selectedName().equals("Waypoint 6"), Playtest.WAIT);
            s.key(GLFW.GLFW_KEY_DELETE);
            s.waitUntil("Delete removes it and selects the next", () -> Playtest.query(".row").size() == 7
                    && selectedName().equals("Waypoint 7"), Playtest.WAIT);
            s.click(".row");
            s.waitUntil("click selects the first row", () -> selectedName().equals("Waypoint 1"), Playtest.WAIT);
            s.shot("waypoints-list");
            s.run(() -> Minecraft.getInstance().setScreen(null));
        });
    }

    private static String selectedName() {
        return Playtest.text(".row.selected .name");
    }
}
