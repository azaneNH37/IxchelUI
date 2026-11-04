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

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The effects one document links, by file id, and the property registry its styles compile against:
 * the global properties at their ids, then each param registered as a typed, animatable custom
 * property at its internal name ({@link Effect#property(Effect.Param)}) with its {@code inherits} and
 * {@code initial-value}, as CSS {@code @property} does. Plain custom properties stay untyped. Each css
 * file writes the effects by the aliases it links them as ({@link Aliases}).
 */
public final class Effects {
    private final Map<String, Effect> byId;
    private final Map<Effect, PropertyDef<?>[]> params;
    private final PropertyRegistry registry;

    private Effects(Map<String, Effect> byId, Map<Effect, PropertyDef<?>[]> params, PropertyRegistry registry) {
        this.byId = byId;
        this.params = params;
        this.registry = registry;
    }

    /**
     * Parses the linked files, their texts by id, and registers their params over {@code base}. A
     * file that cannot be used is reported and left out.
     */
    public static Effects load(Map<String, String> files, PropertyRegistry base, Diagnostics diag) {
        Map<String, Effect> byId = new LinkedHashMap<>();
        Map<Effect, PropertyDef<?>[]> params = new IdentityHashMap<>();
        PropertyRegistry registry = base.extend();
        for (Map.Entry<String, String> f : files.entrySet()) {
            Effect e = EffectParser.parse(f.getValue(), f.getKey(), diag);
            if (e == null) {
                continue;
            }
            PropertyDef<?>[] defs = new PropertyDef<?>[e.params().size()];
            for (int i = 0; i < defs.length; i++) {
                Effect.Param p = e.params().get(i);
                defs[i] = register(registry, e.property(p), p.type().syntax(), p.initial(), p.inherits());
            }
            byId.put(e.id(), e);
            params.put(e, defs);
        }
        registry.freeze();
        return new Effects(Collections.unmodifiableMap(byId), params, registry);
    }

    @SuppressWarnings("unchecked")
    private static <T> PropertyDef<T> register(PropertyRegistry r, String name, Syntax<T> syntax, Object initial, boolean inherits) {
        return r.register(name, syntax, (T) initial, inherits, false, true);
    }

    /** The registry the document's styles, transitions, keyframes and bindings compile against. */
    public PropertyRegistry registry() {
        return registry;
    }

    /** The linked effect of the file {@code id}, {@code null} if there is none. */
    public Effect byId(String id) {
        return byId.get(id);
    }

    /** The linked effects in link order. */
    public Collection<Effect> all() {
        return byId.values();
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
     * Reports the uses of effects in {@code sheet}, already {@linkplain Aliases#rewrite rewritten}
     * with the file's {@code aliases}, that cannot work: a {@code paint()} or {@code -ix-paint()} of
     * an effect of the other kind, a param property the effect does not have, and a lower-case custom
     * property that spells a param. Uses through {@code var()} are reported when painted.
     */
    public void check(ParsedSheet sheet, Aliases aliases, Diagnostics diag) {
        for (Rule r : sheet.rules()) {
            check(r.declarations(), aliases, diag);
        }
        for (ParsedSheet.Keyframes k : sheet.keyframes()) {
            for (ParsedSheet.Keyframe f : k.frames()) {
                check(f.declarations(), aliases, diag);
            }
        }
    }

    private void check(List<Rule.Declaration> declarations, Aliases aliases, Diagnostics diag) {
        for (Rule.Declaration d : declarations) {
            switch (d.name()) {
                case "background-image" -> uses(d.value(), "paint", Effect.Kind.BOX, aliases, diag);
                case "filter" -> uses(d.value(), "-ix-paint", Effect.Kind.LAYER, aliases, diag);
                default -> {
                    if (d.name().startsWith("--") && registry.byName(d.name()) == null) {
                        String unknown = unknownParam(d.name(), aliases);
                        if (unknown != null) {
                            diag.report(EffectCode.UNKNOWN_PARAM, d.pos(), unknown);
                        } else {
                            unmarked(d, aliases, diag);
                        }
                    }
                }
            }
        }
    }

    /**
     * The message for the internal param property {@code name} of an effect {@code aliases} links,
     * which is not one of its params; {@code null} when {@code name} is no such property.
     */
    public String unknownParam(String name, Aliases aliases) {
        for (Map.Entry<String, String> a : aliases.byAlias().entrySet()) {
            Effect e = byId.get(a.getValue());
            String prefix = Effect.property(a.getValue(), "");
            if (e != null && name.startsWith(prefix)) {
                String param = name.substring(prefix.length());
                return (param.isEmpty() ? "--" + a.getKey() + " names no param of " + a.getKey()
                        : a.getKey() + " has no param '" + param + "'") + " (" + e.id() + ")" + paramList(e, a.getKey());
            }
        }
        return null;
    }

    private void unmarked(Rule.Declaration d, Aliases aliases, Diagnostics diag) {
        if (Aliases.isParamName(d.name())) {
            return;
        }
        for (Map.Entry<String, String> a : aliases.byAlias().entrySet()) {
            Effect e = byId.get(a.getValue());
            if (e == null) {
                continue;
            }
            for (Effect.Param p : e.params()) {
                String param = "--" + a.getKey() + "-" + p.name();
                if (d.name().equalsIgnoreCase(param)) {
                    diag.report(EffectCode.UNMARKED_PARAM, d.pos(), "'" + d.name() + "' is a plain custom property, not "
                            + a.getKey() + "'s param", "write " + param);
                    return;
                }
            }
        }
    }

    private void uses(List<ComponentValue> value, String function, Effect.Kind kind, Aliases aliases, Diagnostics diag) {
        for (ComponentValue v : value) {
            if (!(v instanceof ComponentValue.Function f) || !f.name().toLowerCase(Locale.ROOT).equals(function)) {
                continue;
            }
            for (ComponentValue a : f.args()) {
                Effect e = a instanceof Token t && t.is(Token.Kind.IDENT) ? byId.get(t.value()) : null;
                if (e != null && e.kind() != kind) {
                    diag.report(EffectCode.WRONG_KIND, f.pos(), wrongKind(e, aliases.aliasOf(e.id())));
                }
            }
        }
    }

    /** The message for a use of the effect {@code id}, which the document does not link. */
    public static String unknown(String id) {
        return "no effect '" + id + "' is linked";
    }

    /** The message for a use of {@code e}, written {@code alias}, where the other kind belongs. */
    public static String wrongKind(Effect e, String alias) {
        String name = alias == null ? e.id() : alias;
        return e.kind() == Effect.Kind.BOX
                ? "'" + name + "' is a box effect; use it as background-image: paint(" + name + ")"
                : "'" + name + "' is a layer effect; use it as filter: -ix-paint(" + name + ")";
    }

    private static String paramList(Effect e, String alias) {
        if (e.params().isEmpty()) {
            return "; it has none";
        }
        StringBuilder sb = new StringBuilder("; it has ");
        for (int i = 0; i < e.params().size(); i++) {
            sb.append(i == 0 ? "" : ", ").append("--").append(alias).append('-').append(e.params().get(i).name());
        }
        return sb.toString();
    }
}
