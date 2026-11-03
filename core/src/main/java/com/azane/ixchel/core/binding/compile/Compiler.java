package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.EventInfo;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.expr.Expr;
import com.azane.ixchel.core.binding.expr.ExprCode;
import com.azane.ixchel.core.binding.expr.Expressions;
import com.azane.ixchel.core.binding.expr.Types;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.property.Shorthand;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.FormFacet;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.Presence;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.event.Keys;
import com.azane.ixchel.core.template.ComponentDef;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.template.TemplateParser;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static com.azane.ixchel.core.binding.compile.Bindings.MODEL;

/**
 * Resolves a document's directives, blocks and component instances into {@link Part}s. Every block
 * copy and component instance is compiled anew, resolving its names in the {@link Env} it sees.
 */
final class Compiler {
    /**
     * The names visible where a template is being compiled, where its problems go, and, inside a
     * component's template, what fills its slots.
     *
     * @param owner     a page's view model class or a component's {@code <name>}, for messages
     * @param component whether this is a component's template
     * @param slots     the instance's slot content; {@code null} outside a component's template
     */
    record Env(Map<String, Name> names, Diagnostics diag, String owner, boolean component, Slots slots) {
        Env with(String name, Name value) {
            Map<String, Name> next = new HashMap<>(names);
            next.put(name, value);
            return new Env(next, diag, owner, component, slots);
        }
    }

    /** What a component instance's user put in each slot ({@code ""} the default one), compiled in {@code user}. */
    record Slots(Map<String, List<Node>> content, Env user) {
    }

    /** The implicit document root; unfocused key events target it. */
    private final Element documentRoot;
    private final ViewModel model;
    private final Bindings.TextConverter text;
    private final TagRegistry tags;
    private final PropertyRegistry properties;
    private final TopLayer topLayer;
    private final Map<String, ComponentDef> components;
    /** Where blocks enter and exit their copies. */
    final Presence presence;
    private Env env;

    /**
     * @param model the view model local states belong to
     * @param names the page's names, as {@link Bindings} resolved them from the view model
     */
    Compiler(Element documentRoot, ViewModel model, Map<String, Name> names, String owner,
             Bindings.TextConverter text, TagRegistry tags, PropertyRegistry properties, TopLayer topLayer,
             Map<String, ComponentDef> components, Presence presence, Diagnostics diag) {
        this.documentRoot = documentRoot;
        this.model = model;
        this.text = text;
        this.tags = tags;
        this.properties = properties;
        this.topLayer = topLayer;
        this.components = components;
        this.presence = presence;
        this.env = new Env(names, diag, owner, false, null);
    }

    private Diagnostics diag() {
        return env.diag();
    }

    /** Compiles the children of the document root. */
    void compileRoot(Part.Group out) {
        items(documentRoot, List.copyOf(documentRoot.children()), true, Anchor.START, out, null);
    }

    /** Copies {@code content}, compiles the copy in {@code in} and adds its top-level nodes and blocks to {@code items}. */
    void compileCopy(Element parent, List<Node> content, Env in, Anchor head, Part.Group out, List<Object> items) {
        List<Node> copy = new ArrayList<>(content.size());
        for (Node n : content) {
            copy.add(n.copyTemplate());
        }
        Env saved = env;
        env = in;
        try {
            items(parent, copy, false, head, out, items);
        } finally {
            env = saved;
        }
    }

    /**
     * Compiles {@code nodes}, children of {@code parent} when {@code attached}, otherwise not yet
     * mounted. Blocks and slots leave the tree; a slot's content takes its place. Returns the anchor
     * after the last node; {@code items}, when given, receives the nodes and blocks in order.
     */
    private Anchor items(Element parent, List<Node> nodes, boolean attached, Anchor head, Part.Group out,
                               List<Object> items) {
        Anchor prev = head;
        for (Node n : nodes) {
            if (n instanceof Element e && (e.tag().equals("for") || e.tag().equals("if"))) {
                if (attached) {
                    parent.removeChild(e);
                }
                Block b = e.tag().equals("for") ? eachBlock(e, parent, prev) : ifBlock(e, parent, prev);
                if (b != null) {
                    out.parts.add(b);
                    prev = b;
                    if (items != null) {
                        items.add(b);
                    }
                }
                continue;
            }
            if (n instanceof Element e && e.tag().equals("slot")) {
                prev = slot(e, parent, attached, prev, out, items);
                continue;
            }
            if (n instanceof Element e) {
                collect(e, out);
            }
            if (items != null) {
                items.add(n);
            }
            prev = () -> n;
        }
        return prev;
    }

    /** Compiles {@code e}'s own directives and its subtree, or the component it instantiates, into {@code out}. */
    private void collect(Element e, Part.Group out) {
        Env saved = env;
        try {
            for (Directive a : e.directives()) {
                if (a.name().startsWith("state:")) {
                    state(a, out);
                }
            }
            ComponentDef def = components.get(e.tag());
            if (def != null) {
                instantiate(e, def, out);
                return;
            }
            for (Directive a : e.directives()) {
                directive(e, a, out);
            }
            items(e, List.copyOf(e.children()), true, Anchor.START, out, null);
        } finally {
            env = saved;
        }
    }

