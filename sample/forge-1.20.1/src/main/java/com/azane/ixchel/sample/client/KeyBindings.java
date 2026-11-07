package com.azane.ixchel.sample.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Every key of the sample, in its own controls category; what each does is wired in {@link ClientSetup}. */
public final class KeyBindings {
    private static final String CATEGORY = "key.categories.ixchel_sample";

    /** H: the sample index. */
    public static final KeyMapping INDEX = new KeyMapping("key.ixchel_sample.index", GLFW.GLFW_KEY_H, CATEGORY);
    /** G: the character panel. */
    public static final KeyMapping CHARACTER = new KeyMapping("key.ixchel_sample.character", GLFW.GLFW_KEY_G, CATEGORY);
    /** U: the loot radar list. */
    public static final KeyMapping LOOT_LIST = new KeyMapping("key.ixchel_sample.loot", GLFW.GLFW_KEY_U, CATEGORY);
    /** J: the waypoints list. */
    public static final KeyMapping WAYPOINTS = new KeyMapping("key.ixchel_sample.waypoints", GLFW.GLFW_KEY_J, CATEGORY);

    /** R, held: the spell wheel; released, it casts the spell pointed at. */
    public static final KeyMapping SPELL = new KeyMapping("key.ixchel_sample.spell", GLFW.GLFW_KEY_R, CATEGORY);

    private KeyBindings() {
    }

    static void register(RegisterKeyMappingsEvent e) {
        e.register(INDEX);
        e.register(CHARACTER);
        e.register(LOOT_LIST);
        e.register(WAYPOINTS);
        e.register(SPELL);
    }
}
