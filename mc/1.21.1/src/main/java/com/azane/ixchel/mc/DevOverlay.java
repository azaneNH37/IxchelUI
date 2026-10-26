package com.azane.ixchel.mc;

import com.azane.ixchel.core.diag.Diagnostic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Development-only error panel drawn over the UI: one {@code file:line:col: error: message} per line. */
public final class DevOverlay {
    private static final int MAX_LINES = 12;
    private static final int PAD = 3;

    private DevOverlay() {
    }

    /** Draws {@code errors} at the top-left of the screen above everything else; does nothing if empty. */
    public static void draw(GuiGraphics g, List<Diagnostic> errors) {
        if (errors.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int maxWidth = g.guiWidth() - 2 * PAD;
        int shown = Math.min(errors.size(), MAX_LINES);
        int lineHeight = font.lineHeight + 1;
        int height = (shown + 1) * lineHeight + 2 * PAD;
        g.pose().pushPose();
        g.pose().translate(0, 0, 1000);
        g.fill(0, 0, g.guiWidth(), height, 0xD0000000);
        String header = "Ixchel UI: " + errors.size() + (errors.size() == 1 ? " error" : " errors")
                + (errors.size() > shown ? " (first " + shown + " shown)" : "");
        g.drawString(font, header, PAD, PAD, 0xFFFF5555, false);
        for (int i = 0; i < shown; i++) {
            String line = font.plainSubstrByWidth(errors.get(i).toString(), maxWidth);
            g.drawString(font, line, PAD, PAD + (i + 1) * lineHeight, 0xFFFFFFFF, false);
        }
        g.pose().popPose();
    }
}
