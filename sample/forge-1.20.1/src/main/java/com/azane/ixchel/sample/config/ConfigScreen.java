package com.azane.ixchel.sample.config;

import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Config panel sample (S6): the sample's real client config ({@link SampleConfig}) edited in a
 * screen that needs no world. Forge's mod list opens it through the mod's config button; in game the
 * sample index does. Closing it returns to the screen it was opened from.
 */
public final class ConfigScreen {
    private static final ResourceLocation TEMPLATE = new ResourceLocation(IxchelSampleMod.MOD_ID, "ui/config/panel.html");

    private ConfigScreen() {
    }

    /** The mod list's config button; client only, from the mod constructor. */
    public static void registerFactory(ModLoadingContext context) {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> create(parent)));
    }

    /** Opens the panel over the current screen, which it returns to. */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(create(mc.screen));
    }

    private static Screen create(Screen parent) {
        UiScreen[] screen = new UiScreen[1];
        screen[0] = new UiScreen(TEMPLATE, new ConfigView(() -> screen[0].onClose()), parent);
        return screen[0];
    }
}
