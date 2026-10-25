package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.selector.Selector;
import com.azane.ixchel.core.css.selector.SelectorParser;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.diag.Diagnostics;

import java.util.ArrayList;
import java.util.List;

/** Parsed rules with validated declarations; one entry per selector of a selector list. */
public final class StyleSheet {
    /** {@code order} is the rule's position within this sheet. */
    public record StyleRule(Selector selector, List<Declarations.Compiled> declarations, int order) {
    }

    private final List<StyleRule> rules;

    private StyleSheet(List<StyleRule> rules) {
        this.rules = rules;
    }

    public static StyleSheet compile(List<Rule> rules, PropertyRegistry registry, Diagnostics diag) {
        List<StyleRule> out = new ArrayList<>();
        int order = 0;
        for (Rule r : rules) {
            List<Selector> selectors = SelectorParser.parseList(r.prelude(), r.pos(), diag);
            List<Declarations.Compiled> decls = Declarations.compile(r.declarations(), registry, diag);
            for (Selector s : selectors) {
                out.add(new StyleRule(s, decls, order));
            }
            order++;
        }
        return new StyleSheet(List.copyOf(out));
    }

    public List<StyleRule> rules() {
        return rules;
    }
}
