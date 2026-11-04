package com.azane.ixchel.core.effect;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.ParsedSheet;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The effects one css file links, by the alias it gives each: {@code @link "x.ixeffect" as Alias;},
 * or the file name in PascalCase without {@code as}. An alias holds only in the file that links it.
 * {@link #rewrite} turns the aliases a file writes into the internal names the document registers,
 * which are built from the effect's file id: {@code paint(Glow)} names the effect by its id, and
 * {@code --Glow-size} becomes {@link Effect#property(String, String)}. A custom property starting
 * with an upper-case letter is always an effect param.
 */
public final class Aliases {
    /** A file that links no effect. */
    public static final Aliases NONE = new Aliases(Map.of());

    private static final Pattern ALIAS = Pattern.compile("[A-Z][A-Za-z0-9]*");

    private final Map<String, String> byAlias;

    private Aliases(Map<String, String> byAlias) {
        this.byAlias = byAlias;
    }

    /** The alias of the effect {@code id} without {@code as}: its file name in PascalCase, {@code glow-ring} → {@code GlowRing}. */
    public static String defaultAlias(String id) {
        String name = id.substring(Math.max(id.lastIndexOf('/'), id.lastIndexOf(':')) + 1);
        if (name.endsWith(Effect.EXTENSION)) {
            name = name.substring(0, name.length() - Effect.EXTENSION.length());
        }
        StringBuilder sb = new StringBuilder(name.length());
        boolean upper = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '-') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    /** The effect ids by alias, in link order. */
    public Map<String, String> byAlias() {
        return byAlias;
    }

    /** The alias this file gives the effect {@code id}, {@code null} if it does not link it. */
    public String aliasOf(String id) {
        for (Map.Entry<String, String> e : byAlias.entrySet()) {
            if (e.getValue().equals(id)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Whether {@code name} is written as an effect param: {@code --} and an upper-case letter. */
    public static boolean isParamName(String name) {
        return name.length() > 2 && name.startsWith("--") && Character.isUpperCase(name.charAt(2));
    }

    /**
     * The internal name of the param property {@code name}, {@code --Alias-param}; {@code name} itself
     * when it is not written as an effect param, {@code null} when its alias is not linked here.
     */
    public String property(String name) {
        if (!isParamName(name)) {
            return name;
        }
        int dash = name.indexOf('-', 2);
        String id = byAlias.get(dash < 0 ? name.substring(2) : name.substring(2, dash));
        return id == null ? null : Effect.property(id, dash < 0 ? "" : name.substring(dash + 1));
    }

    /** The message for {@code alias}, which this file does not link. */
    public String unknown(String alias) {
        return "'" + alias + "' is not an effect this file links" + (byAlias.isEmpty() ? ""
                : " (linked: " + String.join(", ", byAlias.keySet()) + ")");
    }

    /** The message for the param property {@code name}, whose alias this file does not link. */
    public String unknownProperty(String name) {
        return unknown(alias(name));
    }

    /** {@code sheet} with the aliases it writes as internal names; unknown ones are reported and kept. */
    public ParsedSheet rewrite(ParsedSheet sheet, Diagnostics diag) {
        List<Rule> rules = new ArrayList<>(sheet.rules().size());
        for (Rule r : sheet.rules()) {
            rules.add(new Rule(r.prelude(), rewrite(r.declarations(), diag), r.pos()));
        }
        List<ParsedSheet.Keyframes> keyframes = new ArrayList<>(sheet.keyframes().size());
        for (ParsedSheet.Keyframes k : sheet.keyframes()) {
            List<ParsedSheet.Keyframe> frames = new ArrayList<>(k.frames().size());
            for (ParsedSheet.Keyframe f : k.frames()) {
                frames.add(new ParsedSheet.Keyframe(f.offsets(), rewrite(f.declarations(), diag), f.pos()));
            }
            keyframes.add(new ParsedSheet.Keyframes(k.name(), frames, k.pos()));
        }
        return new ParsedSheet(sheet.kind(), sheet.links(), rules, keyframes);
    }

    /** {@code declarations} with the aliases they write as internal names; unknown ones are reported and kept. */
    public List<Rule.Declaration> rewrite(List<Rule.Declaration> declarations, Diagnostics diag) {
        List<Rule.Declaration> out = new ArrayList<>(declarations.size());
        for (Rule.Declaration d : declarations) {
            String name = property(d.name());
            if (name == null) {
                diag.report(EffectCode.UNKNOWN_ALIAS, d.pos(), unknownProperty(d.name()));
                name = d.name();
            }
            out.add(new Rule.Declaration(name, value(d.value(), diag), d.important(), d.pos()));
        }
        return out;
    }

    /**
     * {@code value} with the aliases it writes as internal names: the effect of {@code paint()} and
     * {@code -ix-paint()}, and param properties ({@code var()}, {@code transition}); unknown ones are
     * reported and kept.
     */
    public List<ComponentValue> value(List<ComponentValue> value, Diagnostics diag) {
        List<ComponentValue> out = new ArrayList<>(value.size());
        for (ComponentValue v : value) {
            if (v instanceof Token t) {
                out.add(t.is(Token.Kind.IDENT) && isParamName(t.value()) ? param(t, diag) : t);
            } else if (v instanceof ComponentValue.Function f) {
                out.add(new ComponentValue.Function(f.name(), isPaint(f.name()) ? effect(f.args(), diag)
                        : value(f.args(), diag), f.pos()));
            } else if (v instanceof ComponentValue.Block b) {
                out.add(new ComponentValue.Block(b.open(), value(b.contents(), diag), b.pos()));
            }
        }
        return out;
    }

    private Token param(Token t, Diagnostics diag) {
        String name = property(t.value());
        if (name == null) {
            diag.report(EffectCode.UNKNOWN_ALIAS, t.pos(), unknownProperty(t.value()));
            return t;
        }
        return new Token(t.kind(), name, t.number(), t.unit(), t.pos());
    }

    private List<ComponentValue> effect(List<ComponentValue> args, Diagnostics diag) {
        List<ComponentValue> out = new ArrayList<>(args.size());
        for (ComponentValue a : args) {
            if (a instanceof Token t && t.is(Token.Kind.IDENT)) {
                String id = byAlias.get(t.value());
                if (id == null) {
                    boolean lower = !Character.isUpperCase(t.value().charAt(0));
                    diag.report(EffectCode.UNKNOWN_ALIAS, t.pos(), unknown(t.value()), lower
                            ? "effect aliases are PascalCase, as @link gives them: " + defaultAlias(t.value()) : null);
                    out.add(t);
                } else {
                    out.add(new Token(t.kind(), id, t.number(), t.unit(), t.pos()));
                }
            } else {
                out.add(a);
            }
        }
        return out;
    }

    private static boolean isPaint(String function) {
        String f = function.toLowerCase(Locale.ROOT);
        return f.equals("paint") || f.equals("-ix-paint");
    }

    private static String alias(String param) {
        int dash = param.indexOf('-', 2);
        return dash < 0 ? param.substring(2) : param.substring(2, dash);
    }

    /** Collects the effect links of one css file. */
    public static final class Builder {
        private final Map<String, String> byAlias = new LinkedHashMap<>();

        /**
         * Adds the effect {@code id} linked at {@code at} as {@code alias}, {@code null} for its
         * {@linkplain #defaultAlias default}; reports an alias that is not PascalCase or that names
         * another effect already.
         */
        public Builder add(String alias, String id, String href, SourcePos at, Diagnostics diag) {
            if (alias != null && !ALIAS.matcher(alias).matches()) {
                diag.report(EffectCode.INVALID_ALIAS, at, "effect alias '" + alias + "' is not PascalCase",
                        "write it as " + defaultAlias(alias.toLowerCase(Locale.ROOT)));
                return this;
            }
            String name = alias == null ? defaultAlias(id) : alias;
            String other = byAlias.putIfAbsent(name, id);
            if (other != null && !other.equals(id)) {
                diag.report(EffectCode.DUPLICATE_ALIAS, at, "'" + href + "' gets the alias " + name + ", which "
                        + other + " has already", "rename one: @link \"" + href + "\" as OtherName;");
            }
            return this;
        }

        public Aliases build() {
            return byAlias.isEmpty() ? NONE : new Aliases(Collections.unmodifiableMap(new LinkedHashMap<>(byAlias)));
        }
    }
}
