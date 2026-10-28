package com.azane.ixchel.sample;

import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.sample.character.CharacterClient;
import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.Script;
import com.azane.ixchel.sample.env.EnvHud;
import com.azane.ixchel.sample.loot.LootClient;
import com.azane.ixchel.sample.machine.MachineClient;
import com.azane.ixchel.sample.refit.RefitClient;
import com.azane.ixchel.sample.waypoints.WaypointsClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** H opens the sample index. Each sample package adds its entry to {@link #all}. */
public final class Samples {
    private static final KeyMapping OPEN = new KeyMapping("key.ixchelsample.index", GLFW.GLFW_KEY_H, "key.categories.misc");

    private Samples() {
    }

    /** Every sample, in index order. Built when the index opens: icons need the item registry. */
    public static List<Sample> all() {
        return List.of(
                RefitClient.refitSample(),
                EnvHud.sample(),
                CharacterClient.sample(),
                MachineClient.sample(),
                LootClient.hudSample(),
                LootClient.screenSample(),
                WaypointsClient.sample());
    }

    public static UiScreen indexScreen() {
        return new UiScreen(IxchelSampleMod.MOD_ID, "ui/index.html", List.of("ui/index.css"), new SampleIndexView(all()));
    }

    static void initClient(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(indexScreen());
                }
            }
        });
        Checks.register("index", s -> s
                .run(() -> Minecraft.getInstance().setScreen(indexScreen()))
                .waitUntil("index lists every sample", () -> Minecraft.getInstance().screen instanceof UiScreen
                        && Script.query(".row").size() == all().size(), Script.WAIT)
                .shot("sample-index"));
    }
}
