package com.azane.ixchel.sample.character;

import com.azane.ixchel.mc.UiContainerScreen;
import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.Sample;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Character panel sample (S1): the player's own inventory menu laid out by a document, with tabs for
 * equipment (paper doll, armor, offhand, crafting), attributes, effects and statistics. Opened with G,
 * next to vanilla's E inventory rather than replacing it (#009).
 */
public final class CharacterClient {
    static final String ID = "character";
    private static final KeyMapping OPEN = new KeyMapping("key.ixchelsample.character", GLFW.GLFW_KEY_G,
            "key.categories.misc");

    private CharacterClient() {
    }

    /** Client only. */
    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(screen(mc.player));
                }
            }
        });
        CharacterCheck.init();
    }

    /**
     * The player's inventory menu needs no open packet: like vanilla's inventory screen, its
     * container id is 0, which the server always has open.
     */
    static UiContainerScreen<InventoryMenu> screen(LocalPlayer player) {
        return new UiContainerScreen<>(player.inventoryMenu, player.getInventory(), player.getDisplayName(),
                IxchelSampleMod.MOD_ID, "ui/character.html", List.of("ui/character.css"), new CharacterView());
    }

    public static Sample sample() {
        return Sample.screen(ID, "Character panel (G)", "Your inventory with tabs: a paper doll that looks at the "
                + "pointer, attributes with tooltips, effects that blink as they end, and statistics from the "
                + "server.", new ItemStack(Items.IRON_CHESTPLATE), () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.setScreen(screen(mc.player));
                });
    }
}
