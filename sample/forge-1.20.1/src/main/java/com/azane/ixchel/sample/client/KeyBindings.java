package com.azane.ixchel.sample.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Every key of the sample, in its own controls category; what each does is wired in {@link ClientSetup}. */
public final class KeyBindings {
    private static final String CATEGORY = "key.categories.ixchelsample";

    /** H: the sample index. */
    public static final KeyMapping INDEX = new KeyMapping("key.ixchelsample.index", GLFW.GLFW_KEY_H, CATEGORY);
    /** G: the character panel. */
    public static final KeyMapping CHARACTER = new KeyMapping("key.ixchelsample.character", GLFW.GLFW_KEY_G, CATEGORY);
    /** U: the loot radar list. */
    public static final KeyMapping LOOT_LIST = new KeyMapping("key.ixchelsample.loot", GLFW.GLFW_KEY_U, CATEGORY);
    /** J: the waypoints list. */
    public static final KeyMapping WAYPOINTS = new KeyMapping("key.ixchelsample.waypoints", GLFW.GLFW_KEY_J, CATEGORY);

    private KeyBindings() {
    }

    static void register(RegisterKeyMappingsEvent e) {
        e.register(INDEX);
        e.register(CHARACTER);
        e.register(LOOT_LIST);
        e.register(WAYPOINTS);
    }
}
