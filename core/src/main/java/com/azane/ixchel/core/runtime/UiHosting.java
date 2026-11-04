package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.template.TemplateLoader;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What a host offers a document beyond its files. A linter gives the same declarations without
 * factories and values: it checks against them and builds nothing.
 *
 * @param components the Java half of the host's component files, by resolved file id
 *                   ({@code "ns:ui/energy.html"})
 * @param library    the library's components, which need no link
 * @param framework  the framework's values, by {@code $} name ({@code "$menu"}); only parameter
 *                   defaults can read them
 */
public record UiHosting(Map<String, JavaComponent> components, List<LibraryComponent> library,
                        Map<String, FrameworkValue<?>> framework) {
    /** No Java components, library components or framework values. */
    public static final UiHosting NONE = new UiHosting(Map.of(), List.of(), Map.of());

    /**
     * A view model class for a component file.
     *
     * @param factory builds the model of each instance; {@code null} for a lint
     */
    public record JavaComponent(Class<? extends ViewModel> type, Supplier<? extends ViewModel> factory) {
    }

    /**
     * A component of the library: {@code <tag>} works in any file without a link. Only these may have
     * a name starting with {@code ix-} or {@code mc-}.
     *
     * @param id      the file's id, starting with {@value TemplateLoader#LIBRARY}
     * @param source  reads the component file; it may hold {@code <style>}
     * @param type    its view model class, {@code null} for a template component
     * @param factory builds the model of each instance; {@code null} for a template component or a lint
     */
    public record LibraryComponent(String tag, String id, Supplier<String> source, Class<? extends ViewModel> type,
                                   Supplier<? extends ViewModel> factory) {
        public LibraryComponent {
            if (!id.startsWith(TemplateLoader.LIBRARY)) {
                throw new IllegalArgumentException("library component id '" + id + "' must start with " + TemplateLoader.LIBRARY);
            }
        }
    }

    /**
     * A value of the framework.
     *
     * @param type  its declared type, what templates are checked against
     * @param value read each pass; {@code null} for a lint
     */
    public record FrameworkValue<T>(Type type, Supplier<? extends T> value) {
    }

    /** The library's component files, by tag. */
    Map<String, TemplateLoader.LibraryFile> libraryFiles() {
        Map<String, TemplateLoader.LibraryFile> out = new HashMap<>();
        for (LibraryComponent c : library) {
            out.put(c.tag(), new TemplateLoader.LibraryFile(c.id(), c.source()));
        }
        return out;
    }

    /** This hosting for a document whose components come from {@code componentFiles}, file ids by component name. */
    Bindings.Hosted forBindings(Map<String, String> componentFiles) {
        Map<String, Bindings.Hosted.Model> byFile = new HashMap<>();
        components.forEach((file, c) -> byFile.put(file, new Bindings.Hosted.Model(c.type(), c.factory())));
        for (LibraryComponent c : library) {
            if (c.type() != null) {
                byFile.put(c.id(), new Bindings.Hosted.Model(c.type(), c.factory()));
            }
        }
        Map<String, Bindings.Hosted.Model> models = new HashMap<>();
        componentFiles.forEach((name, file) -> {
            Bindings.Hosted.Model m = byFile.get(file);
            if (m != null) {
                models.put(name, m);
            }
        });
        Map<String, Bindings.Hosted.Framework> names = new HashMap<>();
        framework.forEach((name, f) -> names.put(name, new Bindings.Hosted.Framework(f.type(), f.value())));
        return new Bindings.Hosted(models, names);
    }
}
