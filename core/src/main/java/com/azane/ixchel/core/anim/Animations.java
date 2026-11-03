package com.azane.ixchel.core.anim;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Easing;
import com.azane.ixchel.core.css.property.Keywords.AnimationDirection;
import com.azane.ixchel.core.css.property.Keywords.AnimationFillMode;
import com.azane.ixchel.core.css.property.Keywords.AnimationPlayState;
import com.azane.ixchel.core.css.property.Keywords.Display;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.style.Keyframes;
import com.azane.ixchel.core.style.StyleEngine;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * CSS animations (CSS Animations Level 1): {@code animation-name} starts one animation per named
 * {@code @keyframes}; an animation keeps running while its name stays in the list (matched by
 * name in order) and the element is displayed, and restarts when its name comes back. Animated
 * values override every normal declaration, inline and bound ones included, but not
 * {@code !important} ones; a later name in the list wins over an earlier one. A property missing
 * from the {@code 0%} or {@code 100%} keyframe takes the cascaded value there. Values that
 * {@code Syntax#interpolate} cannot interpolate switch halfway between two keyframes.
 *
 * <p>Time is the {@link #advance} clock: it does not stop while the game is paused. Animations
 * started before the first {@link #advance} (when the document loads) start at that first time.
 * When an animation with finitely many iterations ends, {@code animationend} goes to its element,
 * or to the element of its pseudo-element, through {@link #dispatchEnded}.
 */
public final class Animations implements StyleEngine.Hook {
    private static final class Run {
        final String name;
        final Keyframes keyframes;
        long start;
        /** The current time while paused, else {@code -1}. */
        long hold = -1;
        boolean ended;
        /** Whether the latest style gives it infinitely many iterations. */
        boolean infinite;
        /** When its active phase ends, as of the latest style: start plus delay plus active duration. */
        long end;

        Run(String name, Keyframes keyframes, long start) {
            this.name = name;
            this.keyframes = keyframes;
            this.start = start;
        }
    }

    /** An element's animations, by position in its {@code animation-name}, and what they animated. */
    private static final class State {
        Run[] runs;
        /** Ids of the properties animated in the element's latest style, and in the one before. */
        BitSet animated = new BitSet();
        BitSet wasAnimated = new BitSet();
        /** Whether a run's value changes with time. */
        boolean live;
    }

    private static final Run[] NO_RUNS = new Run[0];

    private final Element root;
    private final Map<Element, State> states = new IdentityHashMap<>();
    private final List<Element> ended = new ArrayList<>();
    private long now;
    private boolean advanced;

    public Animations(Element root) {
        this.root = root;
    }

    /**
     * Moves the clock to {@code nanos} and marks every element with an animation that changes with
     * time for restyle, dropping elements no longer in the document. Returns whether any are.
     */
    public boolean advance(long nanos) {
        if (!advanced) {
            advanced = true;
            for (State s : states.values()) {
                for (Run r : s.runs) {
                    if (r != null) {
                        r.start += nanos - now;
                        r.end += nanos - now;
                    }
                }
            }
        }
        now = nanos;
        boolean any = false;
        for (Iterator<Map.Entry<Element, State>> it = states.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Element, State> entry = it.next();
            if (!inDocument(entry.getKey())) {
                it.remove();
            } else if (entry.getValue().live) {
                entry.getKey().markStyleDirty();
                any = true;
            }
        }
        return any;
    }

    /** Whether {@code e}'s latest style, or the one before it, has the property {@code id} animated. */
    public boolean animates(Element e, int id) {
        State s = states.get(e);
        return s != null && (s.animated.get(id) || s.wasAnimated.get(id));
    }

    /**
     * Whether {@code e} has an animation with finitely many iterations that has not reached its end
     * by the {@link #advance} time; a paused one has not.
     */
    public boolean finishing(Element e) {
        State s = states.get(e);
        if (s == null) {
            return false;
        }
        for (Run r : s.runs) {
            if (r != null && r.keyframes != null && !r.infinite && (r.hold >= 0 || now < r.end)) {
                return true;
            }
        }
        return false;
    }

    /** Hands each element whose animation ended since the last call to {@code dispatch}, in order. */
    public void dispatchEnded(Consumer<Element> dispatch) {
        for (int i = 0; i < ended.size(); i++) {
            Element target = ended.get(i);
            if (inDocument(target)) {
                dispatch.accept(target);
            }
        }
        ended.clear();
    }

    @Override
    public void computed(Element e, ComputedStyle previous, ComputedStyle next, StyleEngine.Cascade cascade) {
        State s = states.get(e);
        List<String> names = next.get(CssProperties.ANIMATION_NAME);
        if (next.get(CssProperties.DISPLAY) == Display.NONE || none(names)) {
            if (s != null) {
                retire(e, s);
            }
            return;
        }
        if (s == null) {
            s = new State();
            s.runs = NO_RUNS;
            states.put(e, s);
        }
        BitSet swap = s.wasAnimated;
        s.wasAnimated = s.animated;
        s.animated = swap;
        swap.clear();
        s.runs = match(s.runs, names, cascade);
        List<Float> durations = next.get(CssProperties.ANIMATION_DURATION);
        List<Easing> easings = next.get(CssProperties.ANIMATION_TIMING_FUNCTION);
        List<Float> delays = next.get(CssProperties.ANIMATION_DELAY);
        List<Float> counts = next.get(CssProperties.ANIMATION_ITERATION_COUNT);
        List<AnimationDirection> directions = next.get(CssProperties.ANIMATION_DIRECTION);
        List<AnimationFillMode> fills = next.get(CssProperties.ANIMATION_FILL_MODE);
        List<AnimationPlayState> playStates = next.get(CssProperties.ANIMATION_PLAY_STATE);
        boolean live = false;
        for (int i = 0; i < s.runs.length; i++) {
            Run r = s.runs[i];
            if (r == null || r.keyframes == null) {
                continue;
            }
            boolean paused = playStates.get(i % playStates.size()) == AnimationPlayState.PAUSED;
            if (paused && r.hold < 0) {
                r.hold = now - r.start;
            } else if (!paused && r.hold >= 0) {
                r.start = now - r.hold;
                r.hold = -1;
            }
            long t = paused ? r.hold : now - r.start;
            long duration = nanos(durations.get(i % durations.size()));
            long delay = nanos(delays.get(i % delays.size()));
            float count = counts.get(i % counts.size());
            r.infinite = Float.isInfinite(count);
            AnimationFillMode fill = fills.get(i % fills.size());
            double active = duration == 0 || count == 0 ? 0 : duration * (double) count;
            r.end = r.start + delay + (r.infinite ? 0 : (long) active);
            double overall;
            boolean before = false;
            if (t < delay) {
                if (fill != AnimationFillMode.BACKWARDS && fill != AnimationFillMode.BOTH) {
                    r.ended = false;
                    live |= !paused;
                    continue;
                }
                before = true;
                overall = 0;
                r.ended = false;
                live |= !paused;
            } else if (t - delay >= active) {
                if (!r.ended) {
                    r.ended = true;
                    if (!Float.isInfinite(count)) {
                        ended.add(target(e));
                    }
                }
                if (fill != AnimationFillMode.FORWARDS && fill != AnimationFillMode.BOTH) {
                    continue;
                }
                overall = Float.isInfinite(count) ? 1 : count;
            } else {
                overall = (t - delay) / (double) duration;
                r.ended = false;
                live |= !paused;
            }
            double iteration = Math.floor(overall);
            double progress = overall - iteration;
            if (progress == 0 && overall > 0 && t - delay >= active) {
                iteration--;
                progress = 1;
            }
            AnimationDirection direction = directions.get(i % directions.size());
            boolean odd = iteration % 2 != 0;
            boolean reversed = direction == AnimationDirection.REVERSE
                    || direction == AnimationDirection.ALTERNATE && odd
                    || direction == AnimationDirection.ALTERNATE_REVERSE && !odd;
            float p = (float) (reversed ? 1 - progress : progress);
            Easing easing = easings.get(i % easings.size());
            for (Keyframes.Track track : r.keyframes.tracks()) {
                PropertyDef<?> def = track.property();
                if (!cascade.important(def)) {
                    set(next, def, sample(track, def, p, before, easing, next, cascade));
                    s.animated.set(def.id());
                }
            }
        }
        s.live = live;
        if (s.animated.isEmpty() && s.wasAnimated.isEmpty() && !live && !pending(s)) {
            states.remove(e);
        }
    }

    /** Whether an animation has not run to its end; its state is kept to keep it from restarting. */
    private static boolean pending(State s) {
        for (Run r : s.runs) {
            if (r != null) {
                return true;
            }
        }
        return false;
    }

    /** Ends {@code e}'s animations, keeping what they animated for one more style. */
    private void retire(Element e, State s) {
        if (s.animated.isEmpty()) {
            states.remove(e);
            return;
        }
        BitSet swap = s.wasAnimated;
        s.wasAnimated = s.animated;
        s.animated = swap;
        swap.clear();
        s.runs = NO_RUNS;
        s.live = false;
    }

    private static boolean none(List<String> names) {
        for (String n : names) {
            if (!n.equals("none")) {
                return false;
            }
        }
        return true;
    }

    /** The runs for {@code names}: each name takes over the first unclaimed run of that name, else starts. */
    private Run[] match(Run[] old, List<String> names, StyleEngine.Cascade cascade) {
        Run[] runs = old.length == names.size() ? null : new Run[names.size()];
        boolean same = runs == null;
        if (same) {
            for (int i = 0; i < old.length; i++) {
                if (old[i] == null ? !names.get(i).equals("none") : !old[i].name.equals(names.get(i))) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return old;
            }
            runs = new Run[names.size()];
        }
        boolean[] claimed = new boolean[old.length];
        for (int i = 0; i < runs.length; i++) {
            String name = names.get(i);
            if (name.equals("none")) {
                continue;
            }
            for (int j = 0; j < old.length; j++) {
                if (!claimed[j] && old[j] != null && old[j].name.equals(name)) {
                    claimed[j] = true;
                    runs[i] = old[j];
                    break;
                }
            }
            if (runs[i] == null) {
                runs[i] = new Run(name, cascade.keyframes(name), now);
            }
        }
        return runs;
    }

    /** {@code def}'s value at iteration progress {@code p} along {@code track}. */
    private static Object sample(Keyframes.Track track, PropertyDef<?> def, float p, boolean before, Easing easing,
                                 ComputedStyle next, StyleEngine.Cascade cascade) {
        float[] offsets = track.offsets();
        int n = offsets.length;
        Object base = next.get(def);
        float o1;
        float o2;
        Object v1;
        Object v2;
        Easing segment;
        if (p < offsets[0]) {
            o1 = 0;
            v1 = base;
            o2 = offsets[0];
            v2 = value(track, 0, def, next, cascade, base);
            segment = easing;
        } else {
            int j = n - 1;
            while (offsets[j] > p) {
                j--;
            }
            v1 = value(track, j, def, next, cascade, base);
            o1 = offsets[j];
            if (j == n - 1 && (offsets[j] >= 1 || p >= 1)) {
                return offsets[j] >= 1 ? v1 : base;
            }
            o2 = j == n - 1 ? 1 : offsets[j + 1];
            v2 = j == n - 1 ? base : value(track, j + 1, def, next, cascade, base);
            segment = track.easings()[j] != null ? track.easings()[j] : easing;
        }
        float local = (p - o1) / (o2 - o1);
        float eased = before ? local : segment.apply(local);
        Object mixed = interpolate(def, v1, v2, eased);
        return mixed != null ? mixed : eased < 0.5f ? v1 : v2;
    }

    private static Object value(Keyframes.Track track, int k, PropertyDef<?> def, ComputedStyle next,
                                StyleEngine.Cascade cascade, Object base) {
        Object v = cascade.compute(def, track.values()[k], next);
        return v != null ? v : base;
    }

    @SuppressWarnings("unchecked")
    private static <T> Object interpolate(PropertyDef<T> def, Object from, Object to, float p) {
        return def.syntax().interpolate((T) from, (T) to, p);
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(ComputedStyle next, PropertyDef<T> def, Object value) {
        next.set(def, (T) value);
    }

    /** The element an event about {@code e} goes to: its element when {@code e} is a pseudo-element. */
    private static Element target(Element e) {
        Element host = e.parent();
        return host != null && (host.before() == e || host.after() == e) ? host : e;
    }

    /** Seconds parsed as floats ({@code 100ms} is 0.1f) to whole microseconds, as {@link Transitions} does. */
    private static long nanos(float seconds) {
        return Math.round(seconds * 1e6) * 1000;
    }

    private boolean inDocument(Element e) {
        Element a = e;
        while (a.parent() != null) {
            a = a.parent();
        }
        return a == root;
    }
}
