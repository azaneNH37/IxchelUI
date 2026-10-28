package com.azane.ixchel.core.event;

/**
 * The system clipboard as the host provides it ({@link EventDispatcher#setClipboard}); text inputs
 * copy, cut and paste through it. Until a host sets one, a clipboard private to the dispatcher.
 */
public interface Clipboard {
    /** The clipboard text; empty when it holds none. */
    String get();

    void set(String text);
}
