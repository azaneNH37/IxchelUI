package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.selector.Selector;
import com.azane.ixchel.core.css.selector.SelectorParser;
import com.azane.ixchel.core.css.syntax.ParsedSheet;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.diag.Diagnostics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed rules with validated declarations, one entry per selector of a selector list, and the
 * sheet's {@code @keyframes} by name, the last of a name winning.
 */
public final class StyleSheet {
    /** {@code order} is the rule's position within this sheet. */
    public record StyleRule(Selector selector, Declarations.Compiled declarations, int order) {
    }

    private final List<StyleRule> rules;
    private final Map<String, Keyframes> keyframes;

    private StyleSheet(List<StyleRule> rules, Map<String, Keyframes> keyframes) {
        this.rules = rules;
        this.keyframes = keyframes;
    }

    public static StyleSheet compile(ParsedSheet sheet, PropertyRegistry registry, Diagnostics diag) {
        List<StyleRule> out = new ArrayList<>();
        int order = 0;
        for (Rule r : sheet.rules()) {
            List<Selector> selectors = SelectorParser.parseList(r.prelude(), r.pos(), diag);
            Declarations.Compiled decls = Declarations.compile(r.declarations(), registry, diag);
            for (Selector s : selectors) {
                out.add(new StyleRule(s, decls, order));
            }
            order++;
        }
        Map<String, Keyframes> keyframes = new LinkedHashMap<>();
        for (ParsedSheet.Keyframes k : sheet.keyframes()) {
            keyframes.put(k.name(), Keyframes.compile(k, registry, diag));
        }
        return new StyleSheet(List.copyOf(out), Map.copyOf(keyframes));
    }

    public List<StyleRule> rules() {
        return rules;
    }

    public Map<String, Keyframes> keyframes() {
        return keyframes;
    }
}
