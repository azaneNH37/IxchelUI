package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.ModelAccess;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.template.ComponentDef;
import com.azane.ixchel.core.template.TagRegistry;

import java.util.Map;
import java.util.function.Function;

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
    /** Converts a bound value to text: {@code null} is empty, everything else {@link String#valueOf}. */
    public static final Function<Object, String> PLAIN_TEXT = v -> v == null ? "" : String.valueOf(v);

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
     * @param textOf     converts bound values for {@code bind:text} and attribute bindings
     * @param properties the properties {@code bind:style:*} may set
     * @param topLayer   where {@code bind:open} opens and closes dialogs
     */
    public static Bindings compile(Element root, ViewModel model, Map<String, ComponentDef> components,
                                   Function<Object, String> textOf, TagRegistry tags, PropertyRegistry properties,
                                   TopLayer topLayer, Diagnostics diag) {
        ViewModel m = model != null ? model : new ViewModel() {
        };
        String owner = model != null ? model.getClass().getSimpleName() : "the view model (this document has none)";
        Compiler c = new Compiler(root, m, owner, textOf, tags, properties, topLayer, components, diag);
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