    private void directive(Element e, Directive a, Part.Group out) {
        if (a.name().startsWith("state:")) {
            return;
        }
        if (a.name().startsWith("on:")) {
            handler(e, a);
            return;
        }
        Binding b = binding(e, a);
        if (b != null) {
            out.parts.add(b);
        }
    }

    /** {@code state:name="init"}: a new local state, visible from here to the end of the element. */
    private void state(Directive a, Part.Group out) {
        String name = a.name().substring("state:".length());
        if (!isIdentifier(name) || name.startsWith("$")) {
            diag().report(BindingCode.INVALID_NAME, a.namePos(), "'" + name + "' is not a valid state name");
            return;
        }
        if (hides(name, a.namePos(), "state")) {
            return;
        }
        Ref init = expression(a);
        if (init == null) {
            return;
        }
        Value<Object> cell = MODEL.state(model);
        env = env.with(name, new Ref(cell, init.type(), Ref.Write.VALUE));
        out.parts.add(new StateInit(cell, init.cell(), a, diag()));
    }

    /** Reports and returns true if a new {@code name} would hide a visible one. */
    private boolean hides(String name, SourcePos at, String what) {
        Name old = env.names().get(name);
        if (old == null) {
            return false;
        }
        diag().report(BindingCode.HIDDEN_NAME, at, what + " '" + name + "' would hide " + describeName(old));
        return true;
    }

    private String describeName(Name n) {
        if (n instanceof Name.Event) {
            return "an event of " + env.owner();
        }
        return env.component() ? "a parameter, row or state of " + env.owner()
                : "a field, row or state of " + env.owner();
    }

    private Block ifBlock(Element block, Element parent, Anchor prev) {
        Directive test = blockAttribute(block, "test");
        if (test == null) {
            return null;
        }
        Ref ref = expression(test);
        if (ref == null) {
            return null;
        }
        if (!Types.isBoolean(ref.type())) {
            diag().report(BindingCode.VALUE_TYPE, test.valuePos(), "<if> needs a Boolean, but '" + test.value().trim()
                    + "' is " + ref.type().getTypeName());
            return null;
        }
        check(block, env);
        return new IfBlock(this, env, block.children(), parent, prev, ref.cell(), test);
    }

    private Block eachBlock(Element block, Element parent, Anchor prev) {
        Directive each = blockAttribute(block, "each");
        Directive as = blockAttribute(block, "as");
        Directive key = blockAttribute(block, "key");
        if (each == null || as == null || key == null) {
            return null;
        }
        String alias = as.value().trim();
        if (!isIdentifier(alias) || alias.startsWith("$")) {
            diag().report(BindingCode.INVALID_NAME, as.valuePos(), "'as' takes a name, got '" + as.value() + "'");
            return null;
        }
        if (hides(alias, as.valuePos(), "row")) {
            return null;
        }
        Ref list = expression(each);
        if (list == null) {
            return null;
        }
        if (!List.class.isAssignableFrom(Types.rawClass(list.type()))) {
            diag().report(BindingCode.VALUE_TYPE, each.valuePos(), "'each' needs a List, but '" + each.value().trim()
                    + "' is " + list.type().getTypeName());
            return null;
        }
        Type element = Types.typeArgument(list.type());
        Cell<Object> keyInput = MODEL.held(null);
        Type rowType = element == null ? Object.class : element;
        Env rowEnv = env.with(alias, new Ref(keyInput, rowType));
        Directive index = blockAttribute(block, "index");
        String indexName = null;
        if (index != null) {
            indexName = index.value().trim();
            if (!isIdentifier(indexName) || indexName.startsWith("$") || indexName.equals(alias)) {
                diag().report(BindingCode.INVALID_NAME, index.valuePos(), "'index' takes a name other than the row's, got '"
                        + index.value() + "'");
                return null;
            }
            if (hides(indexName, index.valuePos(), "row index")) {
                return null;
            }
        }
        Expr keyExpr = parse(key);
        if (keyExpr == null) {
            return null;
        }
        if (!(keyExpr instanceof Expr.Path keyPath) || !keyPath.names().get(0).equals(alias)) {
            diag().report(BindingCode.INVALID_KEY, key.valuePos(), "'key' is a path from the row '" + alias + "', got '"
                    + key.value().trim() + "'");
            return null;
        }
        Env saved = env;
        env = rowEnv;
        Ref keyRef;
        try {
            keyRef = resolve(key, keyPath);
        } finally {
            env = saved;
        }
        if (keyRef == null) {
            return null;
        }
        check(block, indexName == null ? rowEnv : rowEnv.with(indexName, new Ref(MODEL.held(0), Integer.class)));
        return new EachBlock(this, env, block.children(), parent, prev, list.cell(), alias, rowType, indexName,
                keyInput, keyRef.cell(), each, key);
    }

