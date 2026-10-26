package com.azane.ixchel.testmod.refit;

import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.testmod.IxchelTestMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Z opens the refit screen on the held crossbow. */
public final class RefitClient {
    private static final KeyMapping OPEN = new KeyMapping("key.ixcheltest.refit", GLFW.GLFW_KEY_Z, "key.categories.misc");

    private RefitClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            Minecraft mc = Minecraft.getInstance();
            while (e.phase == TickEvent.Phase.END && OPEN.consumeClick()) {
                if (mc.player != null && mc.screen == null && Attachments.isGun(mc.player.getMainHandItem())) {
                    mc.setScreen(open(mc.player.getInventory().selected));
                }
            }
        });
    }

    private static UiScreen open(int slot) {
        RefitView view = new RefitView(slot);
        // A consumer outside the UI tree (TACZ moves the first-person camera): the action bar names
        // the attachment being looked at. The subscription ends when the screen closes.
        view.subscribe(view.selectedType, () -> {
            String type = view.selectedType.get();
            if (type != null) {
                Minecraft.getInstance().gui.setOverlayMessage(
                        Component.literal("Inspecting ").append(Attachments.type(type).name()), false);
            }
        });
        return new UiScreen(IxchelTestMod.MOD_ID, "ui/refit.html", List.of("ui/refit.css"), view);
    }
}
