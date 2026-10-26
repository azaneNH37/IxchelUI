package com.azane.ixchel.core.binding;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.BindAttribute;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.template.TagRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The {@code bind:*} attributes of one document, resolved against its view model.
 *
 * <pre>{@code
 * <span bind:text="loaded"></span>        <!-- value as text; the element must be empty -->
 * <div bind:class:empty="isEmpty"></div>  <!-- Boolean toggles a class -->
 * <img bind:src="icon"/>                  <!-- value as a plain attribute -->
 * <div bind:class:full="!isEmpty"></div>  <!-- '!' negates a Boolean -->
 * }</pre>
 *
 * Expressions are a cell name with an optional {@code !}; anything more goes into a {@link Derived}.
 */
public final class Bindings {
    /** Converts a bound value to text: {@code null} is empty, everything else {@link String#valueOf}. */
    public static final Function<Object, String> PLAIN_TEXT = v -> v == null ? "" : String.valueOf(v);

    private final ViewModel model;
    private final List<Binding> bindings;

    private Bindings(ViewModel model, List<Binding> bindings) {
        this.model = model;
        this.bindings = bindings;
    }

    /**
     * Resolves every binding under {@code root} and writes the initial values into the DOM. Problems
     * go to {@code diag} with the attribute's position; a template with bindings needs a model.
     *
     * @param model  the view model, or {@code null} for a template without bindings
     * @param textOf converts bound values for {@code bind:text} and attribute bindings
     */
    public static Bindings compile(Element root, ViewModel model, Function<Object, String> textOf,
                                   TagRegistry tags, Diagnostics diag) {
        Map<String, Field> fields = model == null ? Map.of() : cellFields(model.getClass());
        List<Binding> out = new ArrayList<>();
        collect(root, model, fields, textOf, tags, diag, out);
        Bindings b = new Bindings(model, out);
        b.update();
        return b;
    }

    /**
     * Polls the model's sources and writes changed values into the DOM. Returns whether anything was
     * written; when no source or state changed, no cell is read.
     */
    public boolean update() {
        if (model == null || !model.poll()) {
            return false;
        }
        boolean wrote = false;
        for (int i = 0, n = bindings.size(); i < n; i++) {
            wrote |= bindings.get(i).update();
        }
        return wrote;
    }

    private static void collect(Element e, ViewModel model, Map<String, Field> fields,
                                Function<Object, String> textOf, TagRegistry tags, Diagnostics diag,
                                List<Binding> out) {
        for (BindAttribute a : e.bindings()) {
            Binding b = resolve(e, a, model, fields, textOf, tags, diag);
            if (b != null) {
                out.add(b);
            }
        }
        for (Node n : e.children()) {
            if (n instanceof Element child) {
                collect(child, model, fields, textOf, tags, diag, out);
            }
        }
    }

