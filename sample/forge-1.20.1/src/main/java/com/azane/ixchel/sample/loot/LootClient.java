package com.azane.ixchel.sample.loot;

import com.azane.ixchel.forge.IxchelForge;
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

/**
 * Loot radar sample (S4): a HUD labeling the drops around the player, with edge arrows for those out
 * of view and the pickup toasts, and a list screen (U) sorted by distance with a rarity filter and a
 * pick-up button.
 */
public final class LootClient {
    static final String ID = "loot";
    private static final KeyMapping OPEN = new KeyMapping("key.ixchelsample.loot", GLFW.GLFW_KEY_U, "key.categories.misc");
    private static IxchelForge.Hud hud;

    private LootClient() {
    }

    /** Client only. */
    public static void init(IEventBus modBus) {
        hud = IxchelForge.registerHud(modBus, IxchelSampleMod.MOD_ID, ID, "ui/loot_hud.html", List.of("ui/loot_hud.css"),
                LootView::new);
        hud.show(false);
        IxchelForge.registerWorldProjection();
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
        LootCheck.init(hud);
    }

    static UiScreen screen() {
        return new UiScreen(IxchelSampleMod.MOD_ID, "ui/loot.html", List.of("ui/loot.css"), new LootListView());
    }

    public static Sample hudSample() {
        return Sample.hud(ID, "Loot radar HUD", "Labels over the drops in view, an arrow with a count at the screen "
                + "edge for the rest, and toasts for what you pick up.", new ItemStack(Items.HOPPER), hud);
    }

    public static Sample screenSample() {
        return Sample.screen(ID + "-list", "Loot radar list (U)", "Drops nearest first with their direction, a "
                + "rarity filter, and a pick-up button the server only honors within 8 blocks.",
                new ItemStack(Items.RECOVERY_COMPASS), () -> Minecraft.getInstance().setScreen(screen()));
    }
}
