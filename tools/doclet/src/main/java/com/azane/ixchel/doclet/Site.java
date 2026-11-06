package com.azane.ixchel.doclet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Where the site has a page or a section for an element. A module's pages sit under
 * {@code /java-api/<module>/}, a type's at its package path below the module's root package; only
 * {@code @HostApi} types and their public or protected members have one.
 */
final class Site {
    static final String HOST_API = "com.azane.ixchel.core.api.HostApi";

    private final Map<String, String> roots;
    private final Signatures signatures;
    private final Elements elements;

    /** @param roots each documented module's root package to the module's name */
    Site(Map<String, String> roots, Signatures signatures, Elements elements) {
        this.roots = roots;
        this.signatures = signatures;
        this.elements = elements;
    }

    /** The page of {@code top} from the docs root, {@code java-api/ixchel-core/binding/ViewModel.md}; {@code null} if it has none. */
    String page(TypeElement top) {
        if (!hostApi(top)) {
            return null;
        }
        String pkg = elements.getPackageOf(top).getQualifiedName().toString();
        for (Map.Entry<String, String> r : roots.entrySet()) {
            String root = r.getKey();
            if (pkg.equals(root) || pkg.startsWith(root + ".")) {
                String rel = pkg.substring(root.length()).replace('.', '/');
                return "java-api/" + r.getValue() + (rel.isEmpty() ? "" : rel) + "/" + top.getSimpleName() + ".md";
            }
        }
        return null;
    }

    /** The link to {@code target} from the page of {@code from}, {@code null} if the site has no section for it. */
    String link(Element target, TypeElement from) {
        TypeElement top = top(target);
        if (top == null || !shown(target)) {
            return null;
        }
        String page = page(top);
        if (page == null) {
            return null;
        }
        String anchor = target == top ? "" : "#" + id(target);
        return top.equals(from) ? (anchor.isEmpty() ? "#" : anchor) : "/" + page + anchor;
    }

    /** Whether {@code e} and every type around it are public or protected, as a section needs. */
    static boolean shown(Element e) {
        for (Element x = e; x != null && !(x instanceof PackageElement); x = x.getEnclosingElement()) {
            boolean member = x.getKind() != ElementKind.PARAMETER && x.getKind() != ElementKind.TYPE_PARAMETER;
            if (member && !x.getModifiers().contains(Modifier.PUBLIC) && !x.getModifiers().contains(Modifier.PROTECTED)
                    && !(x.getEnclosingElement() != null && x.getEnclosingElement().getKind().isInterface())) {
                return false;
            }
        }
        return true;
    }

    static TypeElement top(Element e) {
        TypeElement top = null;
        for (Element x = e; x != null && !(x instanceof PackageElement); x = x.getEnclosingElement()) {
            if (x instanceof TypeElement t) {
                top = t;
            }
        }
        return top;
    }

    static boolean hostApi(TypeElement t) {
        return t.getAnnotationMirrors().stream().anyMatch(a ->
                ((TypeElement) a.getAnnotationType().asElement()).getQualifiedName().contentEquals(HOST_API));
    }

    /**
     * The anchor of a member or nested type on its top-level type's page: the nested types around it
     * and its name, an executable's with its erased parameter types; every run of characters but
     * letters, digits and {@code _} as one dash ({@code Rect.post(Runnable)} is {@code Rect-post-Runnable-}).
     */
    String id(Element e) {
        Deque<String> path = new ArrayDeque<>();
        for (Element x = e.getEnclosingElement(); x instanceof TypeElement t && t.getEnclosingElement() instanceof TypeElement;
             x = x.getEnclosingElement()) {
            path.push(t.getSimpleName().toString());
        }
        String own = e instanceof ExecutableElement x ? signatures.erasedName(x) : e.getSimpleName().toString();
        path.add(own);
        return String.join(".", path).replaceAll("[^A-Za-z0-9_]+", "-");
    }
}
