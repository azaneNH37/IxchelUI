package com.azane.ixchel.sample.client;

import com.azane.ixchel.mc.element.McElements;
import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.character.CharacterScreen;
import com.azane.ixchel.sample.client.index.SampleIndex;
import com.azane.ixchel.sample.config.ConfigScreen;
import com.azane.ixchel.sample.env.EnvHud;
import com.azane.ixchel.sample.loot.LootScreens;
import com.azane.ixchel.sample.machine.MachineScreen;
import com.azane.ixchel.sample.storage.TerminalScreen;
import com.azane.ixchel.sample.tooltips.GaugeElement;
import com.azane.ixchel.sample.waypoints.WaypointsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All client wiring: key bindings, menu screens, HUD layers, and the one client tick loop that
 * opens a screen when its key is pressed outside any screen.
 */
public final class ClientSetup {
    private static final Map<KeyMapping, Runnable> KEYS = new LinkedHashMap<>();

    private ClientSetup() {
    }

    /** Client only; from the mod constructor. */
    public static void init(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        McElements.register(IxchelSampleMod.MOD_ID, new GaugeElement());
        modBus.addListener(KeyBindings::register);
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            MachineScreen.registerScreen();
            TerminalScreen.registerScreen();
        }));
        EnvHud.register(modBus);
        LootScreens.register(modBus);
        ConfigScreen.registerFactory(context);
        KEYS.put(KeyBindings.INDEX, SampleIndex::open);
        KEYS.put(KeyBindings.CHARACTER, CharacterScreen::open);
        KEYS.put(KeyBindings.LOOT_LIST, LootScreens::openList);
        KEYS.put(KeyBindings.WAYPOINTS, WaypointsScreen::open);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (Map.Entry<KeyMapping, Runnable> key : KEYS.entrySet()) {
            while (key.getKey().consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    key.getValue().run();
                }
            }
        }
    }
}
