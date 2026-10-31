package com.azane.ixchel.sample.check.scenario;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.mc.check.Checks;
import com.azane.ixchel.mc.check.Script;
import com.azane.ixchel.sample.tooltips.TooltipsScreen;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * {@code -Pcheck=tooltips}: every kind of element shows its own {@code <ix-tooltip>} under the pointer
 * (buttons, form controls, inline terms, items, a component's slot, a dialog's button), the deepest
 * element wins, a {@code title} and a bare item stay vanilla, item lines are read only while shown, a
 * live value updates in place, and a tooltip at the screen edge flips inside the screen.
 */
public final class TooltipsCheck {
    private static final String[] countdown = {""};

    private TooltipsCheck() {
    }

    public static void register(Checks checks) {
        checks.register("tooltips", s -> {
            s.run(TooltipsScreen::open);
            s.waitUntil("the page opens with its 24 list entries", () -> Script.isOpen()
                    && Script.query(".entry").size() == 24, Script.WAIT);
            s.expect("the page reports no errors", () -> Script.document().diagnostics().errorCount() == 0);

            // Fails if an element kind does not show its own tooltip.
            s.hover(".save");
            s.waitUntil("a button shows its tooltip, key hints inside", () -> shows(".save")
                    && Script.query(".save .key").size() == 2, Script.WAIT);
            s.shot("tooltips-button");
            s.hover(".name");
            s.waitUntil("an input shows its tooltip", () -> shows(".name"), Script.WAIT);
            s.hover(".mode");
            s.waitUntil("a closed select shows its tooltip", () -> shows(".mode"), Script.WAIT);
            s.hover(".term");
            s.waitUntil("an inline term shows its tooltip", () -> shows(".term"), Script.WAIT);
            s.hover(".link");
            s.waitUntil("an inline link shows its tooltip", () -> shows(".link"), Script.WAIT);
            s.shot("tooltips-term");

            // Fails if the live value is frozen while shown, or showing it needs a pointer move.
            s.hover(".cast");
            s.waitUntil("the countdown shows", () -> shows(".cast"), Script.WAIT);
            s.run(() -> countdown[0] = Script.text(".countdown"));
            s.waitUntil("the countdown changes under a still pointer", () -> !Script.text(".countdown").equals(countdown[0]),
                    2 * Script.WAIT);

            // Items: a bare one is vanilla's; lines are read only while their tooltip shows.
            s.hover(".item.vanilla");
            s.waitUntil("a bare item leaves its tooltip to vanilla", () -> Script.document().topLayer().tooltip() == null,
                    Script.WAIT);
            s.expect("hidden item lines are not read", () -> lines().isEmpty());
            s.hover(".item.custom");
            s.waitUntil("an item shows its custom tooltip", () -> shows(".item.custom"), Script.WAIT);
            s.hover(".item.lines");
            s.waitUntil("an item's tooltip holds its vanilla lines and one more", () -> shows(".item.lines")
                    && lines().size() >= 3 && lines().get(0).equals("Diamond Sword")
                    && lines().stream().anyMatch(l -> l.startsWith("Sharpness")), Script.WAIT);
            s.shot("tooltips-item-lines");

            // Fails if an ancestor's tooltip beats the deepest, or a title stops being vanilla.
            s.hover(".outer.custom .inner");
            s.waitUntil("the inner box's tooltip wins over the outer one", () -> shows(".outer.custom .inner"), Script.WAIT);
            s.hover(".outer.titled .inner");
            s.waitUntil("a title stays a vanilla tooltip", () -> Script.document().topLayer().tooltip() == null, Script.WAIT);
            s.shot("tooltips-title");

            // Fails if a tooltip at the right edge runs off the screen instead of flipping left of the pointer.
            s.run(() -> {
                LayoutBox b = Script.box(".entry");
                Script.hoverAt(b.x() + b.width() - 2, b.y() + b.height() / 2);
            });
            s.waitUntil("an edge entry's tooltip flips left of the pointer, inside the screen", () -> {
                Element tip = Script.document().topLayer().tooltip();
                if (tip == null || tip.parent() != Script.query(".entry").get(0)) {
                    return false;
                }
                LayoutBox b = Script.box(".entry");
                float pointer = b.x() + b.width() - 2;
                float right = tip.box().x() + tip.box().width();
                return right <= pointer && right <= Script.document().root().box().width();
            }, Script.WAIT);
            s.shot("tooltips-edge");

            // Fails if a component's slot content does not reach its tooltip.
            s.hover(".badge");
            s.waitUntil("the badge's tooltip holds what it was given", () -> shows(".badge")
                    && Script.text(".badge-tip .tip-head").equals("Help from a component"), Script.WAIT);

            // Fails if an element in a dialog loses its tooltip to the dialog.
            s.click(".open");
            s.waitUntil("the dialog opens", () -> Script.query("dialog:open").size() == 1, Script.WAIT);
            s.hover(".close");
            s.waitUntil("a button in the dialog shows its tooltip", () -> shows(".close"), Script.WAIT);
            s.shot("tooltips-dialog");
            s.click(".close");
            s.waitUntil("the dialog closes", () -> Script.query("dialog:open").isEmpty(), Script.WAIT);
            s.expect("the page still reports no errors", () -> Script.document().diagnostics().errorCount() == 0);
            s.run(() -> Minecraft.getInstance().setScreen(null));
        });
    }

    /** Whether the shown tooltip is the one written in the first match of {@code selector}. */
    private static boolean shows(String selector) {
        Element tip = Script.document().topLayer().tooltip();
        return tip != null && tip.parent() == Script.query(selector).get(0);
    }

    /** The lines in the {@code <mc-tooltip-lines>} of the page. */
    private static List<String> lines() {
        return Script.query("mc-tooltip-lines").get(0).children().stream()
                .map(n -> ((TextNode) n).text()).toList();
    }
}