    /** Compiles a throwaway copy of a block's content, so its problems are reported while it is not mounted. */
    private void check(Element block, Env in) {
        compileCopy(block, block.children(), in, Anchor.START, new Part.Group(), new ArrayList<>());
    }

    private static Directive blockAttribute(Element block, String name) {
        for (Directive d : block.directives()) {
            if (d.name().equals(name)) {
                return d;
            }
        }
        return null;
    }

    /** A {@code <slot>} in a component's template: replaced by what the user gave, or by its own fallback content. */
    private Anchor slot(Element slot, Element parent, boolean attached, Anchor prev, Part.Group out,
                              List<Object> items) {
        String name = "";
        for (Directive d : slot.directives()) {
            name = d.name().equals("name") ? d.value() : name;
        }
        Slots slots = env.slots();
        List<Node> given = slots.content().get(name);
        List<Node> content = new ArrayList<>();
        Env in;
        if (given != null) {
            for (Node n : given) {
                content.add(n.copyTemplate());
            }
            in = slots.user();
        } else {
            content.addAll(slot.children());
            in = env;
        }
        if (attached) {
            int index = parent.children().indexOf(slot);
            parent.removeChild(slot);
            for (Node n : content) {
                parent.insertChild(index++, n);
            }
        }
        Env saved = env;
        env = in;
        try {
            return items(parent, content, attached, prev, out, items);
        } finally {
            env = saved;
        }
    }

    /**
     * {@code <name …>} of a component: the element stays as the instance's shell, its children become
     * slot content, and the component's template is compiled into it with the parameters and events
     * the shell's attributes give.
     */
    private void instantiate(Element shell, ComponentDef def, Part.Group out) {
        Env user = env;
        String tag = "<" + def.name() + ">";
        Map<String, Ref> args = new HashMap<>();
        Map<String, Directive> listeners = new LinkedHashMap<>();
        // Parameters given with a binding that failed to compile; their problem is already reported.
        Set<String> failed = new HashSet<>();
        for (Directive a : shell.directives()) {
            String name = a.name();
            if (name.startsWith("bind:") && def.hasParam(name.substring("bind:".length()))) {
                Expr parsed = parse(a);
                Ref ref = parsed == null ? null : compile(a, parsed);
                if (ref != null) {
                    boolean writable = parsed instanceof Expr.Path && ref.write() != Ref.Write.NONE;
                    args.put(name.substring("bind:".length()), writable ? ref : new Ref(ref.cell(), ref.type()));
                } else {
                    failed.add(name.substring("bind:".length()));
                }
            } else if (name.equals("bind:text")) {
                diag().report(BindingCode.COMPONENT_TEXT, a.namePos(),
                        "bind:text cannot fill " + tag + ": its content is the component's template");
            } else if (name.startsWith("on:") && def.events().contains(name.substring("on:".length()).split("\\.")[0])) {
                if (name.indexOf('.') >= 0) {
                    diag().report(BindingCode.UNEXPECTED_FILTER, a.namePos(), "component events take no filters");
                } else {
                    listeners.put(name.substring("on:".length()), a);
                }
            } else {
                directive(shell, a, out);
            }
        }
        for (ComponentDef.Param p : def.params()) {
            String literal = shell.attribute(p.name());
            if (literal == null) {
                continue;
            }
            shell.removeAttribute(p.name());
            if (args.containsKey(p.name())) {
                diag().report(BindingCode.DUPLICATE_PARAMETER, shell.pos(),
                        "parameter '" + p.name() + "' of " + tag + " is given twice");
            } else {
                args.put(p.name(), new Ref(MODEL.held(literal), String.class));
            }
        }

        Map<String, List<Node>> content = new HashMap<>();
        for (Node n : List.copyOf(shell.children())) {
            String slot = n instanceof Element e && TemplateParser.slotOf(e) != null ? TemplateParser.slotOf(e) : "";
            if (!def.slots().contains(slot) && !content.containsKey(slot)) {
                diag().report(BindingCode.UNKNOWN_SLOT, n.pos(), slot.isEmpty()
                        ? tag + " has no default <slot>; this content would be dropped"
                        : tag + " has no slot '" + slot + "'");
            }
            content.computeIfAbsent(slot, k -> new ArrayList<>()).add(n);
            shell.removeChild(n);
        }

        Diagnostics inside = user.diag().within(" (in " + tag + " at " + shell.pos() + ")");
        Map<String, Name> names = new HashMap<>();
        env = new Env(names, inside, tag, true, new Slots(content, user));
        boolean unresolved = false;
        for (ComponentDef.Param p : def.params()) {
            Ref given = args.get(p.name());
            if (given != null) {
                names.put(p.name(), given);
            } else if (p.fallback() != null) {
                Ref init = expression(p.fallback());
                if (init != null) {
                    Value<Object> cell = MODEL.state(model);
                    names.put(p.name(), new Ref(cell, init.type(), Ref.Write.VALUE));
                    out.parts.add(new StateInit(cell, init.cell(), p.fallback(), inside));
                } else {
                    unresolved = true;
                }
            } else {
                if (!failed.contains(p.name())) {
                    user.diag().report(BindingCode.MISSING_PARAMETER, shell.pos(), tag + " needs parameter '" + p.name() + "'");
                }
                unresolved = true;
            }
        }
        if (unresolved) {
            // The body would only repeat the missing parameter at each use of it.
            env = user;
            return;
        }
        Map<String, Name.Event> events = new HashMap<>();
        for (String e : def.events()) {
            Name.Event port = new Name.Event(e);
            events.put(e, port);
            names.put(e, port);
        }
        for (Node n : def.body()) {
            shell.appendChild(n.copyTemplate());
        }
        items(shell, List.copyOf(shell.children()), true, Anchor.START, out, null);

        env = user;
        listeners.forEach((event, a) -> listen(events.get(event), a));
    }

