package com.azane.ixchel.sample.spell;

import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.sample.client.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Spell wheel sample: holding R opens a wheel of eight spells over the game; the pointer's direction
 * from the center picks one, and releasing R casts it (a click casts too). A screen rather than a
 * HUD, for the pointer; it draws no background, so the game stays unshaded around the wheel. Mana and
 * cooldowns live across openings, on the client only.
 */
public final class SpellWheel {
    private static final SpellBook BOOK = new SpellBook();

    private SpellWheel() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new Screen(new SpellWheelModel(BOOK)));
    }

    static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof Screen) {
            mc.setScreen(null);
        }
    }

    static void castSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EVOKER_CAST_SPELL, 1.2f));
    }

    /** Casts on the key's release; its presses (and their repeats) do nothing. */
    private static final class Screen extends UiScreen {
        private final SpellWheelModel model;

        Screen(SpellWheelModel model) {
            super(model);
            this.model = model;
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            return KeyBindings.SPELL.matches(key, scanCode) || super.keyPressed(key, scanCode, modifiers);
        }

        @Override
        public boolean keyReleased(int key, int scanCode, int modifiers) {
            if (KeyBindings.SPELL.matches(key, scanCode)) {
                model.castPointed();
                onClose();
                return true;
            }
            return super.keyReleased(key, scanCode, modifiers);
        }

        @Override
        public void renderBackground(GuiGraphics g) {
        }
    }
}
