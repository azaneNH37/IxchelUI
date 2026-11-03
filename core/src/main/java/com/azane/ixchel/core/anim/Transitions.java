package com.azane.ixchel.core.anim;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Easing;
import com.azane.ixchel.core.css.property.Keywords.Display;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.property.Shorthand;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.style.StyleEngine;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * CSS transitions (CSS Transitions Level 1, section 3): starting, replacing, reversing and
 * cancelling on style change, then overriding the cascaded value while running. Elements that
 * are or become {@code display: none} do not transition. Values that {@code Syntax#interpolate}
 * cannot interpolate change at once. A property that {@link Animations} animates in the previous
 * or the new style does not transition.
 *
 * <p>Transitions started before the first {@link #advance} (an element entering as the document
 * loads) start at that first time.
 */
public final class Transitions implements StyleEngine.Hook {
    private static final class Running {
        final Object from;
        final Object to;
        /** The value a transition back from {@code to} must reach to count as reversing this one. */
        final Object reversingStart;
        final float shortening;
        long start;
        final long duration;
        final Easing easing;

        Running(Object from, Object to, Object reversingStart, float shortening, long start, long duration, Easing easing) {
            this.from = from;
            this.to = to;
            this.reversingStart = reversingStart;
            this.shortening = shortening;
            this.start = start;
            this.duration = duration;
            this.easing = easing;
        }

        boolean done(long now) {
            return now >= start + duration;
        }

        float progress(long now) {
            if (done(now)) {
                return 1;
            }
            return now <= start ? 0 : easing.apply((float) (now - start) / duration);
        }

        @SuppressWarnings("unchecked")
        <T> T valueAt(PropertyDef<T> def, long now) {
            return def.syntax().interpolate((T) from, (T) to, progress(now));
        }
    }

    private final PropertyRegistry registry;
    private final Element root;
    private final Animations animations;
    private final Map<Element, Running[]> running = new IdentityHashMap<>();
    private final Map<List<String>, int[]> listIndex = new IdentityHashMap<>();
    private long now;
    private boolean advanced;

    public Transitions(PropertyRegistry registry, Element root, Animations animations) {
        this.registry = registry;
        this.root = root;
        this.animations = animations;
    }

    /**
     * Moves the clock to {@code nanos} and marks every element with a running transition for
     * restyle, dropping elements no longer in the document. Returns whether any are running.
     */
    public boolean advance(long nanos) {
        if (!advanced) {
            advanced = true;
            for (Running[] runs : running.values()) {
                for (Running r : runs) {
                    if (r != null) {
                        r.start += nanos - now;
                    }
                }
            }
        }
        now = nanos;
        for (Iterator<Element> it = running.keySet().iterator(); it.hasNext(); ) {
            Element e = it.next();
            if (inDocument(e)) {
                e.markStyleDirty();
            } else {
                it.remove();
            }
        }
        return !running.isEmpty();
    }

    /** Whether {@code e} has a transition that has not reached its end by the {@link #advance} time. */
    public boolean running(Element e) {
        Running[] runs = running.get(e);
        if (runs == null) {
            return false;
        }
        for (Running r : runs) {
            if (r != null && !r.done(now)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void computed(Element e, ComputedStyle previous, ComputedStyle next, StyleEngine.Cascade cascade) {
        Running[] runs = running.get(e);
        if (previous == null || previous.get(CssProperties.DISPLAY) == Display.NONE
                || next.get(CssProperties.DISPLAY) == Display.NONE) {
            if (runs != null) {
                running.remove(e);
            }
            return;
        }
        List<Float> durations = next.get(CssProperties.TRANSITION_DURATION);
        List<Float> delays = next.get(CssProperties.TRANSITION_DELAY);
        if (runs == null && idle(durations, delays)) {
            return;
        }
        List<Easing> easings = next.get(CssProperties.TRANSITION_TIMING_FUNCTION);
        int[] index = listIndex.computeIfAbsent(next.get(CssProperties.TRANSITION_PROPERTY), this::index);
        boolean any = false;
        for (int id = 0; id < index.length; id++) {
            Running r = runs == null ? null : runs[id];
            int i = index[id];
            if (i >= 0 && animations.animates(e, id)) {
                i = -1;
            }
            if (r == null && i < 0) {
                continue;
            }
            PropertyDef<?> def = registry.byId(id);
            Running updated = update(def, r, previous.get(def), next.get(def),
                    i < 0 ? 0 : durations.get(i % durations.size()),
                    i < 0 ? 0 : delays.get(i % delays.size()),
                    i < 0 ? null : easings.get(i % easings.size()),
                    i >= 0);
            if (updated != null) {
                if (runs == null) {
                    runs = new Running[index.length];
                    running.put(e, runs);
                }
                apply(next, def, updated);
                any = true;
            }
            if (runs != null) {
                runs[id] = updated;
            }
        }
        if (!any && runs != null) {
            running.remove(e);
        }
    }

    /** The transition for {@code def} after this style change; {@code null} when none runs. */
    private Running update(PropertyDef<?> def, Running r, Object before, Object after,
                           float duration, float delay, Easing easing, boolean listed) {
        if (!listed) {
            return null;
        }
        if (r != null && r.done(now)) {
            before = r.to;
            r = null;
        }
        boolean enabled = Math.max(duration, 0) + delay > 0;
        if (r == null) {
            if (!enabled || before.equals(after) || interpolate(def, before, after, 0) == null) {
                return null;
            }
            return start(before, after, before, 1, duration, delay, easing);
        }
        if (r.to.equals(after)) {
            return r;
        }
        Object current = r.valueAt(def, now);
        if (!enabled || current.equals(after) || interpolate(def, current, after, 0) == null) {
            return null;
        }
        if (r.reversingStart.equals(after)) {
            float factor = Math.max(0, Math.min(1, Math.abs(r.progress(now) * r.shortening + 1 - r.shortening)));
            return start(current, after, r.to, factor, duration * factor, delay < 0 ? delay * factor : delay, easing);
        }
        return start(current, after, current, 1, duration, delay, easing);
    }

    private Running start(Object from, Object to, Object reversingStart, float shortening,
                          float duration, float delay, Easing easing) {
        return new Running(from, to, reversingStart, shortening, now + nanos(delay), nanos(Math.max(duration, 0)), easing);
    }

    /** Seconds parsed as floats ({@code 100ms} is 0.1f) to whole microseconds, so a 100ms transition ends at 100ms. */
    private static long nanos(float seconds) {
        return Math.round(seconds * 1e6) * 1000;
    }

    @SuppressWarnings("unchecked")
    private static <T> T interpolate(PropertyDef<T> def, Object from, Object to, float p) {
        return def.syntax().interpolate((T) from, (T) to, p);
    }

    private <T> void apply(ComputedStyle next, PropertyDef<T> def, Running r) {
        next.set(def, r.valueAt(def, now));
    }

    private static boolean idle(List<Float> durations, List<Float> delays) {
        for (float d : durations) {
            if (d > 0) {
                return false;
            }
        }
        for (float d : delays) {
            if (d > 0) {
                return false;
            }
        }
        return true;
    }

    /** For each property id, its position in {@code transition-property}; the last match wins, {@code -1} if none. */
    private int[] index(List<String> names) {
        int[] out = new int[registry.size()];
        Arrays.fill(out, -1);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (name.equals("all")) {
                for (PropertyDef<?> def : registry.all()) {
                    if (def.animatable()) {
                        out[def.id()] = i;
                    }
                }
                continue;
            }
            PropertyDef<?> def = registry.byName(name);
            if (def != null) {
                out[def.id()] = i;
            } else {
                Shorthand s = registry.shorthand(name);
                for (PropertyDef<?> d : s.longhands()) {
                    out[d.id()] = i;
                }
            }
        }
        return out;
    }

    private boolean inDocument(Element e) {
        Element a = e;
        while (a.parent() != null) {
            a = a.parent();
        }
        return a == root;
    }
}
