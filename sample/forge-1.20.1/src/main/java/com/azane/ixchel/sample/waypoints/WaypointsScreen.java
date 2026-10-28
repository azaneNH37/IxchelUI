package com.azane.ixchel.sample.waypoints;

import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Waypoints sample: J opens a list screen with keyboard selection (Up, Down and Delete are
 * {@code on:keydown.<key>} filters in the template) and a scrolling list.
 */
public final class WaypointsScreen {
    private static final ResourceLocation TEMPLATE = new ResourceLocation(IxchelSampleMod.MOD_ID, "ui/waypoints/list.html");

    private WaypointsScreen() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new UiScreen(TEMPLATE, new WaypointsView()));
    }
}
