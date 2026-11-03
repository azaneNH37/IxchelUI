package com.azane.ixchel.core.dom;

import java.util.ArrayList;
import java.util.List;

/**
 * The entering and exiting of one document's units: the top-level elements of an {@code <if>} or
 * {@code <for>} copy, the document's top-level elements as it loads, a {@link TopLayer} entry or the
 * element shown at the pointer. A unit's roots carry {@link Element#ENTERING} from insertion until
 * nothing in their subtrees moves, and {@link Element#EXITING} from removal until then, when the
 * unit's owner removes it; {@link Element#ABSENT} marks the first style of an entering unit, so
 * the next style transitions from it, and the whole exit. An exiting unit's roots are inert unless
 * the owner keeps them interactive. A unit inside another one's absent roots does not enter or exit
 * on its own.
 *
 * <p>An entering unit is first checked after the style that has it absent ran
 * ({@link #styled()}): it then becomes present, and after the next style its motion is watched.
 * Without any stylesheet rule for these states ({@code animated} false) units enter and exit at once.
 */
public final class Presence {
    /** The three states a unit's roots carry. */
    public static final int STATES = Element.ABSENT | Element.ENTERING | Element.EXITING;

    /**
     * Whether an element (or pseudo-element) still has a transition or an animation with finitely many
     * iterations running.
     */
    @FunctionalInterface
    public interface Motion {
        boolean moving(Element e);
    }

    private enum Phase { PRESENT, FIRST, ENTERING, EXITING }

    /** One unit, held by its owner. */
    public static final class Unit {
        private final List<Element> roots;
        private Phase phase = Phase.PRESENT;
        /** Whether a style ran since the unit's last change. */
        private boolean styled;
        private boolean inert;
        private Runnable done;

        private Unit(List<Element> roots) {
            this.roots = roots;
        }

        public boolean exiting() {
            return phase == Phase.EXITING;
        }
    }

    private final Motion motion;
    private final boolean animated;
    private final List<Unit> active = new ArrayList<>();

    /** @param animated whether any rule matches on {@link #STATES}; without one, units enter and exit at once */
    public Presence(Motion motion, boolean animated) {
        this.motion = motion;
        this.animated = animated;
    }

    /** Units that enter and exit at once. */
    public static Presence instant() {
        return new Presence(e -> false, false);
    }

    /** A new unit of {@code roots}, just inserted. */
    public Unit enter(List<Element> roots) {
        Unit u = new Unit(roots);
        if (animated && !roots.isEmpty() && !insideAbsent(roots.get(0))) {
            u.phase = Phase.FIRST;
            set(u, Element.ABSENT | Element.ENTERING);
            active.add(u);
        }
        return u;
    }

    /**
     * The document's top-level elements, under {@code root}, as it loads: one unit, returned, and
     * the units already entering inside it become present.
     */
    public Unit enterDocument(Element root) {
        List<Element> tops = new ArrayList<>();
        for (Node n : root.children()) {
            if (n instanceof Element e && !e.hasState(Element.ENTERING)) {
                tops.add(e);
            }
        }
        Unit document = enter(tops);
        for (int i = active.size() - 1; i >= 0; i--) {
            Unit u = active.get(i);
            if (u != document && insideAbsent(u.roots.get(0))) {
                active.remove(i);
                settle(u);
            }
        }
        return document;
    }

    /**
     * Enters {@code u} again: an exiting one from where its exit got to ({@link #reenter}), a present
     * one from absent, as when it was inserted.
     */
    public void enterAgain(Unit u) {
        if (u.phase == Phase.EXITING) {
            reenter(u);
        } else if (u.phase == Phase.PRESENT && animated && !u.roots.isEmpty() && !insideAbsent(u.roots.get(0))) {
            u.phase = Phase.FIRST;
            u.styled = false;
            for (Element e : u.roots) {
                e.setState(Element.EXITING, false);
            }
            set(u, Element.ABSENT | Element.ENTERING);
            active.add(u);
        }
    }

