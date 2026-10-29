package com.azane.ixchel.core.binding;

import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.property.Shorthand;
import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.BindAttribute;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.event.Keys;
import com.azane.ixchel.core.style.Declarations;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The binding attributes of one document ({@code bind:*}, {@code on:*}, {@code each}/{@code if}),
 * resolved against its view model.
 *
 * <pre>{@code
 * <span bind:text="loaded"></span>          <!-- value as text; the element must be empty -->
 * <div bind:class:empty="isEmpty"></div>    <!-- Boolean toggles a class; '!' negates -->
 * <img bind:src="icon"/>                    <!-- value as a plain attribute -->
 * <item bind:item="gun"></item>              <!-- host object as a native element's payload -->
 * <div bind:style:width="xpPercent"></div>  <!-- one longhand: a String in its CSS syntax ("37.5%"),
 *                                                a Number as px or a plain number, null to unset -->
 * <span bind:text="selected.name"></span>   <!-- path: record components, read when the record changes -->
 * <div bind:class:busy="reload.pending"></div>  <!-- an action's pending / error -->
 * <div on:click="reload"></div>             <!-- runs an action; the event counts as handled -->
 * <div on:click="select(row)"></div>        <!-- with the value of a path as its argument -->
 * <div on:keydown.delete="remove"></div>    <!-- key events may name the key (see Keys) and modifiers:
 *                                                on:keydown.up.shift fires only for Shift+Up, and plain
 *                                                on:keydown.up only with none of Shift, Ctrl, Alt -->
 * <div on:click.right="half(c)"></div>      <!-- mouse events may name the button and modifiers the same
 *                                                way; on:click.shift is a Shift+left click, and plain
 *                                                on:click any left click -->
 * <input bind:value="query"/>               <!-- two-way: typing sets the state Value<String> field; also on
 *                                                number and range inputs and on <select> -->
 * <input type="checkbox" bind:checked="on"/> <!-- two-way: a state Value<Boolean> -->
 * <dialog bind:open="confirming">…</dialog> <!-- two-way: open while true; Escape closes it and writes false -->
 * <input bind:value="r.text"/>              <!-- inside each: a row component of type Value<T> made with
 *                                                state(...) is two-way too; any row component that is a
 *                                                Value or Derived is read through and followed -->
 * <div bind:scrolltop="scroll"></div>       <!-- two-way: scrolling sets the state Value<Integer> field;
 *                                                a value past the scroll range is clamped after layout
 *                                                and the clamped offset written back -->
 * <div on:keydown.escape="close"></div>     <!-- key handlers on a top-level element act as document-level
 *                                                handlers: they see every key event, focused child or not -->
 * <div if="!hasSelection">…</div>           <!-- mounted while true; not read while unmounted -->
 * <div each="slots" as="s" key="s.type"     <!-- one copy per list element, matched by key -->
 *      on:click="select(s)" bind:class:on="s.selected"></div>
 * }</pre>
 *
 * Expressions are a path with an optional {@code !}; anything more goes into a {@link Derived}. Every
 * name, record component and type is checked at load.
 *
 * <p>A {@code bind:style:<property>} value is an inline declaration of that property: it overrides
 * stylesheet rules (except {@code !important} ones) and replaces the {@code style} attribute's
 * declaration of the same property, and its changes run transitions like any style change. The
 * property must be a supported longhand; a Number is only taken by length (px) and number properties.
 * An invalid value keeps the last valid one and is reported once until a valid value arrives.
 */
public final class Bindings {
    /** Converts a bound value to text: {@code null} is empty, everything else {@link String#valueOf}. */
    public static final Function<Object, String> PLAIN_TEXT = v -> v == null ? "" : String.valueOf(v);

    private final ViewModel model;
    private final Group root;

    private Bindings(ViewModel model, Group root) {
        this.model = model;
        this.root = root;
    }

    /**
     * Resolves every binding under {@code root}, registers event handlers, expands {@code each} and
     * {@code if} blocks, and writes the initial values into the DOM. Problems go to {@code diag} with
     * the attribute's position; block contents are checked even while not mounted. A template with
     * bindings or handlers needs a model.
     *
     * @param model      the view model, or {@code null} for a template without bindings
     * @param textOf     converts bound values for {@code bind:text} and attribute bindings
     * @param properties the properties {@code bind:style:*} may set
     * @param topLayer   where {@code bind:open} opens and closes dialogs
     */
    public static Bindings compile(Element root, ViewModel model, Function<Object, String> textOf,
                                   TagRegistry tags, PropertyRegistry properties, TopLayer topLayer, Diagnostics diag) {
        Compiler c = new Compiler(root, model, textOf, tags, properties, topLayer, diag);
        Group group = new Group();
        c.collect(root, group);
        Bindings b = new Bindings(model, group);
        if (!diag.hasErrors()) {
            // A model reused across reloads may have nothing new to poll; the new DOM still needs its values.
            if (model != null) {
                model.poll();
            }
            group.update();
        }
        return b;
    }

