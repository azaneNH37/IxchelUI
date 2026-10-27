package com.azane.ixchel.sample.waypoints;

import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.Sample;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Waypoints sample: J opens a list screen with keyboard selection and a scrolling list. */
public final class WaypointsClient {
    static final String ID = "waypoints";
    private static final KeyMapping OPEN = new KeyMapping("key.ixchelsample.waypoints", GLFW.GLFW_KEY_J, "key.categories.misc");

    private WaypointsClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(screen());
                }
            }
        });
        WaypointsCheck.init();
    }

    /**
     * Keys the template cannot name ({@code on:keydown} fires for every key) are handled on the
     * document root from the host: Up/Down move the selection, Delete removes it.
     */
    static UiScreen screen() {
        WaypointsView view = new WaypointsView();
        UiScreen screen = new UiScreen(IxchelSampleMod.MOD_ID, "ui/waypoints.html", List.of("ui/waypoints.css"), view);
        screen.document().root().addEventListener(UiEvent.Type.KEY_DOWN, false, e -> {
            switch (e.key()) {
                case GLFW.GLFW_KEY_UP -> view.move(-1);
                case GLFW.GLFW_KEY_DOWN -> view.move(1);
                case GLFW.GLFW_KEY_DELETE -> view.remove.run(null);
                default -> {
                    return;
                }
            }
            e.consume();
        });
        return screen;
    }

    public static Sample sample() {
        return Sample.screen(ID, "Waypoints (J)",
                "Session list with keyboard selection, a scrolling list and distances that follow the player; "
                        + "keys are wired on the document root by the host.",
                new ItemStack(Items.COMPASS), () -> Minecraft.getInstance().setScreen(screen()));
    }
}
