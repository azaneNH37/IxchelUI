package com.azane.ixchel.core.view;

import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.dom.Node;

import java.util.List;
import java.util.Set;

/**
 * A component parsed from its file's {@code <component name params events>} root; checked for structure
 * only, its names and types are checked per instance by the binding layer.
 *
 * @param pos    the {@code <component>} tag
 * @param body   the view's content, never mounted itself: each instance copies it
 * @param slots  the names of its {@code <slot>} elements, {@code ""} for the default slot
 */
public record ComponentDef(String name, SourcePos pos, List<Param> params, List<String> events, List<Node> body,
                           Set<String> slots) {
    public ComponentDef {
        params = List.copyOf(params);
        events = List.copyOf(events);
        body = List.copyOf(body);
        slots = Set.copyOf(slots);
    }

    /**
     * A declared parameter.
     *
     * @param fallback the default value expression as a directive named after the parameter, or
     *                 {@code null} when the parameter is required
     */
    public record Param(String name, SourcePos pos, Directive fallback) {
    }

    public boolean hasParam(String name) {
        for (Param p : params) {
            if (p.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
