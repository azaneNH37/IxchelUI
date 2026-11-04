package com.azane.ixchel.core.effect;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.property.Syntax;
import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.ParsedSheet;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.template.TemplateLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The effects one document links, by name, and the property registry its styles compile against:
 * the global properties at their ids, then each param registered as a typed, animatable custom
 * property {@code --<effect>-<param>} with its {@code inherits} and {@code initial-value}, as CSS
 * {@code @property} does. Plain custom properties stay untyped.
 */
public final class Effects {
    private final Map<String, Effect> byName;
    private final Map<Effect, PropertyDef<?>[]> params;
    private final PropertyRegistry registry;

    private Effects(Map<String, Effect> byName, Map<Effect, PropertyDef<?>[]> params, PropertyRegistry registry) {
        this.byName = byName;
        this.params = params;
        this.registry = registry;
    }

    /**
     * Parses the linked files and registers their params over {@code base}. A file that cannot be
     * used and a second effect of the same name are reported and left out.
     */
    public static Effects load(List<TemplateLoader.EffectFile> files, PropertyRegistry base, Diagnostics diag) {
        Map<String, Effect> byName = new LinkedHashMap<>();
        Map<Effect, PropertyDef<?>[]> params = new IdentityHashMap<>();
        PropertyRegistry registry = base.extend();
        for (TemplateLoader.EffectFile f : files) {
            Effect e = EffectParser.parse(f.text(), f.id(), diag);
            if (e == null) {
                continue;
            }
            Effect other = byName.get(e.name());
            if (other != null) {
                diag.report(EffectCode.DUPLICATE_EFFECT, f.link(), "effect '" + e.name() + "' (" + e.id()
                        + ") has the name of " + other.id() + ", which the document links too");
                continue;
            }
            PropertyDef<?>[] defs = new PropertyDef<?>[e.params().size()];
            boolean clash = false;
            for (int i = 0; i < defs.length && !clash; i++) {
                Effect.Param p = e.params().get(i);
                String property = e.property(p);
                if (registry.byName(property) != null) {
                    diag.report(EffectCode.DUPLICATE_PARAM, p.pos(), property + " is also a param of another linked effect");
                    clash = true;
                } else {
                    defs[i] = register(registry, property, p.type().syntax(), p.initial(), p.inherits());
                }
            }
            if (!clash) {
                byName.put(e.name(), e);
                params.put(e, defs);
            }
        }
        registry.freeze();
        return new Effects(Collections.unmodifiableMap(byName), params, registry);
    }

    @SuppressWarnings("unchecked")
    private static <T> PropertyDef<T> register(PropertyRegistry r, String name, Syntax<T> syntax, Object initial, boolean inherits) {
        return r.register(name, syntax, (T) initial, inherits, false, true);
    }

    /** The registry the document's styles, transitions, keyframes and bindings compile against. */
    public PropertyRegistry registry() {
        return registry;
    }

    /** The linked effect named {@code name}, {@code null} if there is none. */
    public Effect byName(String name) {
        return byName.get(name);
    }

    /** The linked effects in link order. */
    public Collection<Effect> all() {
        return byName.values();
    }

    /** {@code e}'s param values on an element of style {@code cs}, packed as {@link ParamType#pack} writes them. */
    public float[] values(Effect e, ComputedStyle cs) {
        PropertyDef<?>[] defs = params.get(e);
        float[] out = new float[e.valueCount()];
        int at = 0;
        for (int i = 0; i < defs.length; i++) {
            ParamType t = e.params().get(i).type();
            t.pack(cs.get(defs[i]), out, at);
            at += t.components();
        }
        return out;
    }

    /**
     * Reports the uses of effects in {@code sheet} that cannot work: a {@code paint()} or
     * {@code -ix-paint()} of an effect the document does not link or of the other kind, and a custom
     * property named after a linked effect that is not one of its params. Uses through {@code var()}
     * are reported when painted.
     */
    public void check(ParsedSheet sheet, Diagnostics diag) {
        for (Rule r : sheet.rules()) {
            check(r.declarations(), diag);
        }
        for (ParsedSheet.Keyframes k : sheet.keyframes()) {
            for (ParsedSheet.Keyframe f : k.frames()) {
                check(f.declarations(), diag);
            }
        }
    }

    private void check(List<Rule.Declaration> declarations, Diagnostics diag) {
        for (Rule.Declaration d : declarations) {
            switch (d.name()) {
                case "background-image" -> uses(d.value(), "paint", Effect.Kind.BOX, diag);
                case "filter" -> uses(d.value(), "-ix-paint", Effect.Kind.LAYER, diag);
                default -> {
                    if (d.name().startsWith("--") && registry.byName(d.name()) == null) {
                        for (Effect e : byName.values()) {
                            if (d.name().startsWith("--" + e.name() + "-")) {
                                diag.report(EffectCode.UNKNOWN_PARAM, d.pos(), "effect '" + e.name() + "' has no param '"
                                        + d.name().substring(e.name().length() + 3) + "'" + paramList(e));
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void uses(List<ComponentValue> value, String function, Effect.Kind kind, Diagnostics diag) {
        for (ComponentValue v : value) {
            if (!(v instanceof ComponentValue.Function f) || !f.name().toLowerCase(Locale.ROOT).equals(function)) {
                continue;
            }
            String name = null;
            for (ComponentValue a : f.args()) {
                if (a instanceof Token t && t.is(Token.Kind.IDENT)) {
                    name = t.value();
                }
            }
            if (name == null) {
                continue; // the property's syntax reports the malformed call
            }
            Effect e = byName.get(name);
            if (e == null) {
                diag.report(EffectCode.UNKNOWN_EFFECT, f.pos(), unknown(name));
            } else if (e.kind() != kind) {
                diag.report(EffectCode.WRONG_KIND, f.pos(), wrongKind(e));
            }
        }
    }

    /** The message for a use of {@code name}, which the document does not link. */
    public String unknown(String name) {
        return "no effect '" + name + "' is linked; add <link rel=\"effect\" href=\"…/" + name + "\"/>"
                + (byName.isEmpty() ? "" : " (linked: " + String.join(", ", byName.keySet()) + ")");
    }

    /** The message for a use of {@code e} where the other kind belongs. */
    public static String wrongKind(Effect e) {
        return e.kind() == Effect.Kind.BOX
                ? "'" + e.name() + "' is a box effect; use it as background-image: paint(" + e.name() + ")"
                : "'" + e.name() + "' is a layer effect; use it as filter: -ix-paint(" + e.name() + ")";
    }

    private static String paramList(Effect e) {
        if (e.params().isEmpty()) {
            return "; it has none";
        }
        StringBuilder sb = new StringBuilder("; it has ");
        for (int i = 0; i < e.params().size(); i++) {
            sb.append(i == 0 ? "" : ", ").append(e.property(e.params().get(i)));
        }
        return sb.toString();
    }
}
