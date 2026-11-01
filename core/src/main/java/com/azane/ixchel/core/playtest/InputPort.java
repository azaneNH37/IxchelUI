package com.azane.ixchel.core.playtest;

import com.azane.ixchel.core.runtime.UiDocument;

/**
 * The host's input path that a {@link PlaytestDriver} acts through, so a playtest exercises what a player's
 * input reaches: the host screen's handlers, not the document directly. Coordinates are GUI pixels;
 * keys and modifiers are GLFW codes. Client thread only.
 */
public interface InputPort {
    /**
     * The document of the open host screen.
     *
     * @throws IllegalStateException when no screen with a document is open
     */
    UiDocument document();

    /** Puts the host's cursor at {@code (x, y)}, the position its rendering reads for hover. */
    void pointAt(double x, double y);

    void mouseMoved(double x, double y);

    void mouseClicked(double x, double y, int button);

    void mouseReleased(double x, double y, int button);

    void mouseDragged(double x, double y, int button, double dx, double dy);

    /** Positive {@code delta} scrolls up. */
    void mouseScrolled(double x, double y, double delta);

    void keyPressed(int key, int modifiers);

    void keyReleased(int key, int modifiers);

    void charTyped(char c);
}
