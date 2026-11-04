package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.library.JavaComponent;
import com.azane.ixchel.core.library.Library;

import java.util.HashMap;
import java.util.Map;

/**
 * What a host offers a document beyond its files. A linter gives the same declarations without
 * factories: it checks against them and builds nothing.
 *
 * @param library    the library's files, the tags known without a link and its Java components
 * @param components the Java half of the host's component files, by resolved file id
 *                   ({@code "ns:ui/energy.html"})
 */
public record UiHosting(Library library, Map<String, JavaComponent> components) {
    /** The core library and no Java components of the host. */
    public static final UiHosting NONE = new UiHosting(Library.CORE, Map.of());

    /** This hosting for a document whose components come from {@code componentFiles}, file ids by component name. */
    Bindings.Hosted forBindings(Map<String, String> componentFiles) {
        Map<String, Bindings.Hosted.Model> models = new HashMap<>();
        componentFiles.forEach((name, file) -> {
            JavaComponent c = Library.isLibrary(file) ? library.components().get(file) : components.get(file);
            if (c != null) {
                models.put(name, new Bindings.Hosted.Model(c.type(), c.factory()));
            }
        });
        return new Bindings.Hosted(models);
    }
}
