package com.azane.ixchel.sample.waypoints;

import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.Minecraft;

/**
 * Waypoints sample: J opens a list screen with keyboard selection (Up, Down and Delete are
 * {@code on:keydown.<key>} filters in the template) and a scrolling list.
 */
public final class WaypointsScreen {
    private WaypointsScreen() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new UiScreen(new WaypointsView()));
    }
}
