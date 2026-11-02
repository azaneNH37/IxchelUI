package com.azane.ixchel.core.event;

/**
 * The control click sound as the host plays it ({@link EventDispatcher#setClickSound}): a
 * {@code <button>} clicked or activated by key, a checkbox toggled, a {@code <select>} opened, unless
 * the control's {@code -ix-click-sound} is {@code none}. Until a host sets one, silence.
 */
@FunctionalInterface
public interface ClickSound {
    void play();
}