    /**
     * Runs posted work, polls the model's sources and writes changed values into the DOM, mounting
     * and unmounting block contents. Returns whether the DOM changed; when no source or state
     * changed, no cell is read. Unmounted contents are not read.
     */
    public boolean update() {
        if (model == null || !model.poll()) {
            return false;
        }
        return root.update();
    }

    /** Something refreshed once per changed frame, in document order. */
    private interface Part {
        boolean update();
    }

    /** The bindings and blocks of one compiled subtree. */
    private static final class Group {
        final List<Part> parts = new ArrayList<>();

        boolean update() {
            boolean wrote = false;
            for (int i = 0, n = parts.size(); i < n; i++) {
                wrote |= parts.get(i).update();
            }
            return wrote;
        }
    }

    /**
     * A resolved expression: the cell to read and its declared value type; {@code rowValue} when the
     * cell reads through a row component of type {@code Value<T>}, which two-way bindings may write.
     */
    private record Ref(Cell<?> cell, Type type, boolean rowValue) {
        Ref(Cell<?> cell, Type type) {
            this(cell, type, false);
        }
    }

    private static final class Compiler {
        private static final Set<String> STRUCTURAL = Set.of("each", "as", "key", "if");

        /** The implicit document root; unfocused key events target it. */
        private final Element documentRoot;
        private final ViewModel model;
        private final Map<String, Field> fields;
        private final Function<Object, String> textOf;
        private final TagRegistry tags;
        private final PropertyRegistry properties;
        private final Diagnostics diag;
        private final TopLayer topLayer;
        /** {@code as} names visible to the subtree being compiled. */
        private Map<String, Ref> aliases = Map.of();

        Compiler(Element documentRoot, ViewModel model, Function<Object, String> textOf, TagRegistry tags,
                 PropertyRegistry properties, TopLayer topLayer, Diagnostics diag) {
            this.topLayer = topLayer;
            this.documentRoot = documentRoot;
            this.model = model;
            this.fields = model == null ? Map.of() : modelFields(model.getClass());
            this.textOf = textOf;
            this.tags = tags;
            this.properties = properties;
            this.diag = diag;
        }

        /** Compiles {@code e}'s own attributes and its subtree into {@code out}; block templates leave the tree. */
        void collect(Element e, Group out) {
            for (BindAttribute a : e.bindings()) {
                if (STRUCTURAL.contains(a.name())) {
                    continue;
                }
                if (model == null) {
                    diag.error(a.namePos(), "'" + a.name() + "' needs a view model, but this document has none");
                } else if (a.name().startsWith("on:")) {
                    handler(e, a);
                } else {
                    Binding b = binding(e, a);
                    if (b != null) {
                        out.parts.add(b);
                    }
                }
            }
            Object prev = null;
            for (Node n : List.copyOf(e.children())) {
                if (n instanceof Element child && hasStructural(child)) {
                    e.removeChild(child);
                    Block block = model == null ? null : block(child, e, prev);
                    if (model == null) {
                        diag.error(child.pos(), "<" + child.tag() + "> has each/if, but this document has no view model");
                    }
                    if (block != null) {
                        out.parts.add(block);
                        prev = block;
                    }
                    continue;
                }
                if (n instanceof Element child) {
                    collect(child, out);
                }
                prev = n;
            }
        }

        /** Compiles {@code e} seeing the {@code as} names in {@code scope}. */
        void collectWith(Element e, Map<String, Ref> scope, Group out) {
            Map<String, Ref> saved = aliases;
            aliases = scope;
            try {
                collect(e, out);
            } finally {
                aliases = saved;
            }
        }

        private static boolean hasStructural(Element e) {
            for (BindAttribute a : e.bindings()) {
                if (STRUCTURAL.contains(a.name())) {
                    return true;
                }
            }
            return false;
        }

