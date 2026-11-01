package com.azane.ixchel.sample.refit;

import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Z opens the refit screen on the held crossbow. */
public final class RefitClient {
    private static final KeyMapping OPEN = new KeyMapping("key.ixchel_sample.refit", GLFW.GLFW_KEY_Z, "key.categories.misc");

    private RefitClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            boolean canOpen = mc.player != null && mc.screen == null && Attachments.isGun(mc.player.getMainHandItem());
            while (OPEN.consumeClick()) {
                if (canOpen) {
                    mc.setScreen(screen(new RefitView(mc.player.getInventory().selected)));
                }
            }
        });
    }

    static UiScreen screen(RefitView view) {
        // A consumer outside the UI tree (TACZ moves the first-person camera): the action bar names
        // the attachment being looked at. The subscription ends when the screen closes.
        view.subscribe(view.selectedType, () -> {
            String type = view.selectedType.get();
            if (type != null) {
                Minecraft.getInstance().gui.setOverlayMessage(
                        Component.literal("Inspecting ").append(Attachments.type(type).name()), false);
            }
        });
        return new UiScreen(view);
    }
}
