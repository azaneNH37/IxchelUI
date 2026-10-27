package com.azane.ixchel.sample;

import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.sample.check.Script;
import com.azane.ixchel.sample.refit.RefitClient;
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
                RefitClient.backpackSample());
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
        Script s = Script.forBench("index");
        if (s != null) {
            s.step(40, () -> Minecraft.getInstance().setScreen(indexScreen()));
            s.step(20, () -> {
                s.expect("index screen is open", () -> Minecraft.getInstance().screen instanceof UiScreen);
                Script.shot("sample-index");
            });
            s.start();
        }
    }
}