        private Block block(Element template, Element parent, Object prev) {
            BindAttribute each = null;
            BindAttribute as = null;
            BindAttribute key = null;
            BindAttribute cond = null;
            for (BindAttribute a : template.bindings()) {
                switch (a.name()) {
                    case "each" -> each = a;
                    case "as" -> as = a;
                    case "key" -> key = a;
                    case "if" -> cond = a;
                    default -> {
                    }
                }
            }
            if (each != null && cond != null) {
                diag.error(cond.namePos(), "'each' and 'if' cannot share an element; wrap one in a <div>");
                return null;
            }
            if (each == null) {
                BindAttribute stray = as != null ? as : key;
                if (stray != null) {
                    diag.error(stray.namePos(), "'" + stray.name() + "' only goes with 'each'");
                    return null;
                }
                return ifBlock(template, parent, prev, cond);
            }
            return eachBlock(template, parent, prev, each, as, key);
        }

        private Block ifBlock(Element template, Element parent, Object prev, BindAttribute cond) {
            String expr = cond.value().trim();
            boolean negate = expr.startsWith("!");
            Ref ref = resolve(cond, negate ? expr.substring(1).trim() : expr);
            if (ref == null) {
                return null;
            }
            if (!isBoolean(ref.type)) {
                diag.error(cond.valuePos(), "'if' needs a Boolean, but '" + expr + "' is " + ref.type.getTypeName());
                return null;
            }
            collectWith(cloneTree(template), aliases, new Group());
            return new IfBlock(this, aliases, template, parent, prev, ref.cell, negate, cond);
        }

        private Block eachBlock(Element template, Element parent, Object prev, BindAttribute each,
                                BindAttribute as, BindAttribute key) {
            if (as == null || key == null) {
                diag.error(each.namePos(), "'each' needs 'as' (the row name) and 'key' (a path identifying a row)");
                return null;
            }
            String alias = as.value().trim();
            if (!isIdentifier(alias)) {
                diag.error(as.valuePos(), "'as' takes a name, got '" + as.value() + "'");
                return null;
            }
            Ref list = resolve(each, each.value().trim());
            if (list == null) {
                return null;
            }
            if (!List.class.isAssignableFrom(rawClass(list.type))) {
                diag.error(each.valuePos(), "'each' needs a List, but '" + each.value().trim() + "' is "
                        + list.type.getTypeName());
                return null;
            }
            Type element = typeArgument(list.type);
            Ref row = new Ref(new Value<>(model, null, null), element == null ? Object.class : element);
            Map<String, Ref> rowScope = new HashMap<>(aliases);
            rowScope.put(alias, row);
            String keyExpr = key.value().trim();
            if (!keyExpr.equals(alias) && !keyExpr.startsWith(alias + ".")) {
                diag.error(key.valuePos(), "'key' is a path from the row '" + alias + "', got '" + keyExpr + "'");
                return null;
            }
            Map<String, Ref> saved = aliases;
            aliases = rowScope;
            Ref keyRef;
            try {
                keyRef = resolve(key, keyExpr);
            } finally {
                aliases = saved;
            }
            if (keyRef == null) {
                return null;
            }
            collectWith(cloneTree(template), rowScope, new Group());
            @SuppressWarnings("unchecked")
            Value<Object> keyInput = (Value<Object>) row.cell;
            return new EachBlock(this, aliases, template, parent, prev, list.cell, alias, row.type,
                    keyInput, keyRef.cell, each, key);
        }