    /** {@code on:<event>="statements"} for a component event; {@code $event} is its payload, if it has one. */
    private void listen(Name.Event port, Directive a) {
        Cell<Object> payload = MODEL.held(null);
        List<Runnable> steps = statements(a, port.payload == null ? null : new Ref(payload, port.payload));
        if (steps == null) {
            return;
        }
        Diagnostics diag = diag();
        port.listener = v -> {
            try {
                MODEL.replace(payload, v);
                for (int i = 0, n = steps.size(); i < n; i++) {
                    steps.get(i).run();
                }
            } catch (RuntimeException ex) {
                diag.report(BindingCode.RUNTIME_EXCEPTION, a.valuePos(), "'" + a.value() + "' threw " + ex);
            }
        };
    }

    /** Compiles {@code a}'s statements seeing {@code event} as {@code $event}; {@code null} after reporting. */
    private List<Runnable> statements(Directive a, Ref event) {
        List<Expr.Stmt> parsed;
        try {
            parsed = Expr.parseStatements(a.value());
        } catch (Expr.SyntaxError ex) {
            diag().report(ExprCode.SYNTAX, at(a, ex.at), ex.getMessage());
            return null;
        }
        Env saved = env;
        if (event != null) {
            env = env.with("$event", event);
        }
        List<Runnable> steps = new ArrayList<>();
        boolean ok = true;
        try {
            for (Expr.Stmt s : parsed) {
                Runnable step = s instanceof Expr.Call c ? call(a, c) : assign(a, (Expr.Assign) s);
                ok &= step != null;
                steps.add(step);
            }
        } finally {
            env = saved;
        }
        return ok ? steps : null;
    }

    private Binding binding(Element e, Directive a) {
        String target = a.name().substring("bind:".length());
        boolean shell = components.containsKey(e.tag());
        Expr parsed = parse(a);
        if (parsed == null) {
            return null;
        }
        Ref ref = compile(a, parsed);
        if (ref == null) {
            return null;
        }
        boolean twoWay = target.equals("scrolltop") || !shell && e.type().facet(FormFacet.KEY).binds(e, target);
        if (twoWay) {
            return twoWay(e, a, parsed, ref, target);
        }
        boolean isClass = target.startsWith("class:");
        if ((isClass || target.equals("disabled")) && !Types.isBoolean(ref.type())) {
            diag().report(BindingCode.VALUE_TYPE, a.valuePos(), "'" + a.name() + "' needs a Boolean, but '"
                    + a.value().trim() + "' is " + ref.type().getTypeName());
            return null;
        }
        if (target.equals("text")) {
            for (Node c : e.children()) {
                if (!(c instanceof TextNode t) || !t.text().isBlank()) {
                    diag().report(BindingCode.TEXT_NOT_EMPTY, a.namePos(),
                            "bind:text replaces the content of <" + e.tag() + ">", "leave it empty");
                    return null;
                }
            }
            while (!e.children().isEmpty()) {
                e.removeChild(e.children().get(0));
            }
            TextNode node = new TextNode("", a.valuePos());
            e.appendChild(node);
            return new Binding.Text(ref.cell(), a, diag(), text, node);
        }
        if (!shell && target.equals(e.type().payload())) {
            Class<?> accepted = e.type().payloadType();
            if (!Types.acceptsPayload(ref.type(), accepted)) {
                diag().report(BindingCode.PAYLOAD_TYPE, a.valuePos(), "<" + e.tag() + "> takes "
                        + accepted.getName() + " or a Supplier of one, but '" + a.value().trim() + "' is " + ref.type().getTypeName());
                return null;
            }
            return new Binding.Payload(ref.cell(), a, diag(), e);
        }
        if (isClass) {
            String cls = target.substring("class:".length());
            if (!isClassName(cls)) {
                diag().report(BindingCode.INVALID_CLASS_NAME, a.namePos(), "'" + cls + "' is not a valid class name");
                return null;
            }
            return new Binding.ClassToggle(ref.cell(), a, diag(), e, cls);
        }
        if (target.equals("disabled")) {
            return new Binding.Disabled(ref.cell(), a, diag(), e);
        }
        if (target.startsWith("style:")) {
            return styleBinding(e, a, ref);
        }
        switch (target) {
            case "class" -> diag().report(BindingCode.INCOMPLETE_TARGET, a.namePos(),
                    "bind:class takes a class name: bind:class:<name>");
            case "style" -> diag().report(BindingCode.INCOMPLETE_TARGET, a.namePos(),
                    "bind:style takes a property name: bind:style:<property>");
            case "type" -> diag().report(BindingCode.UNKNOWN_TARGET, a.namePos(),
                    "an <input>'s type is fixed by the template", "write type=\"…\"");
            default -> {
                boolean allowed = shell ? TagRegistry.GLOBAL_ATTRIBUTES.contains(target)
                        : tags.isAllowedAttribute(e.tag(), target);
                if (target.indexOf(':') >= 0 || !allowed) {
                    diag().report(BindingCode.UNKNOWN_TARGET, a.namePos(),
                            "unknown binding target '" + a.name() + "' on <" + e.tag() + ">");
                } else {
                    return new Binding.Attribute(ref.cell(), a, diag(), text::plain, e, target);
                }
            }
        }
        return null;
    }

