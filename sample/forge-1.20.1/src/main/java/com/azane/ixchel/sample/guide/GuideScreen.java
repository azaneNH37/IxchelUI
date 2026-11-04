package com.azane.ixchel.sample.guide;

import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * One place of the book. The author splits an entry into explicit page elements; how many there are
 * is read from the document after each frame, so an edited and reloaded entry turns over its new
 * pages.
 */
final class GuideScreen extends UiScreen {
    private final GuideModel view;

    GuideScreen(ResourceLocation page, GuideModel view) {
        super(page, view);
        this.view = view;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        UiDocument d = document();
        if (d != null) {
            view.pages.set(d.querySelectorAll(".page").size());
        }
    }
}
