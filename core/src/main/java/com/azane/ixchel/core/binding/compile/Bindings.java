package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.ModelAccess;
import com.azane.ixchel.core.binding.ModelShape;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.StyledText;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.template.ComponentDef;
import com.azane.ixchel.core.template.TagRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * The directives, blocks and component instances of one document, resolved against its view model.
 *
 * <pre>{@code
 * <span bind:text="loaded"></span>          <!-- value as text; the element must be empty -->
 * <div bind:class:empty="count == 0"></div> <!-- Boolean toggles a class -->
 * <img bind:src="icon"/>                    <!-- value as a plain attribute -->
 * <mc-item bind:item="gun"></mc-item>       <!-- host object as a native element's payload -->
 * <div bind:style:width="xpPercent"></div>  <!-- one longhand: a String in its CSS syntax ("37.5%"),
 *                                                a Number as px or a plain number, null to unset -->
 * <span bind:text="selected.name"></span>   <!-- path: record components, read when the record changes -->
 * <span bind:text="rows.size + ' rows'"></span> <!-- expression: literals, paths, ! - + * / %, comparisons,
 *                                                && ||, ?: (see Expr); one Derived over its paths -->
 * <div bind:class:busy="reload.pending"></div>  <!-- an action's pending / error -->
 * <div on:click="reload"></div>             <!-- runs an action; the event counts as handled -->
 * <div on:click="select(row)"></div>        <!-- with the value of an expression as its argument -->
 * <div on:click="open = !open; refresh"></div> <!-- statements: calls and assignments, left to right -->
 * <div on:mousemove="drag($event.movementX)"></div> <!-- $event: the DOM event, an EventInfo -->
 * <div state:open="false">…</div>           <!-- local state of the element and its subtree; its type is
 *                                                the initial value's, evaluated once per built element -->
 * <div on:keydown.delete="remove"></div>    <!-- key events may name the key (see Keys) and modifiers:
 *                                                on:keydown.up.shift fires only for Shift+Up, and plain
 *                                                on:keydown.up only with none of Shift, Ctrl, Alt -->
 * <div on:click.right="half(c)"></div>      <!-- mouse events may name the button and modifiers the same
 *                                                way; on:click.shift is a Shift+left click, and plain
 *                                                on:click any left click -->
 * <input bind:value="query"/>               <!-- two-way: typing sets a Value<String>; also on
 *                                                number and range inputs and on <select> -->
 * <input type="checkbox" bind:checked="on"/> <!-- two-way: a Value<Boolean> -->
 * <dialog bind:open="confirming">…</dialog> <!-- two-way: open while true; Escape closes it and writes false -->
 * <input bind:value="r.text"/>              <!-- inside <for>: a row component of type Value<T> made with
 *                                                state(...) is two-way too; any row component that is a
 *                                                Cell is read through and followed -->
 * <div bind:scrolltop="scroll"></div>       <!-- two-way: scrolling sets a Value<Integer>;
 *                                                a value past the scroll range is clamped after layout
 *                                                and the clamped offset written back -->
 * <div on:keydown.escape="close"></div>     <!-- key handlers on a top-level element act as document-level
 *                                                handlers: they see every key event, focused child or not -->
 * <if test="!hasSelection">…</if>           <!-- mounted while true; not read while unmounted -->
 * <for each="slots" as="s" key="s.type">    <!-- one copy of the content per list element, matched by key -->
 *   <div on:click="select(s)" bind:class:on="s.selected"></div>
 * </for>
 * <ui-confirm bind:open="asking" message="Delete?" on:confirm="remove"></ui-confirm>
 *                                           <!-- a component: parameters, events, slot content -->
 * }</pre>
 *
 * A page's names are its view model's non-private fields, local states and {@code <for>} rows; a
 * component's template sees only its parameters, events, local states and rows. A two-way binding
 * takes a bare path to something writable: a local state, a {@code Value} field, a row component of
 * type {@code Value<T>} or a parameter given a writable path; so does the left side of an assignment.
 * Block attributes are read outside the block's content. Every name, record component and type is
 * checked at load, per component instance, and problems point at their column in the value; problems
 * inside a component's template add {@code (in <name> at file:line:col)} for the instance.
 *
 * <p>A {@code bind:style:<property>} value is an inline declaration of that property: it overrides
 * stylesheet rules (except {@code !important} ones) and replaces the {@code style} attribute's
 * declaration of the same property, and its changes run transitions like any style change. The
 * property must be a supported longhand; a Number is only taken by length (px) and number properties.
 * An invalid value keeps the last valid one and is reported once until a valid value arrives.
 */
public final class Bindings {
    /**
     * Converts bound values to text: {@link #plain} for expressions and attributes, {@link #styled} first
     * for {@code bind:text}, so a host text object (a Minecraft {@code Component}) keeps its colors and
     * formatting there.
     */
    @FunctionalInterface
    public interface TextConverter {
        String plain(Object value);

        /** The value as styled text, or {@code null} to show {@link #plain} text. */
        default StyledText styled(Object value) {
            return null;
        }
    }