    /** {@code bind:style:<longhand>} or {@code bind:style:--<name>} with a String, Number or Object value. */
    private Binding styleBinding(Element e, Directive a, Ref ref) {
        String name = a.name().substring("bind:style:".length()).toLowerCase(Locale.ROOT);
        PropertyDef<?> property = properties.byName(name);
        boolean custom = property == null && name.startsWith("--");
        if (!custom && property == null) {
            Shorthand shorthand = properties.shorthand(name);
            if (shorthand == null) {
                diag().report(BindingCode.UNKNOWN_STYLE_PROPERTY, a.namePos(),
                        "unsupported property '" + name + "' in '" + a.name() + "'");
            } else {
                List<String> longhands = new ArrayList<>();
                for (PropertyDef<?> l : shorthand.longhands()) {
                    longhands.add(l.name());
                }
                diag().report(BindingCode.SHORTHAND_STYLE, a.namePos(), "'" + name + "' is a shorthand",
                        "bind its longhands instead: " + String.join(", ", longhands));
            }
            return null;
        }
        Class<?> raw = Types.boxed(ref.type());
        boolean number = Number.class.isAssignableFrom(raw);
        if (!(number || raw == String.class || raw == Object.class)) {
            diag().report(BindingCode.VALUE_TYPE, a.valuePos(), "'" + a.name() + "' takes a String or a Number, but '"
                    + a.value().trim() + "' is " + ref.type().getTypeName());
            return null;
        }
        if (custom) {
            return new CustomPropertyBinding(ref.cell(), a, diag(), e, name);
        }
        if (number && property.syntax().numberUnit() == null) {
            diag().report(BindingCode.VALUE_TYPE, a.valuePos(), "'" + name + "' takes no plain numbers",
                    "bind a String with its CSS value");
            return null;
        }
        return new StyleBinding(ref.cell(), a, diag(), e, property);
    }

    /**
     * {@code bind:value} on a form control (a String), {@code bind:checked} on a checkbox and
     * {@code bind:open} on a dialog (a Boolean), {@code bind:scrolltop} (an Integer): the view follows
     * the cell and writes its own changes back to it, so the value must be a bare writable path.
     */
    private Binding twoWay(Element e, Directive a, Expr parsed, Ref ref, String target) {
        Class<?> want = switch (target) {
            case "value" -> String.class;
            case "scrolltop" -> Integer.class;
            default -> Boolean.class;
        };
        boolean writable = parsed instanceof Expr.Path && ref.write() != Ref.Write.NONE;
        if (!writable || Types.boxed(ref.type()) != want) {
            diag().report(BindingCode.TWO_WAY_TARGET, a.valuePos(), "'" + a.name() + "' writes back: it needs a state Value<"
                    + want.getSimpleName() + "> (a Value field, a local state, a row component or a parameter passed "
                    + "writable), but '" + a.value().trim() + "' is " + (writable ? ref.type().getTypeName() : "not one"));
            return null;
        }
        Consumer<Object> writeBack = writer(ref);
        switch (target) {
            case "value" -> {
                e.addEventListener(UiEvent.Type.INPUT, false, ev -> writeBack.accept(e.control().value()));
                return new Binding.ControlValue(ref.cell(), a, diag(), e);
            }
            case "checked" -> {
                e.addEventListener(UiEvent.Type.INPUT, false, ev -> writeBack.accept(e.control().checked()));
                return new Binding.Checked(ref.cell(), a, diag(), e);
            }
            case "open" -> {
                e.addEventListener(UiEvent.Type.CLOSE, false, ev -> writeBack.accept(false));
                return new Binding.Open(ref.cell(), a, diag(), e, topLayer);
            }
            default -> {
                e.addEventListener(UiEvent.Type.SCROLL, false, ev -> writeBack.accept(Math.round(e.scrollTop())));
                return new Binding.Scroll(ref.cell(), a, diag(), e);
            }
        }
    }

