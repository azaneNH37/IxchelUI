package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.mc.document.ExitingDocuments;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.sample.waypoints.Waypoints;
import com.azane.ixchel.sample.waypoints.WaypointsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
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
            // Fails if opening a screen does not start from the document's :-ix-absent style.
            s.expect("the panel opens below its place", () -> offset(Playtest.query(".panel").get(0)) > 1);
            s.shot("waypoints-entering");
            s.waitUntil("the panel settles", () -> Playtest.query(".screen:-ix-entering").isEmpty()
                    && offset(Playtest.query(".panel").get(0)) == 0, Playtest.WAIT);
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
            UiDocument[] closed = {null};
            Vec3[] from = {null};
            s.run(() -> {
                Minecraft mc = Minecraft.getInstance();
                closed[0] = Playtest.document();
                from[0] = mc.player.position();
                mc.setScreen(null);
                mc.options.keyUp.setDown(true);
            });
            s.pause(2);
            // Fails if the screen stays open during its exit, or the game does not get the input back.
            s.expect("the closed screen exits while the player walks", () -> {
                Minecraft mc = Minecraft.getInstance();
                return mc.screen == null && closed[0].exiting() && ExitingDocuments.any()
                        && mc.player.position().distanceTo(from[0]) > 0;
            });
            s.shot("waypoints-leaving");
            s.waitUntil("the exit ends and nothing is drawn any more", () -> !ExitingDocuments.any(), Playtest.WAIT);
            s.run(() -> Minecraft.getInstance().options.keyUp.setDown(false));
        });
    }

    private static String selectedName() {
        return Playtest.text(".row.selected .name");
    }

    /** How far below its layout {@code panel} paints. */
    private static float offset(Element panel) {
        LayoutBox b = panel.box();
        return EventDispatcher.paintedCenter(panel)[1] - (b.y() + b.height() / 2);
    }
}
