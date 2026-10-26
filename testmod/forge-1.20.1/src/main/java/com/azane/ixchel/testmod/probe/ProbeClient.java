package com.azane.ixchel.testmod.probe;

import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/** Slot L1 probe: K opens {@link SlotProbeScreen} over the player inventory. */
public final class ProbeClient {
    static final Logger LOG = LogUtils.getLogger();
    private static final KeyMapping OPEN =
            new KeyMapping("key.ixcheltest.slot_probe", GLFW.GLFW_KEY_K, "key.categories.misc");

    private ProbeClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN));
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(SlotWriter::selfCheck));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new SlotProbeScreen(mc.player));
                }
            }
        });
        // Stands in for another mod appending a widget after init; it lands behind the scissor renderable.
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> {
            if (e.getScreen() instanceof SlotProbeScreen) {
                e.addListener(Button.builder(Component.literal("event btn"), b -> LOG.info("[ixchel-probe] event btn clicked"))
                        .bounds(4, 28, 80, 20).build());
            }
        });
    }
}
