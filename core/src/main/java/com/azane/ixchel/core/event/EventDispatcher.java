package com.azane.ixchel.core.event;

import com.azane.ixchel.core.css.property.Affine;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.css.property.Transform;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.layout.Scrollbar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns host input into tree events against the last laid-out frame: hit testing, capture / target /
 * bubble dispatch, the {@code :hover} / {@code :active} / {@code :focus} state bits, focus, and the
 * default actions of the form controls and the {@link TopLayer}.
 *
 * <p>{@code transform} moves where an element is hit, as it moves where it paints: the pointer is
 * mapped through the inverse transforms on the way down, and an element scaled to nothing is not hit.
 * Caret placement and range dragging use the pointer in the element's own coordinates.
 *
 * <p>Hit testing follows paint order: the top layer first, its last entry first (an open select's
 * options; an open dialog, which takes every point outside its content too: everything below it is
 * inert), then the tree, where a later sibling paints on top and wins; {@code display: none}
 * subtrees are skipped and scroll containers ({@code overflow} other than {@code visible}) clip their
 * descendants and move them up by their {@link Element#scrollTop()}, as paint does. Text is not a
 * target. {@code :hover} and {@code :active} cover the target and its ancestors, as in CSS.
 *
 * <p>Wheel default action: a wheel event no listener {@linkplain UiEvent#consume() consumed} scrolls
 * the nearest {@code overflow: auto} or {@code scroll} element at or above the target that can still
 * move that way, by {@link #WHEEL_STEP} (18) GUI pixels, two lines of vanilla text, per wheel unit; at its
 * limit the scroll goes to the next such ancestor, but not past an open dialog. Hover is then
 * re-tested under the pointer. A wheel event closes an open select first.
 *
 * <p>A left press on a {@link Scrollbar} (that of the target or of a scroll container above it) is
 * taken by it and dispatches nothing, not even its release: on the thumb, the thumb follows the
 * pointer until the button is released; on the track, the element scrolls by its padding box height
 * towards the pointer. This works inside disabled elements too, as the wheel does.
 *
 * <p>Drag and drop: a left press in an element with {@code draggable="true"} (the nearest such
 * ancestor; not on a form control) that then moves the pointer more than {@value #DRAG_THRESHOLD}
 * pixels (|dx| + |dy|) starts dragging it: see {@link UiEvent.Type} for the events. While it lasts its
 * {@code <ix-drag-preview>} child, if any, is shown at the pointer in place of any tooltip; hover
 * follows the pointer as usual, so {@code :hover} marks the drop target. Escape, or the source leaving
 * the tree, cancels it: no {@code drop}, the {@code dragend} still comes (a removed source's own
 * listeners only) and the release dispatches nothing.
 *
 * <p>An element is focusable when it has a {@code tabindex} attribute or is a form control
 * ({@code <input>}, {@code <select>}) or a {@code <button>}. Pressing a mouse button focuses the
 * nearest focusable element under the pointer, or clears focus; Tab and Shift+Tab move focus in
 * document order, inside the open dialog while there is one. Key events go to the focused element,
 * or to the root when nothing is focused. Opening a dialog focuses its first focusable element;
 * closing it gives focus back to the element that had it before.
 *
 * <p>Text input: while a text or number {@code <input>} is focused, a key event no listener consumed
 * edits it through the element's interaction facet, and the input takes every key but Escape and Tab, so host shortcuts
 * (the inventory key, hotbar numbers) do not act while typing. {@link #charTyped} inserts at the caret
 * (a number field takes only {@code 0-9 . - + e E}); a left press places the caret (with Shift,
 * extends the selection). Up and Down step a number field by {@code step} within
 * {@code min}/{@code max}. Each change of the value dispatches {@link UiEvent.Type#INPUT} at the
 * input; leaving it or pressing Enter after a change dispatches {@link UiEvent.Type#CHANGE}.
 *
 * <p>Other controls, each change dispatching {@code INPUT} then {@code CHANGE} unless noted: a
 * checkbox toggles on click and Space. A range follows the pointer while the left button that
 * pressed it is down ({@code INPUT} per move, {@code CHANGE} on release), and steps with the arrow
 * keys, Page Up/Down (a tenth of the range), Home and End. A {@code <select>} opens its options into
 * the top layer on click, Enter, Space or Alt+Down; Up and Down step the selection whether it is open
 * or not; clicking an option picks it and closes the list; Enter closes it. A press outside the
 * topmost open select closes it and is not dispatched. A {@code <button>} dispatches
 * {@link UiEvent.Type#CLICK} on Enter and Space.
 *
 * <p>Escape, while the top layer has an entry, closes the topmost one before anything else sees the
 * key: a select's list, or a dialog, which then dispatches {@link UiEvent.Type#CLOSE} (its
 * {@code bind:open} writes {@code false}).
 *
 * <p>The host's {@link ClickSound} plays when a left click lands in a {@code <button>} (before its
 * {@code click} listeners run, whether they consume it or not) or Enter or Space activates one, when a
 * checkbox toggles and when a select opens, unless that control's {@code -ix-click-sound} is
 * {@code none}. Disabled controls take no clicks and so play nothing.
 *
 * <p>{@link UiEvent.Type#SCROLL} is dispatched at each element whose scroll offset changed: right
 * after a wheel scroll (so the next frame's bindings see it) and after each layout or repaint, which
 * covers offsets set by code and offsets the layout clamped.
 *
 * <p>Inside an element with a {@code disabled} attribute, mouse buttons dispatch no events and change
 * neither focus nor {@code :active} (the input still counts as taken), Tab skips focusable elements,
 * and focus there is dropped after the next layout. Hover and the wheel work as usual.
 *
 * <p>Every input method returns whether the UI took the input: a mouse event over any element, a key
 * or character a focused control took, or an event a listener {@linkplain UiEvent#consume() consumed}.
 * The host falls back to its own handling otherwise. Client thread only.
 */
public final class EventDispatcher {
    /** GUI pixels scrolled per unit of wheel delta (one notch of a usual mouse wheel). */
    public static final float WHEEL_STEP = 18;

    private static final int KEY_ESCAPE = 256;
    /** How far a left press in a {@code draggable} element must move the pointer (|dx| + |dy|) to start a drag. */
    public static final float DRAG_THRESHOLD = 3;

    private final Element root;
    private final LayoutEngine layout;
    private final TopLayer top;
    private final List<Element> hovered = new ArrayList<>();
    private final List<Element> active = new ArrayList<>();
    private final List<Element> path = new ArrayList<>();
    /** Open dialogs and the element focused before each opened, in opening order. */
    private final Map<Element, Element> returnFocus = new LinkedHashMap<>();
    private Element focused;
    private Element pressed;
    private int pressedButton = -1;
    /** A press that closed a select: its release dispatches nothing either. */
    private boolean swallowed;
    private Element dragging;
    /** The scroll container whose thumb the left button is dragging, and where on the thumb it holds. */
    private Element scrolling;
    private float grab;
    /** The {@code draggable} element a left press landed in, while the press lasts and no drag started. */
    private Element dragCandidate;
    private float pressX;
    private float pressY;
    private Element dragSource;
    private float mouseX = Float.NaN;
    private float mouseY = Float.NaN;
    /** The second result of {@link #localX}. */
    private float localY;
    private Clipboard clipboard = new Clipboard() {
        private String text = "";

        @Override
        public String get() {
            return text;
        }

        @Override
        public void set(String text) {
            this.text = text;
        }
    };
    private ClickSound clickSound = () -> {
    };

    private final InteractionFacet.Actions actions = new InteractionFacet.Actions() {
        @Override public void dispatch(UiEvent e) { EventDispatcher.this.dispatch(e); }
        @Override public void clickSound(Element e) { EventDispatcher.this.clickSound(e); }
        @Override public boolean isOpen(Element e) { return top.isOpen(e); }
        @Override public void open(Element e) { top.open(e); }
        @Override public void close(Element e) { top.close(e); }
        @Override public Clipboard clipboard() { return clipboard; }
        @Override public float textWidth(String text, float size) { return layout.textWidth(text, size); }
    };

    /**
     * @param layout measures input text to place the caret where it is pressed
     * @param top    the document's top layer, shared with layout and paint
     */
    public EventDispatcher(Element root, LayoutEngine layout, TopLayer top) {
        this.root = root;
        this.layout = layout;
        this.top = top;
    }

    /** The clipboard text inputs copy to and paste from. */
    public void setClipboard(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    /** The sound a control plays when clicked; see {@link ClickSound}. */
    public void setClickSound(ClickSound clickSound) {
        this.clickSound = clickSound;
    }

    /** The element being dragged ({@code draggable="true"}), or {@code null}. */
    public Element dragSource() {
        return dragSource;
    }

    public Element focused() {
        return focused;
    }

    /** The deepest element at {@code (x, y)}, or the root when the point hits nothing. */
    public Element hitTest(float x, float y) {
        List<Element> entries = top.entries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            Element entry = entries.get(i);
            LayerFacet presentation = entry.type().facet(LayerFacet.KEY);
            for (int j = presentation.contentCount(entry) - 1; j >= 0; j--) {
                Element child = presentation.contentAt(entry, j);
                if (child != null) {
                    Element hit = hit(child, x, y);
                    if (hit != null) return hit;
                }
            }
            if (presentation.modal()) return entry;
        }
        Element hit = hit(root, x, y);
        return hit == null ? root : hit;
    }

    /**
     * Where the center of {@code e}'s border box paints, the point {@link #hitTest} maps back to it:
     * through the transforms and scroll offsets on it and above it, up to placed top-layer content
     * ({@link Element#isPlaced()}), which paints alone.
     *
     * @return {@code {x, y}} in document coordinates
     */
    public static float[] paintedCenter(Element e) {
        LayoutBox b = e.box();
        return paintedPoint(e, b.x() + b.width() / 2, b.y() + b.height() / 2);
    }

    /** Where the point {@code (x, y)} of the coordinates {@code e}'s box is laid out in paints; see {@link #paintedCenter}. */
    public static float[] paintedPoint(Element e, float x, float y) {
        for (Element a = e; a != null; a = a.parent()) {
            LayoutBox ab = a.box();
            Affine m = a.computed() == null ? null : Transform.of(a.computed(), ab.x(), ab.y(), ab.width(), ab.height());
            if (m != null) {
                float mx = m.x(x, y);
                y = m.y(x, y);
                x = mx;
            }
            if (a.isPlaced() || a.parent() == null) {
                break;
            }
            y -= a.parent().scrollTop();
        }
        return new float[]{x, y};
    }

    public boolean mouseMoved(float x, float y) {
        float movementX = Float.isNaN(mouseX) ? 0 : x - mouseX;
        float movementY = Float.isNaN(mouseY) ? 0 : y - mouseY;
        mouseX = x;
        mouseY = y;
        Element target = hitTest(x, y);
        setStatePath(hovered, target, Element.HOVER);
        if (dragging != null) {
            interaction(dragging).move(dragging, localX(dragging, x, y), actions);
        }
        Scrollbar bar = scrolling == null ? null : Scrollbar.of(scrolling);
        if (bar != null) {
            localX(scrolling, x, y);
            scrolling.setScrollTop(bar.scrollTopFor(localY - grab, scrolling.maxScrollTop()));
            dispatchScrollEvents();
        }
        if (dragCandidate != null && pressed != null
                && Math.abs(x - pressX) + Math.abs(y - pressY) > DRAG_THRESHOLD) {
            startDrag(x, y);
            return true;
        }
        if (dragSource != null) {
            if (movementX != 0 || movementY != 0) {
                top.showAtPointer(previewOf(dragSource), x, y);
                dispatch(UiEvent.drag(UiEvent.Type.DRAG, dragSource, x, y, movementX, movementY));
            }
            return true;
        }
        if (movementX == 0 && movementY == 0) {
            return target != root;
        }
        // While a button is held its press target keeps the moves, as a drag must not stop at its edge.
        UiEvent e = UiEvent.move(pressed != null ? pressed : target, x, y, movementX, movementY,
                pressed != null ? pressedButton : -1);
        dispatch(e);
        return target != root || e.isConsumed();
    }

    /** {@code modifiers}: the GLFW modifier bits held, which mouse filters compare. */
    public boolean mousePressed(float x, float y, int button, int modifiers) {
        mouseMoved(x, y);
        Element target = hitTest(x, y);
        Element list = top.top();
        if (list != null && list.type().facet(LayerFacet.KEY).dismissOutside()
                && !list.type().facet(LayerFacet.KEY).contains(list, target)) {
            top.remove(list);
            swallowed = true;
            pressed = null;
            pressedButton = -1;
            return true;
        }
        if (top.isOpen(target) && target.type().facet(LayerFacet.KEY).modal() && !inBox(target, localX(target, x, y), localY)) {
            // The backdrop: everything under it is inert, and focus stays inside the dialog.
            swallowed = true;
            pressed = null;
            pressedButton = -1;
            return true;
        }
        if (button == 0 && pressScrollbar(target, x, y)) {
            swallowed = true;
            pressed = null;
            pressedButton = -1;
            return true;
        }
        swallowed = false;
        if (inDisabled(target)) {
            // Taken, so the host does not act on it (vanilla would drop a carried stack), but no event.
            pressed = null;
            pressedButton = -1;
            return true;
        }
        focus(focusableAncestor(target));
        if (button == 0) {
            setStatePath(active, target, Element.ACTIVE);
            interaction(target).beforePress(target, localX(target, x, y), modifiers, actions);
        }
        pressed = target;
        pressedButton = button;
        UiEvent e = UiEvent.mouse(UiEvent.Type.MOUSE_DOWN, target, x, y, button, modifiers);
        dispatch(e);
        if (button == 0 && !e.isConsumed() && interaction(target).press(target, localX(target, x, y), actions)) {
            dragging = target;
        } else if (button == 0 && interaction(target).dragSourceAllowed()) {
            dragCandidate = draggableOf(target);
            pressX = x;
            pressY = y;
        }
        return target != root || e.isConsumed();
    }

    /**
     * A left press on the scrollbar of {@code target} or of a scroll container above it: on the thumb it
     * starts dragging it, on the track it scrolls a page towards the pointer.
     */
    private boolean pressScrollbar(Element target, float x, float y) {
        for (Element a = target; a != null; a = a.parent()) {
            Scrollbar bar = Scrollbar.of(a);
            if (bar != null && bar.contains(localX(a, x, y), localY)) {
                if (localY >= bar.thumbY() && localY < bar.thumbY() + bar.thumbHeight()) {
                    scrolling = a;
                    grab = localY - bar.thumbY();
                } else {
                    a.setScrollTop(a.scrollTop() + (localY < bar.thumbY() ? -bar.height() : bar.height()));
                    dispatchScrollEvents();
                }
                return true;
            }
            if (a.isPlaced()) {
                break;
            }
        }
        return false;
    }

    /** The nearest element at or above {@code e} with {@code draggable="true"}, within its placed content; or {@code null}. */
    private Element draggableOf(Element e) {
        for (Element a = e; a != null && a != root; a = a.parent()) {
            if ("true".equals(a.attribute("draggable"))) {
                return a;
            }
            if (a.isPlaced()) {
                break;
            }
        }
        return null;
    }

    /** The source's own {@code <ix-drag-preview>} child, or {@code null}. */
    private static Element previewOf(Element source) {
        for (Node n : source.children()) {
            if (n instanceof Element c && c.type().facet(LayerFacet.KEY).pointer() == LayerFacet.Pointer.DRAG_PREVIEW) {
                return c;
            }
        }
        return null;
    }

    /** The candidate's press moved past the threshold: {@code dragstart}, then a first {@code drag} by the whole way. */
    private void startDrag(float x, float y) {
        dragSource = dragCandidate;
        dragCandidate = null;
        top.showAtPointer(previewOf(dragSource), x, y);
        dispatch(UiEvent.drag(UiEvent.Type.DRAG_START, dragSource, x, y, 0, 0));
        if (dragSource != null) {
            dispatch(UiEvent.drag(UiEvent.Type.DRAG, dragSource, x, y, x - pressX, y - pressY));
        }
    }

    /** Ends the drag without an event: the pointer shows nothing until the host picks a tooltip again. */
    private void endDrag() {
        dragSource = null;
        pressed = null;
        pressedButton = -1;
        top.showAtPointer(null, 0, 0);
    }

    /** Escape or a removed source: no drop, {@code dragend} at the source, and its release dispatches nothing. */
    private void cancelDrag() {
        Element source = dragSource;
        endDrag();
        swallowed = true;
        setStatePath(active, root, Element.ACTIVE);
        UiEvent end = UiEvent.drag(UiEvent.Type.DRAG_END, source, mouseX, mouseY, 0, 0);
        if (isConnected(source)) {
            dispatch(end);
        } else {
            // Out of the tree: only its own listeners are left to tell.
            end.enter(source, UiEvent.Phase.TARGET);
            source.runListeners(end);
        }
    }

    public boolean mouseReleased(float x, float y, int button, int modifiers) {
        mouseMoved(x, y);
        if (button == 0 && dragSource != null) {
            Element source = dragSource;
            endDrag();
            setStatePath(active, root, Element.ACTIVE);
            Element over = hitTest(x, y);
            if (!inDisabled(over)) {
                dispatch(UiEvent.drag(UiEvent.Type.DROP, over, x, y, 0, 0));
            }
            dispatch(UiEvent.drag(UiEvent.Type.DRAG_END, source, x, y, 0, 0));
            return true;
        }
        Element target = hitTest(x, y);
        if (button == 0) {
            dragCandidate = null;
            scrolling = null;
            setStatePath(active, root, Element.ACTIVE);
            if (dragging != null) {
                Element captured = dragging;
                dragging = null;
                interaction(captured).release(captured, actions);
            }
        }
        if (swallowed) {
            swallowed = false;
            return true;
        }
        if (inDisabled(target)) {
            pressed = null;
            pressedButton = -1;
            return true;
        }
        UiEvent up = UiEvent.mouse(UiEvent.Type.MOUSE_UP, target, x, y, button, modifiers);
        dispatch(up);
        boolean consumed = target != root || up.isConsumed();
        if (button == pressedButton && pressed != null) {
            Element clicked = commonAncestor(pressed, target);
            if (clicked != root && isConnected(clicked) && !inDisabled(clicked)) {
                UiEvent click = UiEvent.mouse(UiEvent.Type.CLICK, clicked, x, y, button, modifiers);
                Element b = button == 0 ? buttonOf(clicked) : null;
                if (b != null) {
                    clickSound(b);
                }
                dispatch(click);
                consumed |= click.isConsumed();
                if (button == 0 && !click.isConsumed()) {
                    interaction(clicked).click(clicked, actions);
                }
            }
        }
        pressed = null;
        pressedButton = -1;
        return consumed;
    }


    private void clickSound(Element control) {
        if (control.computed().get(CssProperties.CLICK_SOUND) == Keywords.ClickSound.AUTO) {
            clickSound.play();
        }
    }

    /** The nearest {@code <button>} at or above {@code e}, or {@code null}. */
    private Element buttonOf(Element e) {
        for (Element a = e; a != null && a != root; a = a.parent()) {
            if (interaction(a).clickSoundBefore()) {
                return a;
            }
        }
        return null;
    }

    public boolean mouseScrolled(float x, float y, double delta) {
        Element list = top.top();
        if (list != null && list.type().facet(LayerFacet.KEY).dismissOnScroll()) {
            // The list is placed where the select showed at the last layout; a scroll would leave it behind.
            top.remove(list);
        }
        mouseMoved(x, y);
        Element target = hitTest(x, y);
        UiEvent e = UiEvent.wheel(target, x, y, delta);
        dispatch(e);
        if (e.isConsumed()) {
            return true;
        }
        if (scroll(target, (float) -delta * WHEEL_STEP)) {
            setStatePath(hovered, hitTest(x, y), Element.HOVER);
            dispatchScrollEvents();
            return true;
        }
        return target != root;
    }

    /** Key events target the focused element, or the root when nothing is focused (as the DOM's document). */
    public boolean keyPressed(int key, int modifiers) {
        if (key == KEY_ESCAPE && dragSource != null) {
            cancelDrag();
            return true;
        }
        if (key == KEY_ESCAPE && top.top() != null) {
            closeTop();
            return true;
        }
        UiEvent e = UiEvent.key(UiEvent.Type.KEY_DOWN, focused != null ? focused : root, key, modifiers);
        dispatch(e);
        if (e.isConsumed()) {
            return true;
        }
        if (key == UiEvent.KEY_TAB) {
            return moveFocus((modifiers & UiEvent.MOD_SHIFT) != 0);
        }
        if (focused == null || key == KEY_ESCAPE) {
            return false;
        }
        return interaction(focused).key(focused, key, modifiers, actions);
    }

    /** Closes the topmost top-layer entry; a dialog dispatches {@code close}. */
    private void closeTop() {
        Element entry = top.top();
        top.close(entry);
        interaction(entry).close(entry, actions);
        if (entry.type().facet(LayerFacet.KEY).modal()) restoreFocus();
    }

    public boolean keyReleased(int key, int modifiers) {
        UiEvent e = UiEvent.key(UiEvent.Type.KEY_UP, focused != null ? focused : root, key, modifiers);
        dispatch(e);
        return e.isConsumed() || focused != null && interaction(focused).capturesKeys(focused)
                && key != KEY_ESCAPE && key != UiEvent.KEY_TAB;
    }

    /**
     * A typed character (a Unicode code point, already filtered by the host to what text may hold):
     * inserted at the caret of the focused text or number input; whether one took it.
     */
    public boolean charTyped(int codePoint, int modifiers) {
        return focused != null && interaction(focused).character(focused, codePoint, actions);
    }

    /**
     * Called after a layout pass or a repaint for a scroll offset change: elements may have moved under
     * a still pointer or left the tree, so hover is re-tested and detached elements lose focus and
     * press state. Selects that left the tree, were hidden or disabled, or sit below an open dialog
     * close; a newly opened dialog takes focus, and a closed one gives it back.
     */
    public void afterLayout() {
        Element modal = top.modal();
        for (Element entry : List.copyOf(top.entries())) {
            if (entry.type().facet(LayerFacet.KEY).dismissWhenInactive() && (!isConnected(entry) || inDisabled(entry) || !isDisplayed(entry)
                    || modal != null && !isInside(entry, modal))) {
                top.remove(entry);
            }
        }
        restoreFocus();
        if (modal != null && !returnFocus.containsKey(modal)) {
            returnFocus.put(modal, focused);
            List<Element> order = new ArrayList<>();
            collectFocusable(modal, order);
            focus(order.isEmpty() ? null : order.get(0));
        }
        if (focused != null && (!isConnected(focused) || inDisabled(focused) || modal != null && !isInside(focused, modal))) {
            focus(null);
        }
        if (dragging != null && (!isConnected(dragging) || inDisabled(dragging))) {
            dragging = null;
        }
        if (scrolling != null && !isConnected(scrolling)) {
            scrolling = null;
        }
        if (dragCandidate != null && !isConnected(dragCandidate)) {
            dragCandidate = null;
        }
        if (dragSource != null && !isConnected(dragSource)) {
            cancelDrag();
        }
        if (!active.isEmpty() && !isConnected(active.get(0))) {
            setStatePath(active, root, Element.ACTIVE);
        }
        if (!Float.isNaN(mouseX)) {
            setStatePath(hovered, hitTest(mouseX, mouseY), Element.HOVER);
        }
        dispatchScrollEvents();
    }

    /** Gives focus back for each dialog that closed since it opened, latest first. */
    private void restoreFocus() {
        List<Element> dialogs = new ArrayList<>(returnFocus.keySet());
        for (int i = dialogs.size() - 1; i >= 0; i--) {
            Element d = dialogs.get(i);
            if (!top.isOpen(d)) {
                Element previous = returnFocus.remove(d);
                if (previous != null && isConnected(previous) && !inDisabled(previous)) {
                    focus(previous);
                } else if (focused != null && isInside(focused, d)) {
                    focus(null);
                }
            }
        }
    }

    /** {@link UiEvent.Type#SCROLL} at every element whose offset changed since the last time, target only. */
    private void dispatchScrollEvents() {
        if (root.takeScrollPending()) {
            dispatchScrollEvents(root);
        }
    }

    private static void dispatchScrollEvents(Element e) {
        if (e.takeScrolled()) {
            UiEvent ev = UiEvent.of(UiEvent.Type.SCROLL, e);
            ev.enter(e, UiEvent.Phase.TARGET);
            e.runListeners(ev);
        }
        List<Node> children = e.children();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof Element c) {
                dispatchScrollEvents(c);
            }
        }
    }

    private void dispatch(UiEvent e) {
        path.clear();
        for (Element a = e.target(); a != null; a = a.parent()) {
            path.add(a);
        }
        for (int i = path.size() - 1; i > 0; i--) {
            e.enter(path.get(i), UiEvent.Phase.CAPTURE);
            path.get(i).runListeners(e);
            if (e.isPropagationStopped()) {
                return;
            }
        }
        e.enter(path.get(0), UiEvent.Phase.TARGET);
        path.get(0).runListeners(e);
        for (int i = 1; i < path.size() && !e.isPropagationStopped(); i++) {
            e.enter(path.get(i), UiEvent.Phase.BUBBLE);
            path.get(i).runListeners(e);
        }
    }

    /**
     * {@code (x, y)} in the coordinates {@code e} is laid out in, i.e. undone by the scroll offsets and
     * transforms above it; {@code e}'s own transform is undone here. Dialogs and a select's options are
     * top-layer content: hit only from {@link #hitTest}'s layer pass.
     */
    private static Element hit(Element e, float x, float y) {
        if (e.computed() == null || e.computed().get(CssProperties.DISPLAY) == Keywords.Display.NONE) {
            return null;
        }
        LayoutBox b = e.box();
        Affine m = Transform.of(e.computed(), b.x(), b.y(), b.width(), b.height());
        if (m != null) {
            Affine inverse = m.invert();
            if (inverse == null) {
                return null;
            }
            float lx = inverse.x(x, y);
            y = inverse.y(x, y);
            x = lx;
        }
        // An inline element is hit on its line rectangles only, not across its bounding box.
        boolean inside = b.contains(x, y);
        if (!interaction(e).hitChildren()) {
            return inside ? e : null;
        }
        if (e.computed().get(CssProperties.OVERFLOW) != Keywords.Overflow.VISIBLE) {
            // Descendants are clipped to the padding box.
            boolean inPadding = x >= b.x() + b.borderLeft() && y >= b.y() + b.borderTop()
                    && x < b.x() + b.width() - b.borderRight() && y < b.y() + b.height() - b.borderBottom();
            if (!inPadding) {
                return inside ? e : null;
            }
        }
        float childY = y + e.scrollTop();
        // A pseudo-element's box counts as its element's: events and :hover go to the element.
        if (e.after() != null && hit(e.after(), x, childY) != null) {
            return e;
        }
        List<Node> children = e.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i) instanceof Element c && !c.rendersInTopLayer()) {
                Element h = hit(c, x, childY);
                if (h != null) {
                    return h;
                }
            }
        }
        if (e.before() != null && hit(e.before(), x, childY) != null) {
            return e;
        }
        return inside ? e : null;
    }

    /**
     * Scrolls the nearest wheel-scrollable element from {@code from} up that moves by {@code dy}, not
     * past an open dialog; whether one did.
     */
    private static boolean scroll(Element from, float dy) {
        for (Element a = from; a != null; a = a.parent()) {
            Keywords.Overflow o = a.computed() == null ? Keywords.Overflow.VISIBLE : a.computed().get(CssProperties.OVERFLOW);
            if (o == Keywords.Overflow.AUTO || o == Keywords.Overflow.SCROLL) {
                float before = a.scrollTop();
                a.setScrollTop(before + dy);
                if (a.scrollTop() != before) {
                    return true;
                }
            }
            if (a.isPlaced()) {
                return false;
            }
        }
        return false;
    }

    /** Sets {@code bit} on {@code target} and its ancestors below the root, clearing it on {@code current} elsewhere. */
    private void setStatePath(List<Element> current, Element target, int bit) {
        path.clear();
        for (Element a = target; a != null && a != root; a = a.parent()) {
            path.add(a);
        }
        for (Element e : current) {
            if (!path.contains(e)) {
                e.setState(bit, false);
            }
        }
        for (Element e : path) {
            e.setState(bit, true);
        }
        current.clear();
        current.addAll(path);
    }

    private void focus(Element next) {
        if (next == focused) {
            return;
        }
        if (focused != null) {
            interaction(focused).blur(focused, actions);
            focused.setState(Element.FOCUS, false);
            Element list = focused;
            if (top.isOpen(list) && list.type().facet(LayerFacet.KEY).dismissOnBlur()) {
                top.remove(list);
            }
        }
        focused = next;
        if (next != null) {
            next.setState(Element.FOCUS, true);
            interaction(next).focus(next, actions);
        }
    }

    private boolean moveFocus(boolean backwards) {
        List<Element> order = new ArrayList<>();
        Element modal = top.modal();
        collectFocusable(modal != null ? modal : root, order);
        if (order.isEmpty()) {
            return false;
        }
        int i = order.indexOf(focused);
        int next;
        if (i < 0) {
            next = backwards ? order.size() - 1 : 0;
        } else {
            next = Math.floorMod(i + (backwards ? -1 : 1), order.size());
        }
        focus(order.get(next));
        return true;
    }

    /** Focusable elements under {@code e} in document order; closed dialogs are not displayed, open ones are their own scope. */
    private static void collectFocusable(Element e, List<Element> out) {
        if (e.computed() != null && e.computed().get(CssProperties.DISPLAY) == Keywords.Display.NONE) {
            return;
        }
        if (e.hasState(Element.DISABLED)) {
            return;
        }
        if (isFocusable(e)) {
            out.add(e);
        }
        if (!interaction(e).hitChildren()) {
            return;
        }
        for (Node n : e.children()) {
            if (n instanceof Element c && !c.rendersInTopLayer()) {
                collectFocusable(c, out);
            }
        }
    }

    private Element focusableAncestor(Element e) {
        for (Element a = e; a != null && a != root; a = a.parent()) {
            if (isFocusable(a)) {
                return a;
            }
        }
        return null;
    }

    private static boolean isFocusable(Element e) {
        return e.attribute("tabindex") != null || interaction(e).focusable();
    }

    private static InteractionFacet interaction(Element e) {
        return e.type().facet(InteractionFacet.KEY);
    }

    private Element commonAncestor(Element a, Element b) {
        for (Element x = a; x != null; x = x.parent()) {
            for (Element y = b; y != null; y = y.parent()) {
                if (x == y) {
                    return x;
                }
            }
        }
        return root;
    }

    /** Whether {@code e} or an ancestor has a {@code disabled} attribute. */
    private static boolean inDisabled(Element e) {
        for (Element a = e; a != null; a = a.parent()) {
            if (a.hasState(Element.DISABLED)) {
                return true;
            }
        }
        return false;
    }

    /** Whether neither {@code e} nor an ancestor is {@code display: none}. */
    private static boolean isDisplayed(Element e) {
        for (Element a = e; a != null; a = a.parent()) {
            if (a.computed() != null && a.computed().get(CssProperties.DISPLAY) == Keywords.Display.NONE) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInside(Element e, Element ancestor) {
        for (Element a = e; a != null; a = a.parent()) {
            if (a == ancestor) {
                return true;
            }
        }
        return false;
    }

    private boolean isConnected(Element e) {
        return isInside(e, root);
    }

    private static boolean inBox(Element e, float x, float y) {
        LayoutBox b = e.box();
        return x >= b.x() && y >= b.y() && x < b.x() + b.width() && y < b.y() + b.height();
    }

    /**
     * {@code x} of the pointer {@code (x, y)} in the coordinates {@code e}'s box is laid out in, its own
     * transform undone too, as {@link #hit} maps it; {@code y} goes to {@link #localY}. The chain starts at
     * the root, or at the {@linkplain Element#isPlaced() placed} top-layer content {@code e} is in.
     */
    private float localX(Element e, float x, float y) {
        List<Element> chain = new ArrayList<>();
        for (Element a = e; a != null; a = a.parent()) {
            chain.add(a);
            if (a.isPlaced()) {
                break;
            }
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            Element a = chain.get(i);
            LayoutBox b = a.box();
            Affine m = a.computed() == null ? null : Transform.of(a.computed(), b.x(), b.y(), b.width(), b.height());
            Affine inverse = m == null ? null : m.invert();
            if (inverse != null) {
                float lx = inverse.x(x, y);
                y = inverse.y(x, y);
                x = lx;
            }
            if (i > 0) {
                y += a.scrollTop();
            }
        }
        localY = y;
        return x;
    }
}
