package com.azane.ixchel.mc;

import com.azane.ixchel.core.event.Keys;
import net.minecraft.client.gui.screens.Screen;

/** The modifier keys held now, for input vanilla passes without them: mouse buttons. */
final class Modifiers {
    private Modifiers() {
    }

    static int held() {
        return (Screen.hasShiftDown() ? Keys.MOD_SHIFT : 0) | (Screen.hasControlDown() ? Keys.MOD_CTRL : 0)
                | (Screen.hasAltDown() ? Keys.MOD_ALT : 0);
    }
}
