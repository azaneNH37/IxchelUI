package com.azane.ixchel.core.binding;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.BindAttribute;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.template.TagRegistry;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The {@code bind:*} and {@code on:*} attributes of one document, resolved against its view model.
 *
 * <pre>{@code
 * <span bind:text="loaded"></span>          <!-- value as text; the element must be empty -->
 * <div bind:class:empty="isEmpty"></div>    <!-- Boolean toggles a class; '!' negates -->
 * <img bind:src="icon"/>                    <!-- value as a plain attribute -->
 * <item bind:item="gun"></item>              <!-- host object as a native element's payload -->
 * <span bind:text="selected.name"></span>   <!-- path: record components, read when the record changes -->
 * <div bind:class:busy="reload.pending"></div>  <!-- an action's pending / error -->
 * <div on:click="reload"></div>             <!-- runs an action; the event counts as handled -->
 * <div on:click="select(row)"></div>        <!-- with the value of a path as its argument -->
 * }</pre>
 *
 * Expressions are a path with an optional {@code !}; anything more goes into a {@link Derived}. Every
 * name, record component and type is checked at load.
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
     * Resolves every binding under {@code root}, registers event handlers and writes the initial
     * values into the DOM. Problems go to {@code diag} with the attribute's position; a template with
     * bindings or handlers needs a model.
     *
     * @param model  the view model, or {@code null} for a template without bindings
     * @param textOf converts bound values for {@code bind:text} and attribute bindings
     */
    public static Bindings compile(Element root, ViewModel model, Function<Object, String> textOf,
                                   TagRegistry tags, Diagnostics diag) {
        Compiler c = new Compiler(model, textOf, tags, diag);
        List<Binding> out = new ArrayList<>();
        c.collect(root, out);
        Bindings b = new Bindings(model, out);
        b.update();
        return b;
    }

    /**
     * Runs posted work, polls the model's sources and writes changed values into the DOM. Returns
     * whether anything was written; when no source or state changed, no cell is read.
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

    /** A resolved expression: the cell to read and its declared value type. */
    private record Ref(Cell<?> cell, Type type) {
    }

    private static final class Compiler {
        private final ViewModel model;
        private final Map<String, Field> fields;
        private final Function<Object, String> textOf;
        private final TagRegistry tags;
        private final Diagnostics diag;

        Compiler(ViewModel model, Function<Object, String> textOf, TagRegistry tags, Diagnostics diag) {
            this.model = model;
            this.fields = model == null ? Map.of() : modelFields(model.getClass());
            this.textOf = textOf;
            this.tags = tags;
            this.diag = diag;
        }

        void collect(Element e, List<Binding> out) {
            for (BindAttribute a : e.bindings()) {
                if (model == null) {
                    diag.error(a.namePos(), "'" + a.name() + "' needs a view model, but this document has none");
                } else if (a.name().startsWith("on:")) {
                    handler(e, a);
                } else {
                    Binding b = binding(e, a);
                    if (b != null) {
                        out.add(b);
                    }
                }
            }
            for (Node n : e.children()) {
                if (n instanceof Element child) {
                    collect(child, out);
                }
            }
        }

        private Binding binding(Element e, BindAttribute a) {
            String target = a.name().substring("bind:".length());
            String expr = a.value().trim();
            boolean negate = expr.startsWith("!");
            Ref ref = resolve(a, negate ? expr.substring(1).trim() : expr);
            if (ref == null) {
                return null;
            }
            boolean isClass = target.startsWith("class:");
            if ((negate || isClass) && !isBoolean(ref.type)) {
                diag.error(a.valuePos(), (negate ? "'!'" : "'" + a.name() + "'") + " needs a Boolean, but '"
                        + a.value().trim() + "' is " + ref.type.getTypeName());
                return null;
            }
            if (target.equals("text")) {
                if (!e.children().isEmpty()) {
                    diag.error(a.namePos(), "bind:text replaces the content of <" + e.tag() + ">; leave it empty");
                    return null;
                }
                TextNode text = new TextNode("", a.valuePos());
                e.appendChild(text);
                return new TextBinding(ref.cell, negate, a, diag, textOf, text);
            }
            if (target.equals(tags.payloadAttribute(e.tag()))) {
                return new PayloadBinding(ref.cell, negate, a, diag, e);
            }
            if (isClass) {
                String cls = target.substring("class:".length());
                if (!isClassName(cls)) {
                    diag.error(a.namePos(), "'" + cls + "' is not a valid class name");
                    return null;
                }
                return new ClassBinding(ref.cell, negate, a, diag, e, cls);
            }
            switch (target) {
                case "class" -> diag.error(a.namePos(), "bind:class takes a class name: bind:class:<name>");
                case "style" -> diag.error(a.namePos(), "bind:style is not supported; toggle a class instead");
                default -> {
                    if (target.indexOf(':') >= 0 || !tags.isAllowedAttribute(e.tag(), target)) {
                        diag.error(a.namePos(), "unknown binding target '" + a.name() + "' on <" + e.tag() + ">");
                    } else {
                        return new AttributeBinding(ref.cell, negate, a, diag, textOf, e, target);
                    }
                }
            }
            return null;
        }

        /** {@code on:<event>="action"} or {@code on:<event>="action(path)"}. */
        private void handler(Element e, BindAttribute a) {
            String eventName = a.name().substring("on:".length());
            UiEvent.Type type = UiEvent.Type.byTemplateName(eventName);
            if (type == null) {
                diag.error(a.namePos(), "unknown event '" + eventName + "'; supported: " + eventNames());
                return;
            }
            String expr = a.value().trim();
            int open = expr.indexOf('(');
            String name = open < 0 ? expr : expr.substring(0, open).trim();
            String argExpr = null;
            if (open >= 0) {
                if (!expr.endsWith(")")) {
                    diag.error(a.valuePos(), "expected 'action' or 'action(path)', got '" + a.value() + "'");
                    return;
                }
                argExpr = expr.substring(open + 1, expr.length() - 1).trim();
                if (argExpr.isEmpty() || argExpr.indexOf(',') >= 0) {
                    diag.error(a.valuePos(), "an action takes one argument path or none, got '" + a.value() + "'");
                    return;
                }
            }
            Field field = isIdentifier(name) ? fields.get(name) : null;
            if (field == null || field.getType() != Action.class) {
                diag.error(a.valuePos(), "'" + name + "' is not an Action field of " + model.getClass().getSimpleName());
                return;
            }
            @SuppressWarnings("unchecked")
            Action<Object> action = (Action<Object>) read(field);
            if (action == null) {
                diag.error(a.valuePos(), "field '" + name + "' of " + model.getClass().getSimpleName() + " is null");
                return;
            }
            Type param = typeArgument(field.getGenericType());
            boolean wantsArg = param != null && param != Void.class;
            if (argExpr == null && wantsArg) {
                diag.error(a.valuePos(), "'" + name + "' takes a " + param.getTypeName() + "; pass it as " + name + "(path)");
                return;
            }
            if (argExpr != null && !wantsArg) {
                diag.error(a.valuePos(), "'" + name + "' takes no argument");
                return;
            }
            Cell<?> arg = null;
            if (argExpr != null) {
                Ref ref = resolve(a, argExpr);
                if (ref == null) {
                    return;
                }
                if (param instanceof Class<?> || param instanceof ParameterizedType) {
                    Class<?> want = rawClass(param);
                    if (!want.isAssignableFrom(box(rawClass(ref.type)))) {
                        diag.error(a.valuePos(), "'" + name + "' takes a " + param.getTypeName() + ", but '"
                                + argExpr + "' is " + ref.type.getTypeName());
                        return;
                    }
                }
                arg = ref.cell;
            }
            Cell<?> argCell = arg;
            e.addEventListener(type, false, ev -> {
                try {
                    action.run(argCell == null ? null : argCell.get());
                } catch (RuntimeException ex) {
                    diag.error(a.valuePos(), "'" + a.value() + "' threw " + ex);
                }
                ev.consume();
            });
        }

        /** Resolves {@code name(.component)*}; reports and returns {@code null} on failure. */
        private Ref resolve(BindAttribute a, String path) {
            String[] parts = path.split("\\.", -1);
            for (String p : parts) {
                if (!isIdentifier(p)) {
                    diag.error(a.valuePos(), "expected a view model field or a path like 'a.b', got '" + a.value() + "'");
                    return null;
                }
            }
            Field field = fields.get(parts[0]);
            if (field == null) {
                diag.error(a.valuePos(), "'" + parts[0] + "' is not a field of " + model.getClass().getSimpleName());
                return null;
            }
            Object value = read(field);
            if (value == null) {
                diag.error(a.valuePos(), "field '" + parts[0] + "' of " + model.getClass().getSimpleName() + " is null");
                return null;
            }
            Ref ref;
            int next = 1;
            if (value instanceof Action<?> action) {
                String member = parts.length > 1 ? parts[1] : "";
                switch (member) {
                    case "pending" -> ref = new Ref(action.pending(), Boolean.class);
                    case "error" -> ref = new Ref(action.error(), Throwable.class);
                    default -> {
                        diag.error(a.valuePos(), "an action is not a value; bind '" + parts[0] + ".pending' or '"
                                + parts[0] + ".error'");
                        return null;
                    }
                }
                next = 2;
            } else {
                Type t = typeArgument(field.getGenericType());
                ref = new Ref((Cell<?>) value, t == null ? Object.class : t);
            }
            for (int i = next; i < parts.length; i++) {
                ref = component(a, ref, parts[i]);
                if (ref == null) {
                    return null;
                }
            }
            return ref;
        }

        private Ref component(BindAttribute a, Ref base, String name) {
            Class<?> raw = rawClass(base.type);
            if (!raw.isRecord()) {
                diag.error(a.valuePos(), "'" + name + "' in '" + a.value() + "': " + base.type.getTypeName()
                        + " is not a record");
                return null;
            }
            for (RecordComponent rc : raw.getRecordComponents()) {
                if (rc.getName().equals(name)) {
                    MethodHandle accessor = accessor(rc.getAccessor());
                    Cell<?> cell = new Derived<>(new Cell<?>[]{base.cell}, args -> {
                        if (args[0] == null) {
                            return null;
                        }
                        try {
                            return accessor.invoke(args[0]);
                        } catch (RuntimeException | Error ex) {
                            throw ex;
                        } catch (Throwable ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
                    return new Ref(cell, rc.getGenericType());
                }
            }
            diag.error(a.valuePos(), raw.getSimpleName() + " has no component '" + name + "'");
            return null;
        }

        private Object read(Field f) {
            try {
                return f.get(model);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static String eventNames() {
        List<String> names = new ArrayList<>();
        for (UiEvent.Type t : UiEvent.Type.values()) {
            names.add(t.templateName());
        }
        return String.join(", ", names);
    }

    private static Map<String, Field> modelFields(Class<?> type) {
        Map<String, Field> out = new HashMap<>();
        for (Class<?> c = type; c != ViewModel.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())
                        && (Cell.class.isAssignableFrom(f.getType()) || f.getType() == Action.class)) {
                    f.setAccessible(true);
                    out.putIfAbsent(f.getName(), f);
                }
            }
        }
        return out;
    }

    private static MethodHandle accessor(Method m) {
        try {
            m.setAccessible(true);
            return MethodHandles.lookup().unreflect(m);
        } catch (IllegalAccessException | RuntimeException ex) {
            throw new IllegalStateException("cannot read " + m, ex);
        }
    }

    /** The first type argument of a parameterized field type, or {@code null}. */
    private static Type typeArgument(Type t) {
        return t instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : null;
    }

    private static Class<?> rawClass(Type t) {
        if (t instanceof Class<?> c) {
            return c;
        }
        if (t instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) {
            return c;
        }
        return Object.class;
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) {
            return c;
        }
        return switch (c.getName()) {
            case "boolean" -> Boolean.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "char" -> Character.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            default -> Void.class;
        };
    }

    private static boolean isBoolean(Type t) {
        return t == Boolean.class || t == boolean.class;
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
                    diag.error(attribute.valuePos(), "'" + attribute.value() + "' threw " + ex);
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

    /** Payloads are host objects without a reliable {@code equals}; any new identity is a change. */
    private static final class PayloadBinding extends Binding {
        private final Element element;

        PayloadBinding(Cell<?> cell, boolean negate, BindAttribute a, Diagnostics diag, Element element) {
            super(cell, negate, a, diag);
            this.element = element;
        }

        @Override
        boolean write(Object value) {
            if (value == element.payload()) {
                return false;
            }
            element.setPayload(value);
            return true;
        }
    }
}
