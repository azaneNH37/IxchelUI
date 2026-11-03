package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Easing;
import com.azane.ixchel.core.css.property.PropertyCode;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.syntax.ParsedSheet;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.diag.Diagnostics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A compiled {@code @keyframes} rule, split per property into {@link Track}s. Keyframes at the same
 * offset merge, later declarations winning; a keyframe's {@code animation-timing-function} is the
 * easing from it to the property's next keyframe. {@code animation-*}, {@code transition-*},
 * {@code display} and custom properties are reported and ignored.
 */
public record Keyframes(String name, List<Track> tracks) {

    /**
     * One property's keyframes, offsets ascending and distinct. A value is as {@link Declarations.Longhand}
     * holds it: the property's value type, a {@link Declarations.Wide} or a {@link Declarations.Pending}.
     * An easing is {@code null} where the keyframe has none and the animation's applies.
     */
    public record Track(PropertyDef<?> property, float[] offsets, Object[] values, Easing[] easings) {
    }

    static Keyframes compile(ParsedSheet.Keyframes source, PropertyRegistry registry, Diagnostics diag) {
        TreeMap<Float, Map<PropertyDef<?>, Object>> frames = new TreeMap<>();
        Map<Float, Easing> easings = new HashMap<>();
        for (ParsedSheet.Keyframe frame : source.frames()) {
            List<Rule.Declaration> kept = new ArrayList<>(frame.declarations().size());
            Easing easing = null;
            for (Rule.Declaration d : frame.declarations()) {
                String n = d.name();
                if (n.equals(CssProperties.ANIMATION_TIMING_FUNCTION.name())) {
                    Object v = Declarations.value(CssProperties.ANIMATION_TIMING_FUNCTION, d.value(), d.pos(), diag);
                    if (v instanceof List<?> list) {
                        easing = (Easing) list.get(0);
                    } else if (v != null) {
                        diag.report(PropertyCode.NOT_ANIMATABLE, d.pos(),
                                "a keyframe's animation-timing-function takes an easing, without var() or a CSS-wide keyword");
                    }
                } else if (n.startsWith("--") && registry.byName(n) == null || n.equals("display") || n.equals("animation") || n.startsWith("animation-")
                        || n.equals("transition") || n.startsWith("transition-")) {
                    diag.report(PropertyCode.NOT_ANIMATABLE, d.pos(), "'" + n + "' cannot be animated with @keyframes");
                } else {
                    kept.add(d);
                }
            }
            Declarations.Compiled compiled = Declarations.compile(kept, registry, diag);
            for (float offset : frame.offsets()) {
                Map<PropertyDef<?>, Object> values = frames.computeIfAbsent(offset, k -> new HashMap<>());
                for (Declarations.Longhand l : compiled.longhands()) {
                    values.put(l.property(), l.value());
                }
                if (easing != null) {
                    easings.put(offset, easing);
                }
            }
        }
        TreeMap<Integer, PropertyDef<?>> properties = new TreeMap<>();
        for (Map<PropertyDef<?>, Object> values : frames.values()) {
            for (PropertyDef<?> def : values.keySet()) {
                properties.put(def.id(), def);
            }
        }
        List<Track> tracks = new ArrayList<>(properties.size());
        for (PropertyDef<?> def : properties.values()) {
            int n = 0;
            for (Map<PropertyDef<?>, Object> values : frames.values()) {
                if (values.containsKey(def)) {
                    n++;
                }
            }
            float[] offsets = new float[n];
            Object[] values = new Object[n];
            Easing[] trackEasings = new Easing[n];
            int k = 0;
            for (Map.Entry<Float, Map<PropertyDef<?>, Object>> f : frames.entrySet()) {
                Object v = f.getValue().get(def);
                if (v != null) {
                    offsets[k] = f.getKey();
                    values[k] = v;
                    trackEasings[k] = easings.get(f.getKey());
                    k++;
                }
            }
            tracks.add(new Track(def, offsets, values, trackEasings));
        }
        return new Keyframes(source.name(), List.copyOf(tracks));
    }
}
