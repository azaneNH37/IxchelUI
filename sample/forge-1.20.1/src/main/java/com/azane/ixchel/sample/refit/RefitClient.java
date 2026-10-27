package com.azane.ixchel.sample.refit;

import com.azane.ixchel.mc.UiContainerScreen;
import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Z opens the refit screen on the held crossbow, V the hand-written vanilla version of it, B the
 * backpack refit (a container screen).
 */
public final class RefitClient {
    private static final KeyMapping OPEN = new KeyMapping("key.ixchelsample.refit", GLFW.GLFW_KEY_Z, "key.categories.misc");
    private static final KeyMapping OPEN_VANILLA =
            new KeyMapping("key.ixchelsample.refit_vanilla", GLFW.GLFW_KEY_V, "key.categories.misc");
    private static final KeyMapping OPEN_BACKPACK =
            new KeyMapping("key.ixchelsample.backpack_refit", GLFW.GLFW_KEY_B, "key.categories.misc");

    private RefitClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(OPEN);
            e.register(OPEN_VANILLA);
            e.register(OPEN_BACKPACK);
        });
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> MenuScreens.register(RefitMenus.BACKPACK.get(),
                (BackpackRefitMenu menu, Inventory inv, Component title) -> new UiContainerScreen<>(menu, inv, title,
                        IxchelSampleMod.MOD_ID, "ui/backpack_refit.html", List.of("ui/backpack_refit.css"),
                        new BackpackRefitView(menu), UiContainerScreen.SlotItemRendering.IXCHEL))));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            boolean canOpen = mc.player != null && mc.screen == null && Attachments.isGun(mc.player.getMainHandItem());
            while (OPEN.consumeClick()) {
                if (canOpen) {
                    mc.setScreen(screen(new RefitView(mc.player.getInventory().selected)));
                }
            }
            while (OPEN_VANILLA.consumeClick()) {
                if (canOpen) {
                    mc.setScreen(new VanillaRefitScreen(mc.player.getInventory().selected));
                }
            }
            while (OPEN_BACKPACK.consumeClick()) {
                if (canOpen) {
                    RefitNetwork.openBackpack();
                }
            }
        });
        RefitBench.init();
        BackpackRefitCheck.init();
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
        return new UiScreen(IxchelSampleMod.MOD_ID, "ui/refit.html", List.of("ui/refit.css"), view);
    }
}
