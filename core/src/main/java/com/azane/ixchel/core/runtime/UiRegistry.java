package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.api.HostApi;
import com.azane.ixchel.core.binding.JavaComponent;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.html.DeclaredElements;
import com.azane.ixchel.core.html.HtmlElements;
import com.azane.ixchel.core.library.Library;
import com.azane.ixchel.core.view.UiElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the core, a library layer or a host adds to the documents that see it: elements, the Java
 * half of component files and, for a library only, roots of {@value Library#PREFIX} files.
 * {@link UiHosting#of} combines the registries one document sees. Fill a registry during setup and
 * leave it once a hosting is built from it. Css, effect and view files are not registered: links
 * read them by id.
 *
 * <pre>{@code
 * UiRegistry techmod = UiRegistry.host("techmod")
 *         .element(new GaugeElement())                 // @UiElement(tag = "techmod-gauge", ...)
 *         .component(EnergyModel.class, EnergyModel::new);
 * }</pre>
 */
@HostApi
public final class UiRegistry {
    /** The engine's elements and the core's files: {@code @ixchel/x} is {@code ui/x} next to {@link Library}. */
    public static final UiRegistry CORE = library().root("", Library.class, "ui/").elements(HtmlElements.ALL);

    private final String host;
    private final List<Library.Root> roots = new ArrayList<>();
    private final List<Element> elements = new ArrayList<>();
    private final Map<String, JavaComponent> components = new LinkedHashMap<>();

    /**
     * An element: its type and the object implementing it ({@code null} for none, as for one declared
     * on a class the host builds itself).
     */
    record Element(ElementType type, Object implementation) {
    }

    private UiRegistry(String host) {
        this.host = host;
    }

    /** A library layer's registry, which may define {@code ix-} and {@code mc-} names. */
    public static UiRegistry library() {
        return new UiRegistry(null);
    }

    /** The registry of the host mod {@code modId}, whose documents alone see it. */
    public static UiRegistry host(String modId) {
        if (modId == null || modId.isEmpty()) {
            throw new IllegalArgumentException("a host registry needs its mod id");
        }
        return new UiRegistry(modId);
    }

    /** The host's mod id; {@code null} for a library. */
    public String host() {
        return host;
    }

    /**
     * Maps the {@value Library#PREFIX} paths starting with {@code prefix} to {@code dir} next to {@code anchor}.
     *
     * @see Library.Root
     */
    public UiRegistry root(String prefix, Class<?> anchor, String dir) {
        library("a library root");
        roots.add(new Library.Root(prefix, anchor, dir));
        return this;
    }

    /** Engine elements, with any facets; only the core registers them. */
    public UiRegistry elements(List<ElementType> types) {
        library("engine elements");
        for (ElementType t : types) {
            elements.add(new Element(t, null));
        }
        return this;
    }

    /**
     * The element {@code implementation}'s class declares with {@link UiElement}; the host layer
     * looks the implementation up by tag, such as the painter of a drawn element.
     *
     * @throws IllegalArgumentException if the declaration is invalid or a host's tag is a library name
     */
    public UiRegistry element(Object implementation) {
        return element(implementation.getClass(), implementation);
    }

    /**
     * The element {@code declaring} declares with {@link UiElement}, implemented by
     * {@code implementation}, or by nothing when {@code null}.
     *
     * @throws IllegalArgumentException if the declaration is invalid or a host's tag is a library name
     */
    public UiRegistry element(Class<?> declaring, Object implementation) {
        ElementType type = DeclaredElements.declare(declaring);
        if (host != null && isLibraryName(type.tag())) {
            throw new IllegalArgumentException("<" + type.tag() + "> of " + declaring.getName()
                    + ": ix- and mc- names belong to the library");
        }
        elements.add(new Element(type, implementation));
        return this;
    }

    /**
     * The Java half of the component file {@code type} names with {@link UiView}: a library's under
     * {@value Library#PREFIX}, a host's outside it.
     *
     * @param factory builds the model of each instance; {@code null} for a lint, which builds none
     * @throws IllegalArgumentException if {@code type} does not name exactly one file, the file is
     *                                  on the wrong side of {@value Library#PREFIX} or already registered
     */
    public UiRegistry component(Class<? extends ViewModel> type, Supplier<? extends ViewModel> factory) {
        return component(UiView.Ids.single(type), new JavaComponent(type, factory));
    }

    /** The Java half {@code java} of the component file {@code id}; as {@link #component(Class, Supplier)}. */
    UiRegistry component(String id, JavaComponent java) {
        if (Library.isLibrary(id) != (host == null)) {
            throw new IllegalArgumentException(java.type().getName() + " for '" + id + "': "
                    + (host == null ? "a library's components are its own files under " : "a host's components are not under ")
                    + Library.PREFIX);
        }
        if (components.putIfAbsent(id, java) != null) {
            throw new IllegalArgumentException(id + " already has a Java component");
        }
        return this;
    }

    List<Library.Root> roots() {
        return roots;
    }

    List<Element> elements() {
        return elements;
    }

    Map<String, JavaComponent> components() {
        return components;
    }

    private void library(String what) {
        if (host != null) {
            throw new IllegalArgumentException(what + " is registered by a library, not by the host " + host);
        }
    }

    private static boolean isLibraryName(String name) {
        return name.startsWith("ix-") || name.startsWith("mc-");
    }
}