    /** Sets a writable {@code ref}; a row whose component is null ignores the write. */
    @SuppressWarnings("unchecked")
    private static Consumer<Object> writer(Ref ref) {
        if (ref.write() == Ref.Write.VALUE) {
            return v -> ((Value<Object>) ref.cell()).set(v);
        }
        return v -> {
            Value<Object> inner = (Value<Object>) MODEL.current(ref.cell());
            if (inner != null) {
                inner.set(v);
            }
        };
    }

    /**
     * {@code on:<event>="statements"}. {@code keydown} and {@code keyup} take a filter
     * {@code .<key>[.<modifier>…]}, {@code mousedown}, {@code mouseup}, {@code click} and
     * {@code mousemove} a filter {@code [.<button>][.<modifier>…]}, with the names of {@link Keys}: the
     * listener runs, and consumes the event, only for that key or button (left when a mouse filter
     * names none; for {@code mousemove} the button held) with exactly the named modifiers among Shift,
     * Ctrl and Alt (a move carries none). Without a filter it runs for every event of the type, except
     * that {@code click} is a left click, as in the DOM.
     *
     * <p>The statements see the event as {@code $event}, an {@link EventInfo}.
     */
    private void handler(Element e, Directive a) {
        String[] parts = a.name().substring("on:".length()).split("\\.", -1);
        String eventName = parts[0];
        UiEvent.Type type = UiEvent.Type.byTemplateName(eventName);
        if (type == null) {
            diag().report(BindingCode.UNKNOWN_EVENT, a.namePos(),
                    "unknown event '" + eventName + "'; supported: " + eventNames());
            return;
        }
        boolean keyEvent = type == UiEvent.Type.KEY_DOWN || type == UiEvent.Type.KEY_UP;
        boolean mouseEvent = type == UiEvent.Type.MOUSE_DOWN || type == UiEvent.Type.MOUSE_UP
                || type == UiEvent.Type.CLICK || type == UiEvent.Type.MOUSE_MOVE;
        int filterKey = -1;
        int filterButton = type == UiEvent.Type.CLICK ? 0 : -1;
        int filterMods = -1;
        if (parts.length > 1) {
            if (!keyEvent && !mouseEvent) {
                diag().report(BindingCode.UNEXPECTED_FILTER, a.namePos(),
                        "'" + eventName + "' takes no filter; only keydown, keyup, mousedown, "
                        + "mouseup, click and mousemove do");
                return;
            }
            int k = 1;
            if (keyEvent) {
                filterKey = Keys.code(parts[1]);
                if (filterKey < 0) {
                    diag().report(BindingCode.UNKNOWN_FILTER, a.namePos(),
                            "unknown key '" + parts[1] + "' in '" + a.name() + "'");
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
                    diag().report(BindingCode.UNKNOWN_FILTER, a.namePos(),
                            "unknown " + (keyEvent || k > 1 ? "modifier" : "button or modifier")
                            + " '" + parts[k] + "' in '" + a.name() + "'; supported: "
                            + (keyEvent || k > 1 ? "" : "left, right, middle, ") + "shift, ctrl, alt");
                    return;
                }
                filterMods |= bit;
            }
        }
        Cell<EventInfo> event = MODEL.held(null);
        List<Runnable> steps = statements(a, new Ref(event, EventInfo.class));
        if (steps == null) {
            return;
        }
        int wantKey = filterKey;
        int wantButton = filterButton;
        int wantMods = filterMods;
        Diagnostics diag = diag();
        // Unfocused key events target the implicit root and run only its listeners, so a top-level
        // element's key handlers live on the root: document-level, like a handler on <body>.
        Element on = keyEvent && e.parent() == documentRoot ? documentRoot : e;
        on.addEventListener(type, false, ev -> {
            if (wantKey >= 0 && ev.key() != wantKey || wantButton >= 0 && ev.button() != wantButton
                    || wantMods >= 0 && (ev.modifiers() & Keys.FILTER_MODIFIERS) != wantMods) {
                return;
            }
            try {
                MODEL.replace(event, new EventInfo(ev.x(), ev.y(), ev.movementX(), ev.movementY(), ev.wheelDelta()));
                for (int i = 0, n = steps.size(); i < n; i++) {
                    steps.get(i).run();
                }
            } catch (RuntimeException ex) {
                diag.report(BindingCode.RUNTIME_EXCEPTION, a.valuePos(), "'" + a.value() + "' threw " + ex);
            }
            ev.consume();
        });
    }

