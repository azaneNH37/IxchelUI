package com.azane.ixchel.mc.playtest;

import com.azane.ixchel.core.playtest.InputPort;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.mc.UiContainerScreen;
import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;

/** Input through the open vanilla {@link Screen}'s handlers, as the keyboard and mouse deliver it. */
final class ScreenInput implements InputPort {
    static final ScreenInput INSTANCE = new ScreenInput();

    private ScreenInput() {
    }

    static Screen screen() {
        Screen s = Minecraft.getInstance().screen;
        if (s == null) {
            throw new IllegalStateException("no screen is open");
        }
        return s;
    }

    /** The open Ixchel screen's document, or {@code null}. */
    static UiDocument openDocument() {
        Screen s = Minecraft.getInstance().screen;
        return s instanceof UiScreen u ? u.document()
                : s instanceof UiContainerScreen<?> c ? c.document() : null;
    }

    @Override
    public UiDocument document() {
        UiDocument d = openDocument();
        if (d == null) {
            throw new IllegalStateException("no Ixchel document on screen " + screen());
        }
        return d;
    }

    /**
     * Sets the cursor position vanilla hands to {@code render}, which is what hover reads. The window
     * is not focused in an unattended run, so moving the OS cursor does not reach the game. Playtests run
     * in development only, where the game has Mojang names.
     */
    @Override
    public void pointAt(double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        try {
            Field xpos = MouseHandler.class.getDeclaredField("xpos");
            Field ypos = MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(mc.mouseHandler, x * scale);
            ypos.setDouble(mc.mouseHandler, y * scale);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void mouseMoved(double x, double y) {
        screen().mouseMoved(x, y);
    }

    @Override
    public void mouseClicked(double x, double y, int button) {
        screen().mouseClicked(x, y, button);
    }

    @Override
    public void mouseReleased(double x, double y, int button) {
        screen().mouseReleased(x, y, button);
    }

    @Override
    public void mouseDragged(double x, double y, int button, double dx, double dy) {
        screen().mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public void mouseScrolled(double x, double y, double delta) {
        screen().mouseScrolled(x, y, delta);
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        screen().keyPressed(key, 0, modifiers);
    }

    @Override
    public void keyReleased(int key, int modifiers) {
        screen().keyReleased(key, 0, modifiers);
    }

    @Override
    public void charTyped(char c) {
        screen().charTyped(c, 0);
    }
}
