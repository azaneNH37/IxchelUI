package com.azane.ixchel.testmod.probe;

import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
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
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new SlotProbeScreen(mc.player));
                }
            }
        });
    }
}