        private Binding binding(Element e, BindAttribute a) {
            String target = a.name().substring("bind:".length());
            String expr = a.value().trim();
            boolean negate = expr.startsWith("!");
            Ref ref = resolve(a, negate ? expr.substring(1).trim() : expr);
            if (ref == null) {
                return null;
            }
            FormControl control = e.control();
            boolean twoWay = switch (target) {
                case "scrolltop" -> true;
                case "value" -> control != null && control.kind() != FormControl.Kind.CHECKBOX;
                case "checked" -> control != null && control.kind() == FormControl.Kind.CHECKBOX;
                case "open" -> e.tag().equals("dialog");
                default -> false;
            };
            if (twoWay) {
                return twoWay(e, a, expr, ref, negate, target);
            }
            boolean isClass = target.startsWith("class:");
            boolean isFlag = isClass || target.equals("disabled");
            if ((negate || isFlag) && !isBoolean(ref.type)) {
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
            if (target.equals("disabled")) {
                return new DisabledBinding(ref.cell, negate, a, diag, e);
            }
            if (target.startsWith("style:")) {
                return styleBinding(e, a, ref, negate);
            }
            switch (target) {
                case "class" -> diag.error(a.namePos(), "bind:class takes a class name: bind:class:<name>");
                case "style" -> diag.error(a.namePos(), "bind:style takes a property name: bind:style:<property>");
                case "type" -> diag.error(a.namePos(), "an <input>'s type is fixed by the template; write type=\"…\"");
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

        /** {@code bind:style:<longhand>} with a String, Number or Object path. */
        private Binding styleBinding(Element e, BindAttribute a, Ref ref, boolean negate) {
            String name = a.name().substring("bind:style:".length()).toLowerCase(Locale.ROOT);
            PropertyDef<?> property = properties.byName(name);
            if (property == null) {
                Shorthand shorthand = properties.shorthand(name);
                if (shorthand == null) {
                    diag.error(a.namePos(), "unsupported property '" + name + "' in '" + a.name() + "'");
                } else {
                    List<String> longhands = new ArrayList<>();
                    for (PropertyDef<?> l : shorthand.longhands()) {
                        longhands.add(l.name());
                    }
                    diag.error(a.namePos(), "'" + name + "' is a shorthand; bind its longhands instead: "
                            + String.join(", ", longhands));
                }
                return null;
            }
            Class<?> raw = box(rawClass(ref.type));
            boolean number = Number.class.isAssignableFrom(raw);
            if (negate || !(number || raw == String.class || raw == Object.class)) {
                diag.error(a.valuePos(), "'" + a.name() + "' takes a String or a Number, but '" + a.value().trim()
                        + "' is " + (negate ? "a Boolean" : ref.type.getTypeName()));
                return null;
            }
            if (number && property.syntax().numberUnit() == null) {
                diag.error(a.valuePos(), "'" + name + "' takes no plain numbers; bind a String with its CSS value");
                return null;
            }
            return new StyleBinding(ref.cell, a, diag, e, property);
        }

        /**
         * {@code bind:value} on a form control (a String), {@code bind:checked} on a checkbox and
         * {@code bind:open} on a dialog (a Boolean), {@code bind:scrolltop} (an Integer): the view follows
         * the cell and writes its own changes back to it, so the path must be a state {@link Value}
         * field of the model itself, or a row component of type {@code Value<T>} made by
         * {@code state(...)}.
         */
        @SuppressWarnings("unchecked")
        private Binding twoWay(Element e, BindAttribute a, String expr, Ref ref, boolean negate, String target) {
            Class<?> want = switch (target) {
                case "value" -> String.class;
                case "scrolltop" -> Integer.class;
                default -> Boolean.class;
            };
            boolean field = !aliases.containsKey(expr) && fields.containsKey(expr)
                    && ref.cell instanceof Value<?> v && v.isState();
            boolean state = !negate && (field || ref.rowValue);
            if (!state || box(rawClass(ref.type)) != want) {
                diag.error(a.valuePos(), "'" + a.name() + "' writes back to the view model: it needs a state Value<"
                        + want.getSimpleName() + "> field or row component, but '" + a.value().trim() + "' is "
                        + (state ? ref.type.getTypeName() : negate ? "negated" : "not one"));
                return null;
            }
            Consumer<Object> writeBack = field ? v -> ((Value<Object>) ref.cell).set(v) : v -> {
                Cell<?> inner = ((Flat<?>) ref.cell).current();
                if (inner instanceof Value<?> value && value.isState()) {
                    ((Value<Object>) value).set(v);
                } else {
                    diag.error(a.valuePos(), "'" + a.value().trim() + "' is not a state Value (made by state(...)); "
                            + "'" + a.name() + "' cannot write to it");
                }
            };
            switch (target) {
                case "value" -> {
                    e.addEventListener(UiEvent.Type.INPUT, false, ev -> writeBack.accept(e.control().value()));
                    return new ValueBinding(ref.cell, a, diag, e);
                }
                case "checked" -> {
                    e.addEventListener(UiEvent.Type.INPUT, false, ev -> writeBack.accept(e.control().checked()));
                    return new CheckedBinding(ref.cell, a, diag, e);
                }
                case "open" -> {
                    e.addEventListener(UiEvent.Type.CLOSE, false, ev -> writeBack.accept(false));
                    return new OpenBinding(ref.cell, a, diag, e, topLayer);
                }
                default -> {
                    e.addEventListener(UiEvent.Type.SCROLL, false, ev -> writeBack.accept(Math.round(e.scrollTop())));
                    return new ScrollBinding(ref.cell, a, diag, e);
                }
            }
        }

        /**
         * {@code on:<event>="action"} or {@code on:<event>="action(path)"}. {@code keydown} and
         * {@code keyup} take a filter {@code .<key>[.<modifier>…]}, {@code mousedown}, {@code mouseup} and
         * {@code click} a filter {@code [.<button>][.<modifier>…]}, with the names of {@link Keys}: the
         * listener runs, and consumes the event, only for that key or button (left when a mouse filter
         * names none) with exactly the named modifiers among Shift, Ctrl and Alt. Without a filter it
         * runs for every event of the type, except that {@code click} is a left click, as in the DOM.
         */
        private void handler(Element e, BindAttribute a) {
            String[] parts = a.name().substring("on:".length()).split("\\.", -1);
            String eventName = parts[0];
            UiEvent.Type type = UiEvent.Type.byTemplateName(eventName);
            if (type == null) {
                diag.error(a.namePos(), "unknown event '" + eventName + "'; supported: " + eventNames());
                return;
            }
            boolean keyEvent = type == UiEvent.Type.KEY_DOWN || type == UiEvent.Type.KEY_UP;
            boolean mouseEvent = type == UiEvent.Type.MOUSE_DOWN || type == UiEvent.Type.MOUSE_UP
                    || type == UiEvent.Type.CLICK;
            int filterKey = -1;
            int filterButton = type == UiEvent.Type.CLICK ? 0 : -1;
            int filterMods = -1;
            if (parts.length > 1) {
                if (!keyEvent && !mouseEvent) {
                    diag.error(a.namePos(), "'" + eventName + "' takes no filter; only keydown, keyup, mousedown, "
                            + "mouseup and click do");
                    return;
                }
                int k = 1;
                if (keyEvent) {
                    filterKey = Keys.code(parts[1]);
                    if (filterKey < 0) {
                        diag.error(a.namePos(), "unknown key '" + parts[1] + "' in '" + a.name() + "'");
                        return;
                    }
                    k = 2;
                } else {
                    filterButton = 0;
                    if (Keys.button(parts[1]) >= 0) {
                        filterButton = Keys.button(parts[1]);
                        k = 2;
                    }
                }
                filterMods = 0;
                for (; k < parts.length; k++) {
                    int bit = Keys.modifier(parts[k]);
                    if (bit == 0) {
                        diag.error(a.namePos(), "unknown " + (keyEvent || k > 1 ? "modifier" : "button or modifier")
                                + " '" + parts[k] + "' in '" + a.name() + "'; supported: "
                                + (keyEvent || k > 1 ? "" : "left, right, middle, ") + "shift, ctrl, alt");
                        return;
                    }
                    filterMods |= bit;
                }
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
            int wantKey = filterKey;
            int wantButton = filterButton;
            int wantMods = filterMods;
            // Unfocused key events target the implicit root and run only its listeners, so a top-level
            // element's key handlers live on the root: document-level, like a handler on <body>.
            Element on = keyEvent && e.parent() == documentRoot ? documentRoot : e;
            on.addEventListener(type, false, ev -> {
                if (wantKey >= 0 && ev.key() != wantKey || wantButton >= 0 && ev.button() != wantButton
                        || wantMods >= 0 && (ev.modifiers() & Keys.FILTER_MODIFIERS) != wantMods) {
                    return;
                }
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
            Ref ref = aliases.get(parts[0]);
            int next = 1;
            if (ref == null) {
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
                    Type type = rc.getGenericType();
                    if (Cell.class.isAssignableFrom(rawClass(type))) {
                        Type inner = typeArgument(type);
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Cell<?> flat = new Flat<>((Cell) cell);
                        return new Ref(flat, inner == null ? Object.class : inner, rawClass(type) == Value.class);
                    }
                    return new Ref(cell, type);
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

    /** A deep copy of a block template without its own structural attributes; bindings are not resolved. */
    private static Element cloneTree(Element src) {
        Element e = copy(src);
        for (BindAttribute a : src.bindings()) {
            if (!Compiler.STRUCTURAL.contains(a.name())) {
                e.addBinding(a);
            }
        }
        return e;
    }

    private static Element copy(Element src) {
        Element e = new Element(src.tag(), src.pos());
        for (String cls : src.classes()) {
            e.addClass(cls);
        }
        src.attributes().forEach(e::setAttribute);
        if (!src.inlineStyle().isEmpty()) {
            e.setInlineStyle(src.inlineStyle());
        }
        for (Node n : src.children()) {
            if (n instanceof Element c) {
                Element child = copy(c);
                for (BindAttribute a : c.bindings()) {
                    child.addBinding(a);
                }
                e.appendChild(child);
            } else {
                e.appendChild(new TextNode(((TextNode) n).text(), n.pos()));
            }
        }
        return e;
    }

    /** One mounted copy of a block template with its compiled bindings. */
    private static final class Instance {
        final Element root;
        final Group group = new Group();
        final Value<Object> row;

        Instance(Element root, Value<Object> row) {
            this.root = root;
            this.row = row;
        }
    }

    /**
     * Template content mounted by data. Mounted nodes sit after {@code prev} (a static sibling, an
     * earlier block, or {@code null} for the start of {@code parent}).
     */
    private abstract static class Block implements Part {
        final Compiler compiler;
        final Map<String, Ref> scope;
        final Element template;
        final Element parent;
        final Object prev;

        Block(Compiler compiler, Map<String, Ref> scope, Element template, Element parent, Object prev) {
            this.compiler = compiler;
            this.scope = scope;
            this.template = template;
            this.parent = parent;
            this.prev = prev;
        }

        /** The last node this block has mounted, or {@code null}. */
        abstract Node lastMounted();

        /** Where this block's first node goes. */
        final int start() {
            for (Object p = prev; p != null; ) {
                if (p instanceof Node n) {
                    return parent.children().indexOf(n) + 1;
                }
                Block b = (Block) p;
                Node last = b.lastMounted();
                if (last != null) {
                    return parent.children().indexOf(last) + 1;
                }
                p = b.prev;
            }
            return 0;
        }

        final Instance instantiate(Map<String, Ref> instanceScope, Value<Object> row) {
            Instance i = new Instance(cloneTree(template), row);
            compiler.collectWith(i.root, instanceScope, i.group);
            return i;
        }
    }

    /** {@code if="path"}: mounted while the Boolean is true; recreated on each false-to-true change. */
    private static final class IfBlock extends Block {
        private final Cell<?> cond;
        private final boolean negate;
        private final BindAttribute attribute;
        private Instance instance;
        private boolean failing;

        IfBlock(Compiler compiler, Map<String, Ref> scope, Element template, Element parent, Object prev,
                Cell<?> cond, boolean negate, BindAttribute attribute) {
            super(compiler, scope, template, parent, prev);
            this.cond = cond;
            this.negate = negate;
            this.attribute = attribute;
        }

        @Override
        Node lastMounted() {
            return instance == null ? null : instance.root;
        }

        @Override
        public boolean update() {
            boolean on;
            try {
                on = Boolean.TRUE.equals(cond.get()) != negate;
                failing = false;
            } catch (RuntimeException ex) {
                if (!failing) {
                    failing = true;
                    compiler.diag.error(attribute.valuePos(), "'" + attribute.value() + "' threw " + ex);
                }
                on = instance != null;
            }
            boolean wrote = false;
            if (on && instance == null) {
                instance = instantiate(scope, null);
                parent.insertChild(start(), instance.root);
                wrote = true;
            } else if (!on && instance != null) {
                parent.removeChild(instance.root);
                instance = null;
                return true;
            }
            if (instance != null) {
                wrote |= instance.group.update();
            }
            return wrote;
        }
    }

    /**
     * {@code each="list" as="row" key="row.id"}: one copy of the template per element, matched to the
     * previous frame's copies by key. A kept copy keeps its nodes (and so hover, focus and layout
     * state) and only sees the new row value; copies are moved into list order.
     */
    private static final class EachBlock extends Block {
        private final Cell<?> list;
        private final String alias;
        private final Type rowType;
        private final Value<Object> keyInput;
        private final Cell<?> key;
        private final BindAttribute eachAttribute;
        private final BindAttribute keyAttribute;
        private Map<Object, Instance> rows = new LinkedHashMap<>();
        private boolean failing;
        private boolean reportedDuplicate;

        EachBlock(Compiler compiler, Map<String, Ref> scope, Element template, Element parent, Object prev,
                  Cell<?> list, String alias, Type rowType, Value<Object> keyInput, Cell<?> key,
                  BindAttribute eachAttribute, BindAttribute keyAttribute) {
            super(compiler, scope, template, parent, prev);
            this.list = list;
            this.alias = alias;
            this.rowType = rowType;
            this.keyInput = keyInput;
            this.key = key;
            this.eachAttribute = eachAttribute;
            this.keyAttribute = keyAttribute;
        }

        @Override
        Node lastMounted() {
            Node last = null;
            for (Instance i : rows.values()) {
                last = i.root;
            }
            return last;
        }

        @Override
        public boolean update() {
            List<?> items;
            try {
                items = (List<?>) list.get();
                failing = false;
            } catch (RuntimeException ex) {
                if (!failing) {
                    failing = true;
                    compiler.diag.error(eachAttribute.valuePos(), "'" + eachAttribute.value() + "' threw " + ex);
                }
                return updateRows();
            }
            boolean wrote = false;
            Map<Object, Instance> next = new LinkedHashMap<>();
            if (items != null) {
                for (Object item : items) {
                    keyInput.replace(item);
                    Object k = key.get();
                    if (next.containsKey(k)) {
                        if (!reportedDuplicate) {
                            reportedDuplicate = true;
                            compiler.diag.error(keyAttribute.valuePos(), "duplicate key " + k + " in '"
                                    + eachAttribute.value() + "'; later rows with it are skipped");
                        }
                        continue;
                    }
                    Instance row = rows.remove(k);
                    if (row == null) {
                        Value<Object> cell = new Value<>(compiler.model, null, null);
                        cell.replace(item);
                        Map<String, Ref> rowScope = new HashMap<>(scope);
                        rowScope.put(alias, new Ref(cell, rowType));
                        row = instantiate(rowScope, cell);
                    } else {
                        row.row.replace(item);
                    }
                    next.put(k, row);
                }
            }
            for (Instance gone : rows.values()) {
                parent.removeChild(gone.root);
                wrote = true;
            }
            rows = next;
            int index = start();
            for (Instance row : rows.values()) {
                if (index >= parent.children().size() || parent.children().get(index) != row.root) {
                    parent.insertChild(index, row.root);
                    wrote = true;
                }
                index++;
            }
            return updateRows() || wrote;
        }

        private boolean updateRows() {
            boolean wrote = false;
            for (Instance row : rows.values()) {
                wrote |= row.group.update();
            }
            return wrote;
        }
    }

    /** One bound attribute; writes the DOM only when the value differs from the last write. */
    private abstract static class Binding implements Part {
        private final Cell<?> cell;
        private final boolean negate;
        final BindAttribute attribute;
        final Diagnostics diag;
        private boolean failing;

        Binding(Cell<?> cell, boolean negate, BindAttribute attribute, Diagnostics diag) {
            this.cell = cell;
            this.negate = negate;
            this.attribute = attribute;
            this.diag = diag;
        }

        /** A failing computation keeps the last written value and is reported once until it recovers. */
        @Override
        public final boolean update() {
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

    /** {@code bind:disabled}: the boolean attribute is present while the value is true. */
    private static final class DisabledBinding extends Binding {
        private final Element element;

        DisabledBinding(Cell<?> cell, boolean negate, BindAttribute a, Diagnostics diag, Element element) {
            super(cell, negate, a, diag);
            this.element = element;
        }

        @Override
        boolean write(Object value) {
            boolean on = Boolean.TRUE.equals(value);
            if (on == (element.attribute("disabled") != null)) {
                return false;
            }
            if (on) {
                element.setAttribute("disabled", "");
            } else {
                element.removeAttribute("disabled");
            }
            return true;
        }
    }

    /** {@code bind:value}: the control shows the cell; user edits write it back through an input listener. */
    private static final class ValueBinding extends Binding {
        private final Element element;

        ValueBinding(Cell<?> cell, BindAttribute a, Diagnostics diag, Element element) {
            super(cell, false, a, diag);
            this.element = element;
        }

        /** Repaint-only, or a restyle of a select's options: the control marks what it needs itself. */
        @Override
        boolean write(Object value) {
            element.control().setValue(value == null ? "" : (String) value);
            return false;
        }
    }

    /** {@code bind:checked}: the checkbox follows the cell; toggles write it back through an input listener. */
    private static final class CheckedBinding extends Binding {
        private final Element element;

        CheckedBinding(Cell<?> cell, BindAttribute a, Diagnostics diag, Element element) {
            super(cell, false, a, diag);
            this.element = element;
        }

        @Override
        boolean write(Object value) {
            element.control().setChecked(Boolean.TRUE.equals(value));
            return false;
        }
    }

    /** {@code bind:open}: the dialog is in the top layer while the cell is true; Escape writes false back. */
    private static final class OpenBinding extends Binding {
        private final Element element;
        private final TopLayer topLayer;

        OpenBinding(Cell<?> cell, BindAttribute a, Diagnostics diag, Element element, TopLayer topLayer) {
            super(cell, false, a, diag);
            this.element = element;
            this.topLayer = topLayer;
        }

        @Override
        boolean write(Object value) {
            if (Boolean.TRUE.equals(value)) {
                topLayer.open(element);
            } else {
                topLayer.close(element);
            }
            return false;
        }
    }

    /**
     * {@code bind:scrolltop}: the element scrolls to the field; scrolling, and clamping to the
     * scroll range after layout, write the offset back through a scroll listener.
     */
    private static final class ScrollBinding extends Binding {
        private final Element element;

        ScrollBinding(Cell<?> cell, BindAttribute a, Diagnostics diag, Element element) {
            super(cell, false, a, diag);
            this.element = element;
        }

        /**
         * Requests the offset against the next layout's range, so content bound in the same frame
         * counts; reports a write only past the current range, to make that layout run.
         */
        @Override
        boolean write(Object value) {
            int top = value == null ? 0 : (Integer) value;
            if (top == Math.round(element.scrollTop())) {
                return false;
            }
            element.requestScrollTop(top);
            return top > element.maxScrollTop();
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

    /**
     * {@code bind:style:<property>}: writes the element's bound value of one longhand. An unchanged
     * value is not parsed again; an invalid one keeps the last valid value and is reported once
     * until a valid value arrives.
     */
    private static final class StyleBinding extends Binding {
        private static final Object NONE = new Object();
        private final Element element;
        private final PropertyDef<?> property;
        private Object last = NONE;
        private boolean invalid;

        StyleBinding(Cell<?> cell, BindAttribute a, Diagnostics diag, Element element, PropertyDef<?> property) {
            super(cell, false, a, diag);
            this.element = element;
            this.property = property;
        }

        @Override
        boolean write(Object value) {
            if (Objects.equals(value, last)) {
                return false;
            }
            last = value;
            Object parsed = null;
            if (value != null) {
                Diagnostics problems = new Diagnostics();
                parsed = parse(value, problems);
                if (parsed == null) {
                    if (!invalid) {
                        invalid = true;
                        diag.error(attribute.valuePos(), "'" + attribute.value() + "' gave " + describe(value)
                                + " for '" + property.name() + "': " + firstError(problems));
                    }
                    return false;
                }
            }
            invalid = false;
            if (Objects.equals(parsed, element.boundStyle().get(property))) {
                return false;
            }
            element.setBoundStyle(property, parsed);
            return true;
        }

        /** The property's value or a CSS-wide keyword; {@code null} after reporting to {@code problems}. */
        private Object parse(Object value, Diagnostics problems) {
            SourcePos pos = attribute.valuePos();
            if (value instanceof String s) {
                List<ComponentValue> css = CssParser.parseValue(s, pos, problems);
                return problems.hasErrors() ? null : Declarations.value(property, css, pos, problems);
            }
            if (value instanceof Number n) {
                String unit = property.syntax().numberUnit();
                double d = n.doubleValue();
                if (unit == null) {
                    problems.error(pos, "this property takes no plain numbers; give a String with its CSS value");
                    return null;
                }
                if (!Double.isFinite(d)) {
                    problems.error(pos, "not a finite number");
                    return null;
                }
                Token t = unit.isEmpty() ? new Token(Token.Kind.NUMBER, "", d, "", pos)
                        : new Token(Token.Kind.DIMENSION, "", d, unit, pos);
                return property.syntax().parse(List.of(t), pos, problems);
            }
            problems.error(pos, "expected a String or a Number");
            return null;
        }

        private static String describe(Object value) {
            if (value instanceof String s) {
                return "'" + s + "'";
            }
            return value instanceof Number ? value.toString() : "a " + value.getClass().getName();
        }

        private static String firstError(Diagnostics problems) {
            for (Diagnostic d : problems.entries()) {
                if (d.severity() == Diagnostic.Severity.ERROR) {
                    return d.message();
                }
            }
            throw new IllegalStateException("no error reported");
        }
    }
}