    /**
     * Leaves a {@code u} whose exit ended styled as it ended, for {@link #enterAgain}: restyled it would
     * transition back to present, and the next entry would start from there.
     */
    public void rest(Unit u) {
        set(u, Element.ABSENT | Element.EXITING);
    }

    /**
     * Starts {@code u}'s exit; {@code done} runs once it ended, which is at once when nothing can
     * animate it. Nothing when it already exits.
     *
     * @param inert whether its roots stop taking input
     */
    public void exit(Unit u, boolean inert, Runnable done) {
        if (u.phase == Phase.EXITING) {
            return;
        }
        active.remove(u);
        if (!animated || u.roots.isEmpty() || insideAbsent(u.roots.get(0))) {
            settle(u);
            done.run();
            return;
        }
        u.phase = Phase.EXITING;
        u.styled = false;
        u.inert = inert;
        u.done = done;
        for (Element e : u.roots) {
            e.setState(Element.ENTERING, false);
            e.setState(Element.ABSENT, true);
            e.setState(Element.EXITING, true);
            e.setInert(inert);
        }
        active.add(u);
    }

    /** Takes an exiting {@code u} back: it enters from where its exit got to. Nothing otherwise. */
    public void reenter(Unit u) {
        if (u.phase != Phase.EXITING) {
            return;
        }
        u.phase = Phase.ENTERING;
        u.styled = false;
        u.inert = false;
        u.done = null;
        for (Element e : u.roots) {
            e.setState(Element.EXITING, false);
            e.setState(Element.ABSENT, false);
            e.setState(Element.ENTERING, true);
            e.setInert(false);
        }
    }

    /** Ends {@code u} at once, present, without running its exit's {@code done}. */
    public void cut(Unit u) {
        active.remove(u);
        settle(u);
    }

    /** A style ran: units changed before it can be checked. */
    public void styled() {
        for (Unit u : active) {
            u.styled = true;
        }
    }

    /**
     * Makes units past their first style present and ends those whose subtrees stopped moving,
     * running exits' {@code done}; whether anything changed.
     */
    public boolean update() {
        boolean changed = false;
        for (Unit u : List.copyOf(active)) {
            if (!u.styled) {
                continue;
            }
            if (u.phase == Phase.FIRST) {
                u.phase = Phase.ENTERING;
                u.styled = false;
                for (Element e : u.roots) {
                    e.setState(Element.ABSENT, false);
                }
                changed = true;
            } else if (!moving(u.roots)) {
                active.remove(u);
                Runnable done = u.phase == Phase.EXITING ? u.done : null;
                settle(u);
                if (done != null) {
                    done.run();
                }
                changed = true;
            }
        }
        return changed;
    }

    private void settle(Unit u) {
        u.phase = Phase.PRESENT;
        u.done = null;
        for (Element e : u.roots) {
            e.setState(STATES, false);
            if (u.inert) {
                e.setInert(false);
            }
        }
        u.inert = false;
    }

    private static void set(Unit u, int states) {
        for (Element e : u.roots) {
            e.setState(states, true);
        }
    }

    private static boolean insideAbsent(Element e) {
        for (Element a = e.parent(); a != null; a = a.parent()) {
            if (a.hasState(Element.ABSENT)) {
                return true;
            }
        }
        return false;
    }

    private boolean moving(List<Element> elements) {
        for (Element e : elements) {
            if (moving(e)) {
                return true;
            }
        }
        return false;
    }

    private boolean moving(Element e) {
        if (motion.moving(e) || e.before() != null && motion.moving(e.before())
                || e.after() != null && motion.moving(e.after())) {
            return true;
        }
        for (Node n : e.children()) {
            if (n instanceof Element c && moving(c)) {
                return true;
            }
        }
        return false;
    }
}
