package com.azane.ixchel.sample.showcase;

import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Theme showcase sample: the same component on four pages, each linking one built-in theme
 * (vanilla, dark, holo, cyber). Switching themes opens the other page, so the document loads again.
 */
public final class ShowcaseScreen {
    private ShowcaseScreen() {
    }

    public static void open() {
        open("vanilla");
    }

    public static void open(String theme) {
        ResourceLocation page = new ResourceLocation("ixchel_sample", "ui/showcase/themes/" + theme + ".html");
        Minecraft.getInstance().setScreen(new UiScreen(page, new ShowcaseModel(theme)));
    }
}