    /** {@code null} is empty, everything else {@link String#valueOf}; nothing is styled. */
    public static final TextConverter PLAIN_TEXT = v -> v == null ? "" : String.valueOf(v);

    /** The model internals this package uses; taken once, when the first document compiles. */
    static final ModelAccess MODEL = ModelAccess.claim();

    private final ViewModel model;
    private final Part.Group root;

    private Bindings(ViewModel model, Part.Group root) {
        this.model = model;
        this.root = root;
    }

    /**
     * Resolves every binding under {@code root}, registers event handlers, expands blocks and
     * component instances, and writes the initial values into the DOM. Problems go to {@code diag}
     * with the attribute's position; block contents are checked even while not mounted.
     *
     * @param model      the view model, or {@code null}: the document then sees only its local states
     * @param components the components the document's tags name
     * @param text       converts bound values for {@code bind:text}, attribute bindings and string concatenation
     * @param properties the properties {@code bind:style:*} may set
     * @param topLayer   where {@code bind:open} opens and closes dialogs
     */
    public static Bindings compile(Element root, ViewModel model, Map<String, ComponentDef> components,
                                   TextConverter text, TagRegistry tags, PropertyRegistry properties,
                                   TopLayer topLayer, Diagnostics diag) {
        ViewModel m = model != null ? model : new ViewModel() {
        };
        String owner = model != null ? model.getClass().getSimpleName() : NO_MODEL;
        Map<String, Name> names = names(ModelShape.of(m.getClass()), m, false, root.pos(), diag);
        Compiler c = new Compiler(root, m, names, owner, text, tags, properties, topLayer, components, diag);
        Part.Group group = new Part.Group();
        c.compileRoot(group);
        Bindings b = new Bindings(m, group);
        if (!diag.hasErrors()) {
            // A model reused across reloads may have nothing new to poll; the new DOM still needs its values.
            MODEL.poll(m);
            group.update();
        }
        return b;
    }

    /**
     * Checks every binding under {@code root} as {@link #compile} does, against a view model class
     * instead of an instance: each member stands for a value of its declared type. Nothing is read
     * and no value is written into the DOM, so problems that depend on values are not found.
     *
     * @param shape the view model's shape, or {@code null} for a document without one
     */
    public static void check(Element root, ModelShape shape, Map<String, ComponentDef> components,
                             TextConverter text, TagRegistry tags, PropertyRegistry properties,
                             TopLayer topLayer, Diagnostics diag) {
        ViewModel stub = new ViewModel() {
        };
        String owner = shape != null ? shape.type().getSimpleName() : NO_MODEL;
        Map<String, Name> names = shape != null ? names(shape, stub, true, root.pos(), diag) : Map.of();
        new Compiler(root, stub, names, owner, text, tags, properties, topLayer, components, diag)
                .compileRoot(new Part.Group());
    }

    private static final String NO_MODEL = "the view model (this document has none)";

    /**
     * The names of a page: {@code shape}'s members, holding {@code model}'s field values, or, as
     * {@code placeholders}, stand-ins owned by {@code model} (a writable state for a {@code Value},
     * an empty cell for another cell, an idle action). Names starting with {@code $} belong to the
     * framework.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Name> names(ModelShape shape, ViewModel model, boolean placeholders, SourcePos at,
                                           Diagnostics diag) {
        Map<String, Name> out = new HashMap<>();
        for (ModelShape.Member m : shape.members().values()) {
            if (m.name().startsWith("$")) {
                diag.report(BindingCode.MODEL_RESERVED_NAME, at, "field '" + m.name() + "' of "
                        + m.owner().getSimpleName() + ": names starting with '$' belong to the framework");
                continue;
            }
            Object value;
            if (!placeholders) {
                value = MODEL.read(model, m);
            } else if (m.kind() == ModelShape.Kind.ACTION) {
                value = MODEL.idle(model);
            } else {
                value = m.writable() ? MODEL.state(model) : MODEL.held(null);
            }
            Name name;
            if (value == null) {
                name = new Name.Unset(m.name());
            } else if (m.kind() == ModelShape.Kind.ACTION) {
                name = new Name.Act((Action<Object>) value, m.type());
            } else {
                if (!m.writable() && value instanceof Value<?>) {
                    diag.report(BindingCode.MODEL_UNDECLARED_WRITABLE, at, "field '" + m.name() + "' of "
                                    + m.owner().getSimpleName() + " holds a Value but is declared read-only",
                            "declare it as Value<...> to bind it two-way");
                }
                name = new Ref((Cell<?>) value, m.type(), m.writable() ? Ref.Write.VALUE : Ref.Write.NONE);
            }
            out.put(m.name(), name);
        }
        return out;
    }

    /**
     * Runs posted work, polls the model's sources and writes changed values into the DOM, mounting
     * and unmounting block contents. Returns whether the DOM changed; when no source or state
     * changed, no cell is read. Unmounted contents are not read.
     */
    public boolean update() {
        if (!MODEL.poll(model)) {
            return false;
        }
        return root.update();
    }
}