    /** {@code action}, {@code action(expression)}, or the same for a component event; {@code null} after reporting. */
    private Runnable call(Directive a, Expr.Call c) {
        String name = c.action();
        Name target = env.names().get(name);
        if (target instanceof Name.Event port) {
            return emit(a, c, port);
        }
        if (target instanceof Name.Unset) {
            diag().report(BindingCode.MODEL_NULL_FIELD, at(a, c.at()),
                    "field '" + name + "' of " + env.owner() + " is null");
            return null;
        }
        if (!(target instanceof Name.Act act)) {
            // A name that is not visible at all is the unknown-name mistake, whatever the call expected.
            diag().report(target == null ? BindingCode.UNKNOWN_NAME : BindingCode.NOT_CALLABLE, at(a, c.at()),
                    env.component() ? "'" + name + "' is not an event of " + env.owner()
                            : "'" + name + "' is not an Action field of " + env.owner());
            return null;
        }
        Action<Object> action = act.action();
        Type param = act.param();
        boolean wantsArg = param != null && param != Void.class;
        if (c.arg() == null && wantsArg) {
            diag().report(BindingCode.ARGUMENT_ARITY, at(a, c.at()), "'" + name + "' takes a " + param.getTypeName(),
                    "pass it as " + name + "(value)");
            return null;
        }
        if (c.arg() != null && !wantsArg) {
            diag().report(BindingCode.ARGUMENT_ARITY, at(a, c.at()), "'" + name + "' takes no argument");
            return null;
        }
        if (c.arg() == null) {
            return () -> action.run(null);
        }
        Ref arg = compile(a, c.arg());
        if (arg == null) {
            return null;
        }
        boolean checkable = param instanceof Class<?> || param instanceof ParameterizedType;
        if (checkable && !Types.assignable(arg.type(), param)) {
            diag().report(BindingCode.ARGUMENT_TYPE, at(a, c.arg().at()), "'" + name + "' takes a "
                    + param.getTypeName() + ", but " + describe(c.arg()) + " is " + arg.type().getTypeName());
            return null;
        }
        Cell<?> cell = arg.cell();
        return () -> action.run(Types.convert(cell.get(), param));
    }

    /** A component template raising one of its events; every call of an event must carry the same payload type. */
    private Runnable emit(Directive a, Expr.Call c, Name.Event port) {
        Ref arg = c.arg() == null ? null : compile(a, c.arg());
        if (c.arg() != null && arg == null) {
            return null;
        }
        Type type = arg == null ? null : arg.type();
        if (!port.called) {
            port.called = true;
            port.payload = type;
        } else if (!Objects.equals(port.payload, type)) {
            diag().report(BindingCode.EVENT_PAYLOAD, at(a, c.at()), "event '" + port.name + "' is raised with "
                    + (type == null ? "no payload" : type.getTypeName()) + " here, but with "
                    + (port.payload == null ? "no payload" : port.payload.getTypeName()) + " elsewhere");
            return null;
        }
        if (arg == null) {
            return () -> port.fire(null);
        }
        Cell<?> cell = arg.cell();
        return () -> port.fire(cell.get());
    }

    /** {@code path = expression}; {@code null} after reporting. */
    private Runnable assign(Directive a, Expr.Assign s) {
        Ref target = resolve(a, s.target());
        Ref value = compile(a, s.value());
        if (target == null || value == null) {
            return null;
        }
        String path = s.target().text();
        if (target.write() == Ref.Write.NONE) {
            diag().report(BindingCode.NOT_ASSIGNABLE, at(a, s.at()),
                    "'" + path + "' cannot be assigned: only local states, Value fields, "
                    + "row components of type Value<T> and parameters passed writable can");
            return null;
        }
        if (!Types.assignable(value.type(), target.type())) {
            diag().report(BindingCode.ASSIGNMENT_TYPE, at(a, s.value().at()),
                    "'" + path + "' is " + target.type().getTypeName() + ", but "
                    + describe(s.value()) + " is " + value.type().getTypeName());
            return null;
        }
        Consumer<Object> write = writer(target);
        Cell<?> cell = value.cell();
        Type type = target.type();
        return () -> write.accept(Types.convert(cell.get(), type));
    }

    private static String describe(Expr e) {
        return e instanceof Expr.Path p ? "'" + p.text() + "'" : "the value";
    }

    /** The parsed value of {@code a}; {@code null} after reporting a syntax error. */
    private Expr parse(Directive a) {
        try {
            return Expr.parseExpression(a.value());
        } catch (Expr.SyntaxError ex) {
            diag().report(ExprCode.SYNTAX, at(a, ex.at), ex.getMessage());
            return null;
        }
    }

    /** A bare path is its own cell, so two-way bindings and assignments can write it; anything else is derived. */
    private Ref compile(Directive a, Expr e) {
        if (e instanceof Expr.Path p) {
            return resolve(a, p);
        }
        Expressions.Compiled c = Expressions.compile(e, p -> resolve(a, p), text::plain,
                (code, offset, message) -> diag().report(code, at(a, offset), message));
        return c == null ? null : new Ref(MODEL.derived(c.inputs(), c.eval()), c.type());
    }

    private Ref expression(Directive a) {
        Expr e = parse(a);
        return e == null ? null : compile(a, e);
    }

