package com.azane.ixchel.mc;

import com.azane.ixchel.core.runtime.UiDocument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Display-only UI over the whole GUI-scaled screen; the loader layer calls {@link #render} from its HUD layer. */
public final class UiHud {
    private final UiDocument document;
    private final McPainter painter = new McPainter(Minecraft.getInstance().font);

    public UiHud(UiDocument document) {
        this.document = document;
    }

    public void render(GuiGraphics g) {
        painter.paint(g, document.frame(g.guiWidth(), g.guiHeight()));
    }
}
