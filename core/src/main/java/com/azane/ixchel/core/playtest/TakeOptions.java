package com.azane.ixchel.core.playtest;

import com.azane.ixchel.core.api.HostApi;

/**
 * What a {@linkplain PlaytestDriver#take take} shows: the whole window, or the border box of an
 * element with some padding around it (cut to the window), and whether a GIF is made besides the
 * MP4. Immutable.
 *
 * <pre>{@code
 * s.take("panel-open", TakeOptions.element(".ix-panel").padding(12).gif(), t -> t.click(".open").hold(800));
 * }</pre>
 */
@HostApi
public final class TakeOptions {
    /** Padding around an element, in GUI pixels, unless set. */
    public static final float PADDING = 8;
    private static final TakeOptions WINDOW = new TakeOptions(null, PADDING, false);

    private final String selector;
    private final float padding;
    private final boolean gif;

    private TakeOptions(String selector, float padding, boolean gif) {
        this.selector = selector;
        this.padding = padding;
        this.gif = gif;
    }

    /** The whole window. */
    public static TakeOptions window() {
        return WINDOW;
    }

    /**
     * The laid-out border box of the first element {@code selector} matches when the take begins, plus
     * {@value #PADDING} GUI pixels each side; the element must be on screen then.
     */
    public static TakeOptions element(String selector) {
        return new TakeOptions(selector, PADDING, false);
    }

    /** These options with {@code px} GUI pixels around the element instead. */
    public TakeOptions padding(float px) {
        return new TakeOptions(selector, px, gif);
    }

    /** These options, also making a GIF of the take. */
    public TakeOptions gif() {
        return new TakeOptions(selector, padding, true);
    }

    String selector() {
        return selector;
    }

    float paddingPx() {
        return padding;
    }

    boolean makesGif() {
        return gif;
    }
}