    private static Binding resolve(Element e, BindAttribute a, ViewModel model, Map<String, Field> fields,
                                   Function<Object, String> textOf, TagRegistry tags, Diagnostics diag) {
        if (model == null) {
            diag.error(a.namePos(), "'" + a.name() + "' needs a view model, but this document has none");
            return null;
        }
        String target = a.name().substring("bind:".length());
        String expr = a.value().trim();
        boolean negate = expr.startsWith("!");
        String name = negate ? expr.substring(1).trim() : expr;
        if (name.indexOf('.') >= 0) {
            diag.error(a.valuePos(), "binding paths ('" + name + "') are not supported yet; derive the value instead");
            return null;
        }
        if (!isIdentifier(name)) {
            diag.error(a.valuePos(), "expected a view model field name, got '" + a.value() + "'");
            return null;
        }
        Field field = fields.get(name);
        if (field == null) {
            diag.error(a.valuePos(), "'" + name + "' is not a Value or Derived field of "
                    + model.getClass().getSimpleName());
            return null;
        }
        Cell<?> cell;
        try {
            cell = (Cell<?>) field.get(model);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
        if (cell == null) {
            diag.error(a.valuePos(), "field '" + name + "' of " + model.getClass().getSimpleName() + " is null");
            return null;
        }
        boolean isClass = target.startsWith("class:");
        if ((negate || isClass) && valueType(field) != Boolean.class) {
            diag.error(a.valuePos(), (negate ? "'!'" : "'" + a.name() + "'") + " needs a Boolean, but '"
                    + name + "' is " + field.getGenericType().getTypeName());
            return null;
        }
        if (target.equals("text")) {
            if (!e.children().isEmpty()) {
                diag.error(a.namePos(), "bind:text replaces the content of <" + e.tag() + ">; leave it empty");
                return null;
            }
            TextNode text = new TextNode("", a.valuePos());
            e.appendChild(text);
            return new TextBinding(cell, negate, a, diag, textOf, text);
        }
        if (isClass) {
            String cls = target.substring("class:".length());
            if (!isClassName(cls)) {
                diag.error(a.namePos(), "'" + cls + "' is not a valid class name");
                return null;
            }
            return new ClassBinding(cell, negate, a, diag, e, cls);
        }
        switch (target) {
            case "class" -> diag.error(a.namePos(), "bind:class takes a class name: bind:class:<name>");
            case "style" -> diag.error(a.namePos(), "bind:style is not supported; toggle a class instead");
            default -> {
                if (target.indexOf(':') >= 0 || !tags.isAllowedAttribute(e.tag(), target)) {
                    diag.error(a.namePos(), "unknown binding target '" + a.name() + "' on <" + e.tag() + ">");
                } else {
                    return new AttributeBinding(cell, negate, a, diag, textOf, e, target);
                }
            }
        }
        return null;
    }

    private static Map<String, Field> cellFields(Class<?> type) {
        Map<String, Field> out = new HashMap<>();
        for (Class<?> c = type; c != ViewModel.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && Cell.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    out.putIfAbsent(f.getName(), f);
                }
            }
        }
        return out;
    }

    private static Type valueType(Field f) {
        return f.getGenericType() instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : null;
    }

    private static boolean isIdentifier(String s) {
        if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isClassName(String s) {
        if (s.isEmpty() || Character.isDigit(s.charAt(0))) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c == '-' || c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    /** One bound attribute; writes the DOM only when the value differs from the last write. */
    private abstract static class Binding {
        private final Cell<?> cell;
        private final boolean negate;
        private final BindAttribute attribute;
        private final Diagnostics diag;
        private boolean failing;

        Binding(Cell<?> cell, boolean negate, BindAttribute attribute, Diagnostics diag) {
            this.cell = cell;
            this.negate = negate;
            this.attribute = attribute;
            this.diag = diag;
        }

        /** A failing computation keeps the last written value and is reported once until it recovers. */
        final boolean update() {
            Object v;
            try {
                v = cell.get();
            } catch (RuntimeException ex) {
                if (!failing) {
                    failing = true;
                    SourcePos pos = attribute.valuePos();
                    diag.error(pos, "'" + attribute.value() + "' threw " + ex);
                }
                return false;
            }
            failing = false;
            if (negate) {
                v = !Boolean.TRUE.equals(v);
            }
            return write(v);
        }

        abstract boolean write(Object value);
    }

    private static final class TextBinding extends Binding {
        private final Function<Object, String> textOf;
        private final TextNode text;

        TextBinding(Cell<?> cell, boolean negate, BindAttribute a, Diagnostics diag,
                    Function<Object, String> textOf, TextNode text) {
            super(cell, negate, a, diag);
            this.textOf = textOf;
            this.text = text;
        }

        @Override
        boolean write(Object value) {
            String s = textOf.apply(value);
            if (s.equals(text.text())) {
                return false;
            }
            text.setText(s);
            return true;
        }
    }

    private static final class ClassBinding extends Binding {
        private final Element element;
        private final String cls;

        ClassBinding(Cell<?> cell, boolean negate, BindAttribute a, Diagnostics diag, Element element, String cls) {
            super(cell, negate, a, diag);
            this.element = element;
            this.cls = cls;
        }

        @Override
        boolean write(Object value) {
            boolean on = Boolean.TRUE.equals(value);
            if (on == element.hasClass(cls)) {
                return false;
            }
            if (on) {
                element.addClass(cls);
            } else {
                element.removeClass(cls);
            }
            return true;
        }
    }

    private static final class AttributeBinding extends Binding {
        private final Function<Object, String> textOf;
        private final Element element;
        private final String name;

        AttributeBinding(Cell<?> cell, boolean negate, BindAttribute a, Diagnostics diag,
                         Function<Object, String> textOf, Element element, String name) {
            super(cell, negate, a, diag);
            this.textOf = textOf;
            this.element = element;
            this.name = name;
        }

        @Override
        boolean write(Object value) {
            String s = textOf.apply(value);
            if (Objects.equals(s, element.attribute(name))) {
                return false;
            }
            element.setAttribute(name, s);
            return true;
        }
    }
}
