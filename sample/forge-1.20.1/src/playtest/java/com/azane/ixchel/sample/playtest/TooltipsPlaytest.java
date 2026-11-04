package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.sample.tooltips.TooltipsScreen;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * {@code -Pplaytest=tooltips}: every kind of element shows its own {@code <ix-tooltip>} under the pointer
 * (buttons, form controls, inline terms, items, a component's slot, a dialog's button), the deepest
 * element wins, a {@code title} and a bare item stay vanilla, item lines are read only while shown, a
 * live value updates in place, a tooltip at the screen edge flips inside the screen, and the list's
 * scrollbar thumb drags it to its end.
 */
public final class TooltipsPlaytest {
    private static final String[] countdown = {""};

    private TooltipsPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("tooltips", s -> {
            s.run(TooltipsScreen::open);
            s.waitUntil("the page opens with its 24 list entries", () -> Playtest.isOpen()
                    && Playtest.query(".entry").size() == 24, Playtest.WAIT);
            s.expect("the page reports no errors", () -> Playtest.document().diagnostics().errorCount() == 0);

            // Fails if an element kind does not show its own tooltip.
            s.hover(".save");
            s.waitUntil("a button shows its tooltip, key hints inside", () -> shows(".save")
                    && Playtest.query(".save .key").size() == 2, Playtest.WAIT);
            s.shot("tooltips-button");
            s.hover(".name");
            s.waitUntil("an input shows its tooltip", () -> shows(".name"), Playtest.WAIT);
            s.hover(".mode");
            s.waitUntil("a closed select shows its tooltip", () -> shows(".mode"), Playtest.WAIT);
            s.hover(".term");
            s.waitUntil("an inline term shows its tooltip", () -> shows(".term"), Playtest.WAIT);
            s.hover(".link");
            s.waitUntil("an inline link shows its tooltip", () -> shows(".link"), Playtest.WAIT);
            s.shot("tooltips-term");

            // Fails if the live value is frozen while shown, or showing it needs a pointer move.
            s.hover(".cast");
            s.waitUntil("the countdown shows", () -> shows(".cast"), Playtest.WAIT);
            s.run(() -> countdown[0] = Playtest.text(".countdown"));
            s.waitUntil("the countdown changes under a still pointer", () -> !Playtest.text(".countdown").equals(countdown[0]),
                    2 * Playtest.WAIT);

            // Items: a bare one is vanilla's; lines are read only while their tooltip shows.
            s.hover(".item.vanilla");
            s.waitUntil("a bare item leaves its tooltip to vanilla", () -> Playtest.document().topLayer().tooltip() == null,
                    Playtest.WAIT);
            s.expect("hidden item lines are not read", () -> lines().isEmpty());
            s.hover(".item.custom");
            s.waitUntil("an item shows its custom tooltip", () -> shows(".item.custom"), Playtest.WAIT);
            s.hover(".item.lines");
            s.waitUntil("an item's tooltip holds its vanilla lines and one more", () -> shows(".item.lines")
                    && lines().size() >= 3 && lines().get(0).equals("Diamond Sword")
                    && lines().stream().anyMatch(l -> l.startsWith("Sharpness")), Playtest.WAIT);
            s.shot("tooltips-item-lines");

            // Fails if an ancestor's tooltip beats the deepest, or a title stops being vanilla.
            s.hover(".outer.custom .inner");
            s.waitUntil("the inner box's tooltip wins over the outer one", () -> shows(".outer.custom .inner"), Playtest.WAIT);
            s.hover(".outer.titled .inner");
            s.waitUntil("a title stays a vanilla tooltip", () -> Playtest.document().topLayer().tooltip() == null, Playtest.WAIT);
            s.shot("tooltips-title");

            // Fails if a tooltip at the right edge runs off the screen instead of flipping left of the pointer.
            s.run(() -> {
                LayoutBox b = Playtest.box(".entry");
                Playtest.hoverAt(b.x() + b.width() - 2, b.y() + b.height() / 2);
            });
            s.waitUntil("an edge entry's tooltip flips left of the pointer, inside the screen", () -> {
                Element tip = Playtest.document().topLayer().tooltip();
                if (tip == null || tip.parent() != Playtest.query(".entry").get(0)) {
                    return false;
                }
                LayoutBox b = Playtest.box(".entry");
                float pointer = b.x() + b.width() - 2;
                float right = tip.box().x() + tip.box().width();
                return right <= pointer && right <= Playtest.document().root().box().width();
            }, Playtest.WAIT);
            s.shot("tooltips-edge");

            // Fails if a component's slot content does not reach its tooltip.
            s.hover(".badge");
            s.waitUntil("the badge's tooltip holds what it was given", () -> shows(".badge")
                    && Playtest.text(".badge-tip .tip-head").equals("Help from a component"), Playtest.WAIT);

            // Fails if the host's element is unknown to the template, has no painter, or takes no pointer.
            s.hover(".gauge");
            s.waitUntil("the host-made element paints and shows its tooltip", () -> shows(".gauge"), Playtest.WAIT);
            s.shot("tooltips-gauge");

            // Fails if an element in a dialog loses its tooltip to the dialog.
            s.click(".open");
            s.waitUntil("the dialog opens", () -> Playtest.query("dialog:open").size() == 1, Playtest.WAIT);
            s.hover(".close");
            s.waitUntil("a button in the dialog shows its tooltip", () -> shows(".close"), Playtest.WAIT);
            s.shot("tooltips-dialog");
            s.click(".close");
            s.waitUntil("the dialog closes", () -> Playtest.query("dialog:open").isEmpty(), Playtest.WAIT);

            // Fails if the list's thumb cannot be dragged, or the drag stops at the list's edge.
            s.dragScrollbar(".list", 200);
            s.waitUntil("dragging the thumb past the bottom scrolls the list to its end", () -> {
                Element list = Playtest.query(".list").get(0);
                return list.maxScrollTop() > 0 && list.scrollTop() == list.maxScrollTop();
            }, Playtest.WAIT);
            s.shot("tooltips-scrolled");
            s.expect("the page still reports no errors", () -> Playtest.document().diagnostics().errorCount() == 0);
            s.run(() -> Minecraft.getInstance().setScreen(null));
        });
    }

    /** Whether the shown tooltip is the one written in the first match of {@code selector}. */
    private static boolean shows(String selector) {
        Element tip = Playtest.document().topLayer().tooltip();
        return tip != null && tip.parent() == Playtest.query(selector).get(0);
    }

    /** The lines in the {@code <mc-tooltip-lines>} of the page, one element each. */
    private static List<String> lines() {
        return Playtest.query("mc-tooltip-lines").get(0).children().stream()
                .filter(n -> n instanceof Element).map(n -> ((Element) n).textContent()).toList();
    }
}
