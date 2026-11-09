package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.sample.arcane.ArcaneTree;
import net.minecraft.client.Minecraft;

/**
 * {@code -Pplaytest=arcane}: the tree opens with the core learned, its four arts open and the rest
 * sealed; learning an art spends its cost and opens its children; an art costing more than the
 * points left cannot be learned; a sealed art cannot be picked. {@code -Precord=arcane} records it.
 */
public final class ArcanePlaytest {
    /** Indexes in ArcaneModel.ARTS, the order of the nodes. */
    private static final int EMBER = 1;
    private static final int TIDE = 2;
    private static final int GALE = 4;
    private static final int INFERNO = 5;
    private static final int FROST = 7;
    private static final int MENDING = 8;
    private static final int BLINK = 11;

    private ArcanePlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("arcane", s -> {
            setUp(s);
            s.expect("the tree reports no errors", () -> Playtest.document().diagnostics().errorCount() == 0);
            s.expect("the core learned, four arts open, eight sealed, seven points", () ->
                    count(".node.learned") == 1 && count(".node.open") == 4 && count(".node.sealed") == 8
                            && Playtest.text(".points span").equals("7"));
            s.take("arcane", ArcanePlaytest::showcase);
            tearDown(s);
        });
    }

    /** Setup, showcase and teardown, for the reel. */
    static void scene(Playtest s) {
        setUp(s);
        showcase(s);
        tearDown(s);
    }

    private static void setUp(Playtest s) {
        s.run(ArcaneTree::open);
        s.waitUntil("the tree opens", Playtest::isOpen, Playtest.WAIT);
    }

    private static void showcase(Playtest t) {
        t.hold(1500);
        // Fails if a sealed art can be picked.
        t.clickNth(".node .hit", BLINK);
        t.expect("a sealed art is not picked", () -> count(".node.ix-selected") == 0);
        learn(t, EMBER, "Ember", "6");
        t.expect("Ember opens Inferno and Meteor", () -> count(".node.open") == 5 && count(".node.sealed") == 6);
        t.hold(1600);
        learn(t, INFERNO, "Inferno", "4");
        t.hold(900);
        learn(t, TIDE, "Tide", "3");
        t.hold(1600);
        learn(t, FROST, "Frost", "1");
        t.hold(900);
        // Fails if the learn button ignores the points left.
        t.clickNth(".node .hit", MENDING);
        t.expect("Mending costs 2 with 1 left: it cannot be learned", () ->
                Playtest.text(".detail .name").equals("Mending") && Playtest.query(".learn").get(0).hasState(Element.DISABLED));
        t.hold(1200);
        learn(t, GALE, "Gale", "0");
        t.expect("Gale opens Blink and Storm", () -> count(".node.open") == 5 && count(".node.learned") == 6);
        t.hold(2200);
    }

    private static void tearDown(Playtest s) {
        s.run(() -> Minecraft.getInstance().setScreen(null));
    }

    /** Picks the art at {@code index} and learns it, leaving {@code points}. */
    private static void learn(Playtest t, int index, String name, String points) {
        t.clickNth(".node .hit", index);
        t.waitUntil("picking " + name + " shows it, learnable", () -> Playtest.text(".detail .name").equals(name)
                && !Playtest.query(".learn").get(0).hasState(Element.DISABLED), Playtest.WAIT);
        t.hold(500);
        t.click(".learn");
        t.waitUntil("learning " + name + " leaves " + points + " points", () ->
                Playtest.text(".points span").equals(points) && Playtest.text(".cost.learned").equals("Learned"), Playtest.WAIT);
    }

    private static int count(String selector) {
        return Playtest.query(selector).size();
    }
}
