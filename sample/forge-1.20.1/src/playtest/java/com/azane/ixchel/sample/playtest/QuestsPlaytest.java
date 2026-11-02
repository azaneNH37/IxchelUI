package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.mc.FrameTimer;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.quests.ClaimQuestPacket;
import com.azane.ixchel.sample.quests.Claimed;
import com.azane.ixchel.sample.quests.Quests;
import com.azane.ixchel.sample.quests.QuestsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@code -Pplaytest=quests}: the quest tree's canvas pans by drag and zooms by wheel through a
 * {@code transform}, quests are hit where they paint, the detail panel stays put, a claim goes through
 * the server's checks, and a chapter of 33 quests holds the frame budget.
 */
public final class QuestsPlaytest {
    /** About twice the p95 measured on the development machine: 3.9-4.1 ms still, 3.4-3.8 ms panning. */
    private static final double STILL_BUDGET_US = 8000;
    private static final double PANNING_BUDGET_US = 8000;
    private static final int TIMED_TICKS = 60;

    private static float[] before;

    private QuestsPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("quests", s -> {
            Quests.Chapter first = Quests.chapters().get(0);
            int lines = first.quests().stream().mapToInt(q -> q.deps().size()).sum();
            s.server(Claimed::clear);
            s.run(QuestsScreen::open);
            s.waitUntil("the first chapter's 33 quests and " + lines + " lines are built", () ->
                    Playtest.query(".node").size() == 33 && Playtest.query(".edge").size() == lines, Playtest.WAIT);
            s.expect("only the five quests without prerequisites are unlocked",
                    () -> Playtest.query(".node").size() - Playtest.query(".node.locked").size() == 5);
            s.shot("quests-start");

            // Fails if the canvas does not follow the drag, or a quest is not hit where it now paints.
            s.run(() -> before = EventDispatcher.paintedCenter(node(0)));
            s.drag(".node", 40, 24);
            s.waitUntil("a drag moves the canvas with the pointer", () -> moved(node(0), before, 40, 24), Playtest.WAIT);
            s.expect("the moved quest is hit where it paints", () -> hitsItself(node(0)));
            s.expect("a drag that starts on a quest opens nothing", () -> Playtest.query(".detail").isEmpty());

            // Fails if the wheel does not scale the canvas, or the zoom is not held to 0.5-2.
            s.scroll(".view", 1);
            s.waitUntil("a wheel notch zooms in by 1.25", () -> near(gap(), 38 * 1.25f), Playtest.WAIT);
            for (int i = 0; i < 6; i++) {
                s.scroll(".view", 1);
            }
            s.waitUntil("zoom stops at 2", () -> near(gap(), 76), Playtest.WAIT);
            s.expect("the zoomed quest is hit where it paints", () -> hitsItself(node(6)));
            for (int i = 0; i < 10; i++) {
                s.scroll(".view", -1);
            }
            s.waitUntil("zoom stops at 0.5", () -> near(gap(), 19), Playtest.WAIT);
            s.shot("quests-zoomed-out");
            s.scroll(".view", 1);
            s.scroll(".view", 1);
            s.scroll(".view", 1);

            // Fails if the quest's <ix-tooltip> does not show for its icon (the screenshot shows it unscaled).
            s.hover(".node");
            s.waitUntil("the hovered quest shows its tooltip", () -> {
                Element tip = Playtest.document().topLayer().tooltip();
                return tip != null && tip.parent().parent() == node(0);
            }, Playtest.WAIT);
            s.shot("quests-tooltip");
            s.click(".node");
            s.waitUntil("a click opens the quest's details", () -> Playtest.query(".detail").size() == 1
                    && Playtest.text(".detail .name").equals(first.quests().get(0).title()), Playtest.WAIT);
            s.expect("the detail panel does not move with the canvas", () -> {
                Element detail = Playtest.query(".detail").get(0);
                float[] p = EventDispatcher.paintedCenter(detail);
                return near(p[0], detail.box().x() + detail.box().width() / 2) && hitsInside(detail, p);
            });
            s.expect("claim is disabled without the items", () -> Playtest.query(".claim:disabled").size() == 1);
            // The screenshot shows the framed panel, its shadow, its gold rule and the selected quest's glow.
            s.expect("the detail panel has its ::before rule", () -> Playtest.query(".detail").get(0).before() != null);
            s.shot("quests-detail");

            // Fails if a ready quest cannot be claimed, the reward is not given, or its followers stay locked.
            s.server(p -> p.getInventory().add(new ItemStack(first.quests().get(0).item(), first.quests().get(0).count())));
            s.waitUntil("holding the items makes the quest ready", () -> Playtest.query(".node.ready").size() == 1
                    && Playtest.query(".claim:disabled").isEmpty(), Playtest.WAIT);
            s.click(".claim");
            s.waitUntil("the claim comes back from the server", () -> Playtest.query(".node.done").size() == 1
                    && Playtest.query(".edge.done").size() > 0, Playtest.WAIT);
            s.waitUntilServer("the reward is given", p -> p.getInventory().countItem(Items.EMERALD) == 1, Playtest.WAIT);
            s.expect("a quest after it unlocks", () -> Playtest.query(".node").size() - Playtest.query(".node.locked").size() == 6);
            s.shot("quests-claimed");

            // Fails if the server honors a claim of a locked quest the client never offered.
            s.run(() -> Network.sendToServer(new ClaimQuestPacket(first.quests().get(6).id())));
            s.pause(10);
            s.waitUntilServer("a locked quest's claim is refused", p -> !Claimed.view(p, 0).has(first.quests().get(6).id())
                    && p.getInventory().countItem(Items.EMERALD) == 1, 1);

            s.click(".close");
            s.waitUntil("close hides the details", () -> Playtest.query(".detail").isEmpty(), Playtest.WAIT);
            s.clickNth(".chapter", 1);
            s.waitUntil("the second chapter replaces the canvas", () -> Playtest.query(".chapter.selected").size() == 1
                    && Playtest.text(".chapter.selected span").equals(Quests.chapters().get(1).title())
                    && Playtest.query(".node.done").isEmpty(), Playtest.WAIT);

            // Standing still nothing changes; panning changes one transform per tick (no layout).
            s.run(() -> FrameTimer.screen(4096));
            s.pause(100);
            s.run(() -> FrameTimer.expect(s, "quest tree standing still", STILL_BUDGET_US));
            s.run(() -> FrameTimer.screen(4096));
            for (int i = 0; i < TIMED_TICKS; i++) {
                // Over 3px of travel each: a drag that ends on a quest must not open it.
                s.drag(".view", i < TIMED_TICKS / 2 ? 4 : -4, 2);
            }
            s.run(() -> FrameTimer.expect(s, "quest tree panning every tick", PANNING_BUDGET_US));
            s.shot("quests-second-chapter");
            s.run(() -> Minecraft.getInstance().setScreen(null));
        });
    }

    private static Element node(int index) {
        return Playtest.query(".node").get(index);
    }

    private static boolean moved(Element e, float[] from, float dx, float dy) {
        float[] p = EventDispatcher.paintedCenter(e);
        return near(p[0] - from[0], dx) && near(p[1] - from[1], dy);
    }

    /** The painted distance between the first two quests of a column, 38px apart on the canvas. */
    private static float gap() {
        return EventDispatcher.paintedCenter(node(1))[1] - EventDispatcher.paintedCenter(node(0))[1];
    }

    private static boolean hitsItself(Element e) {
        return hitsInside(e, EventDispatcher.paintedCenter(e));
    }

    private static boolean hitsInside(Element e, float[] p) {
        for (Element hit = Playtest.document().events().hitTest(p[0], p[1]); hit != null; hit = hit.parent()) {
            if (hit == e) {
                return true;
            }
        }
        return false;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.6f;
    }
}