    /** Resolves {@code name(.member)*}; reports and returns {@code null} on failure. */
    private Ref resolve(Directive a, Expr.Path path) {
        String first = path.names().get(0);
        Name name = env.names().get(first);
        Ref ref;
        int next = 1;
        if (name == null) {
            diag().report(first.startsWith("$") ? BindingCode.UNAVAILABLE_NAME : BindingCode.UNKNOWN_NAME,
                    at(a, path.at()), "'" + first + "' is not "
                    + (first.startsWith("$") ? "available here" : env.component()
                    ? "a parameter, event, row or state of " + env.owner() : "a field of " + env.owner()));
            return null;
        } else if (name instanceof Name.Unset) {
            diag().report(BindingCode.MODEL_NULL_FIELD, at(a, path.at()),
                    "field '" + first + "' of " + env.owner() + " is null");
            return null;
        } else if (name instanceof Name.Event) {
            diag().report(BindingCode.NOT_A_VALUE, at(a, path.at()), "'" + first + "' is an event of " + env.owner()
                    + "; it can only be called, and has no pending or error");
            return null;
        } else if (name instanceof Name.Act act) {
            String member = path.names().size() > 1 ? path.names().get(1) : "";
            switch (member) {
                case "pending" -> ref = new Ref(act.action().pending(), Boolean.class);
                case "error" -> ref = new Ref(act.action().error(), Throwable.class);
                default -> {
                    diag().report(BindingCode.NOT_A_VALUE, at(a, path.at()), "an action is not a value",
                            "use '" + first + ".pending' or '" + first + ".error'");
                    return null;
                }
            }
            next = 2;
        } else {
            ref = (Ref) name;
        }
        for (int i = next; i < path.names().size(); i++) {
            ref = member(a, path, i, ref);
            if (ref == null) {
                return null;
            }
        }
        return ref;
    }

    /** Member {@code i} of {@code path} on {@code base}: a record component, or a List's {@code size}. */
    private Ref member(Directive a, Expr.Path path, int i, Ref base) {
        String name = path.names().get(i);
        SourcePos pos = at(a, path.offsets().get(i));
        Class<?> raw = Types.rawClass(base.type());
        if (List.class.isAssignableFrom(raw) && name.equals("size")) {
            return new Ref(MODEL.derived(new Cell<?>[]{base.cell()},
                    args -> args[0] == null ? null : ((List<?>) args[0]).size()), Integer.class);
        }
        if (!raw.isRecord()) {
            diag().report(BindingCode.UNKNOWN_MEMBER, pos, "'" + name + "' in '" + path.text() + "': "
                    + base.type().getTypeName()
                    + (List.class.isAssignableFrom(raw) ? " has only 'size'" : " is not a record"));
            return null;
        }
        for (RecordComponent rc : raw.getRecordComponents()) {
            if (rc.getName().equals(name)) {
                MethodHandle accessor = accessor(rc.getAccessor());
                Cell<?> cell = MODEL.derived(new Cell<?>[]{base.cell()}, args -> {
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
                if (Cell.class.isAssignableFrom(Types.rawClass(type))) {
                    Type inner = Types.typeArgument(type);
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Cell<?> flat = MODEL.flat((Cell) cell);
                    return new Ref(flat, inner == null ? Object.class : inner,
                            Types.rawClass(type) == Value.class ? Ref.Write.ROW : Ref.Write.NONE);
                }
                return new Ref(cell, type);
            }
        }
        diag().report(BindingCode.UNKNOWN_MEMBER, pos, raw.getSimpleName() + " has no component '" + name + "'");
        return null;
    }

    /** The position of character {@code offset} of {@code a}'s value. */
    private static SourcePos at(Directive a, int offset) {
        SourcePos p = a.valuePos();
        String v = a.value();
        int line = p.line();
        int column = p.column();
        for (int k = 0; k < offset && k < v.length(); k++) {
            if (v.charAt(k) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new SourcePos(p.file(), line, column);
    }

    private static String eventNames() {
        List<String> names = new ArrayList<>();
        for (UiEvent.Type t : UiEvent.Type.values()) {
            names.add(t.templateName());
        }
        return String.join(", ", names);
    }

    private static MethodHandle accessor(Method m) {
        try {
            m.setAccessible(true);
            return MethodHandles.lookup().unreflect(m);
        } catch (IllegalAccessException | RuntimeException ex) {
            throw new IllegalStateException("cannot read " + m, ex);
        }
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

    /** {@code state:name="init"} or a parameter's default: sets the state once, on the first update after it is built. */
    private static final class StateInit implements Part {
        private final Value<Object> state;
        private final Cell<?> init;
        private final Directive attribute;
        private final Diagnostics diag;
        private boolean done;

        StateInit(Value<Object> state, Cell<?> init, Directive attribute, Diagnostics diag) {
            this.state = state;
            this.init = init;
            this.attribute = attribute;
            this.diag = diag;
        }

        @Override
        public boolean update() {
            if (!done) {
                done = true;
                try {
                    MODEL.replace(state, init.get());
                } catch (RuntimeException ex) {
                    diag.report(BindingCode.RUNTIME_EXCEPTION, attribute.valuePos(),
                            "'" + attribute.value() + "' threw " + ex);
                }
            }
            return false;
        }
    }
}
