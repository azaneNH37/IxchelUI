package com.azane.ixchel.sample.playtest;

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
 * without errors, shows a tooltip on the overlay surface, keeps every box (the library components'
 * parts included) where the first theme put it, and holo and cyber, whose animations and effects run
 * every frame, draw within the showcase's budget. One screenshot per theme.
 */
public final class ThemesPlaytest {
    private static final List<String> THEMES = List.of("vanilla", "dark", "holo", "cyber");
    /** p95 CPU time per frame of the showcase in holo, the heaviest theme (median about 5000 us), in microseconds. */
    private static final double BUDGET_US = 12000;
    /** The boxes under the first theme, kept between steps. */
    private static final String[] first = {null};
    /** The document open before a tab is clicked, kept between steps. */
    private static final UiDocument[] before = {null};

    private ThemesPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("themes", s -> {
            s.run(ShowcaseScreen::open);
            for (String theme : THEMES) {
                if (!theme.equals("vanilla")) {
                    s.run(() -> before[0] = Playtest.document());
                    s.click(".tab-" + theme);
                    // Fails if a tab's pick does not reach the page: the tab would select itself on the
                    // page already open.
                    s.waitUntil("clicking the " + theme + " tab opens another page", () -> Playtest.isOpen()
                            && Playtest.document() != before[0], Playtest.WAIT);
                }
                // Fails if a tab does not open its theme's page.
                s.waitUntil("the " + theme + " page opens", () -> Playtest.isOpen()
                        && Playtest.query(".tab-" + theme + " .ix-tab-button.ix-selected").size() == 1, Playtest.WAIT);
                s.expect(theme + " reports no errors", () -> Playtest.document().diagnostics().errorCount() == 0);
                s.pause(10);
                // Fails if a theme sets a size, margin, padding or border width: the boxes would move.
                s.expect(theme + " leaves every box where the first theme put it", () -> {
                    String now = boxes();
                    if (first[0] == null) {
                        first[0] = now;
                    }
                    return now.equals(first[0]);
                });
                if (theme.equals("holo") || theme.equals("cyber")) {
                    s.run(() -> FrameTimer.screen(4096));
                    s.pause(100);
                    s.run(() -> FrameTimer.expect(s, theme + " showcase standing still", BUDGET_US));
                }
                s.hover(".tipped");
                s.waitUntil(theme + " shows the tooltip", () -> {
                    Element tip = Playtest.document().topLayer().tooltip();
                    return tip != null && tip.parent() == Playtest.query(".tipped").get(0);
                }, Playtest.WAIT);
                s.pause(10);
                s.shot("themes-" + theme);
            }
            s.run(() -> {
                first[0] = null;
                before[0] = null;
            });
        });
    }

    /** Every element's border box, in tree order; a tooltip is placed at the pointer, so it is left out. */
    private static String boxes() {
        StringBuilder out = new StringBuilder();
        boxes(Playtest.document().root(), out);
        return out.toString();
    }

    private static void boxes(Element e, StringBuilder out) {
        if (e.tag().equals("ix-tooltip")) {
            return;
        }
        out.append(e.tag()).append(' ').append(e.box().x()).append(' ').append(e.box().y()).append(' ')
                .append(e.box().width()).append(' ').append(e.box().height()).append('\n');
        for (Node n : e.children()) {
            if (n instanceof Element c) {
                boxes(c, out);
            }
        }
    }
}
