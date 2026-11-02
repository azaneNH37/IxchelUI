package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.template.NativeElement;
import com.azane.ixchel.core.template.TagRegistry;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * The tags templates may use: the HTML subset plus every element a compiled class declares with
 * {@link NativeElement}, the mc layer's on the classpath and the project's own. Without either it is
 * plain HTML.
 *
 * @param containerOnly the tags only container screens accept
 */
public record ElementDeclarations(TagRegistry tags, Set<String> containerOnly) {
    public ElementDeclarations {
        containerOnly = Collections.unmodifiableSet(new TreeSet<>(containerOnly));
    }

    /**
     * Reads the declarations of {@link Project#elementClasses()}.
     *
     * @throws IllegalArgumentException if a declaration is invalid or two declare the same tag
     */
    public static ElementDeclarations load(Project project) {
        TagRegistry.Builder all = com.azane.ixchel.core.html.HtmlElements.TAGS.extend();
        Set<String> container = new TreeSet<>();
        for (Class<?> c : project.elementClasses()) {
            all.add(com.azane.ixchel.core.html.NativeElements.declare(c));
            NativeElement d = c.getAnnotation(NativeElement.class);
            if (d.containerOnly()) {
                container.add(d.tag());
            }
        }
        return new ElementDeclarations(all.build(), container);
    }
}
