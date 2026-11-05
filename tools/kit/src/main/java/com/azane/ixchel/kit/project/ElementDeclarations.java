package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.view.UiElement;
import com.azane.ixchel.core.view.TagRegistry;

/**
 * The tags views may use: the HTML subset plus every element a compiled class declares with
 * {@link UiElement}, the mc layer's on the classpath and the project's own. Without either it is
 * plain HTML.
 */
public record ElementDeclarations(TagRegistry tags) {
    /**
     * Reads the declarations of {@link Project#elementClasses()}.
     *
     * @throws IllegalArgumentException if a declaration is invalid or two declare the same tag
     */
    public static ElementDeclarations load(Project project) {
        TagRegistry.Builder all = com.azane.ixchel.core.html.HtmlElements.TAGS.extend();
        for (Class<?> c : project.elementClasses()) {
            all.add(com.azane.ixchel.core.html.DeclaredElements.declare(c));
        }
        return new ElementDeclarations(all.build());
    }
}
