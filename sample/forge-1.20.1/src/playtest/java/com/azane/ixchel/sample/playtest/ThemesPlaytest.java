package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.mc.FrameTimer;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.sample.showcase.ShowcaseScreen;

import java.util.List;

/**
 * {@code -Pplaytest=themes}: the showcase opens in each built-in theme through its {@code ix-tab}s
 * without errors, shows a tooltip on the overlay surface, gives every box (the library components'
 * parts included) the box model the first theme gave it, and holo and amber, whose animations and effects run
 * every frame, draw within the showcase's budget. One screenshot per theme; {@code -Precord=themes}
 * records the walk through the four themes as {@code recordings/themes.mp4}.
 */
public final class ThemesPlaytest {
    private static final List<String> THEMES = List.of("vanilla", "dark", "holo", "amber");
    /** p95 CPU time per frame of the showcase in holo, the heaviest theme (median about 5000 us), in microseconds. */
    private static final double BUDGET_US = 12000;
    /** The box models under the first theme, kept between steps. */
    private static final String[] first = {null};
    /** The document open before a tab is clicked, kept between steps. */
    private static final UiDocument[] before = {null};
    /** What places and sizes a box, other than its text; the theme contract in base.css leaves them to the user agent sheet. */
    private static final List<PropertyDef<?>> BOX_MODEL = List.of(
            CssProperties.DISPLAY, CssProperties.POSITION,
            CssProperties.TOP, CssProperties.RIGHT, CssProperties.BOTTOM, CssProperties.LEFT,
            CssProperties.WIDTH, CssProperties.HEIGHT, CssProperties.MIN_WIDTH, CssProperties.MIN_HEIGHT,
            CssProperties.MAX_WIDTH, CssProperties.MAX_HEIGHT, CssProperties.ASPECT_RATIO,
            CssProperties.MARGIN_TOP, CssProperties.MARGIN_RIGHT, CssProperties.MARGIN_BOTTOM, CssProperties.MARGIN_LEFT,
            CssProperties.PADDING_TOP, CssProperties.PADDING_RIGHT, CssProperties.PADDING_BOTTOM, CssProperties.PADDING_LEFT,
            CssProperties.BORDER_TOP_WIDTH, CssProperties.BORDER_RIGHT_WIDTH,
            CssProperties.BORDER_BOTTOM_WIDTH, CssProperties.BORDER_LEFT_WIDTH,
            CssProperties.FLEX_DIRECTION, CssProperties.FLEX_WRAP, CssProperties.FLEX_GROW, CssProperties.FLEX_SHRINK,
            CssProperties.FLEX_BASIS, CssProperties.ROW_GAP, CssProperties.COLUMN_GAP, CssProperties.OVERFLOW,
            CssProperties.FONT_SIZE, CssProperties.LINE_HEIGHT);

    private ThemesPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("themes", s -> {
            s.run(ShowcaseScreen::open);
            // Recorded: the showcase through the four themes, the tooltip shown in each.
            s.take("themes", t -> {
                for (String theme : THEMES) {
                    if (!theme.equals("vanilla")) {
                        t.run(() -> before[0] = Playtest.document());
                        t.click(".tab-" + theme);
                        // Fails if a tab's pick does not reach the page: the tab would select itself on the
                        // page already open.
                        t.waitUntil("clicking the " + theme + " tab opens another page", () -> Playtest.isOpen()
                                && Playtest.document() != before[0], Playtest.WAIT);
                    }
                    // Fails if a tab does not open its theme's page.
                    t.waitUntil("the " + theme + " page opens", () -> Playtest.isOpen()
                            && Playtest.query(".tab-" + theme + " .ix-tab-button.ix-selected").size() == 1, Playtest.WAIT);
                    t.expect(theme + " reports no errors", () -> Playtest.document().diagnostics().errorCount() == 0);
                    t.pause(10);
                    // Fails if a theme sets a size, margin, padding or border width: the boxes would move.
                    t.expect(theme + " leaves every box model as the first theme had it", () -> {
                        String now = boxes();
                        if (first[0] == null) {
                            first[0] = now;
                        }
                        return now.equals(first[0]);
                    });
                    if (theme.equals("holo") || theme.equals("amber")) {
                        t.run(() -> FrameTimer.screen(4096));
                        t.pause(100);
                        t.run(() -> FrameTimer.expect(t, theme + " showcase standing still", BUDGET_US));
                    }
                    t.hover(".tipped");
                    t.waitUntil(theme + " shows the tooltip", () -> {
                        Element tip = Playtest.document().topLayer().tooltip();
                        return tip != null && tip.parent() == Playtest.query(".tipped").get(0);
                    }, Playtest.WAIT);
                    t.pause(10);
                    t.shot("themes-" + theme);
                    t.hold(1000);
                }
            });
            s.run(() -> {
                first[0] = null;
                before[0] = null;
            });
        });
    }

    /**
     * Every element's box-model properties, in tree order; a tooltip is placed at the pointer, so it is
     * left out. Not the laid-out boxes: a theme's font and case change how wide text is (base.css), and
     * every box around the text with it.
     */
    private static String boxes() {
        StringBuilder out = new StringBuilder();
        boxes(Playtest.document().root(), out);
        return out.toString();
    }

    private static void boxes(Element e, StringBuilder out) {
        if (e.tag().equals("ix-tooltip")) {
            return;
        }
        out.append(e.tag());
        for (PropertyDef<?> p : BOX_MODEL) {
            out.append(' ').append(e.computed().get(p));
        }
        out.append('\n');
        for (Node n : e.children()) {
            if (n instanceof Element c) {
                boxes(c, out);
            }
        }
    }
}
