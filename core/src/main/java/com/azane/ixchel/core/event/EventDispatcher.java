package com.azane.ixchel.core.event;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextField;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.layout.LayoutEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns host input into tree events against the last laid-out frame: hit testing, capture / target /
 * bubble dispatch, the {@code :hover} / {@code :active} / {@code :focus} state bits, and focus.
 *
 * <p>Hit testing follows paint order: a later sibling paints on top and wins; {@code display: none}
 * subtrees are skipped and scroll containers ({@code overflow} other than {@code visible}) clip their
 * descendants and move them up by their {@link Element#scrollTop()}, as paint does. Text is not a
 * target. {@code :hover} and {@code :active} cover the target and its ancestors, as in CSS.
 *
 * <p>Wheel default action: a wheel event no listener {@linkplain UiEvent#consume() consumed} scrolls
 * the nearest {@code overflow: auto} or {@code scroll} element at or above the target that can still
 * move that way, by {@link #WHEEL_STEP} (18) GUI pixels, two lines of vanilla text, per wheel unit; at its
 * limit the scroll goes to the next such ancestor. Hover is then re-tested under the pointer.
 *
 * <p>An element is focusable when it has a {@code tabindex} attribute or is an {@code <input>}.
 * Pressing a mouse button focuses the nearest focusable element under the pointer, or clears focus;
 * Tab and Shift+Tab move focus in document order. Key events go to the focused element, or to the
 * root when nothing is focused.
 *
 * <p>Text input: while an {@code <input>} is focused, a key event no listener consumed edits it
 * ({@link TextEditing}), and the input takes every key but Escape and Tab, so host shortcuts (the
 * inventory key, hotbar numbers) do not act while typing. {@link #charTyped} inserts at the caret; a
 * left press places the caret (with Shift, extends the selection). Each change of the value
 * dispatches {@link UiEvent.Type#INPUT} at the input.
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
 * or character a focused input took, or an event a listener {@linkplain UiEvent#consume() consumed}.
 * The host falls back to its own handling otherwise. Client thread only.
 */
public final class EventDispatcher {
    /** GUI pixels scrolled per unit of wheel delta (one notch of a usual mouse wheel). */
    public static final float WHEEL_STEP = 18;

    private static final int KEY_ESCAPE = 256;

    private final Element root;
    private final LayoutEngine layout;
    private final List<Element> hovered = new ArrayList<>();
    private final List<Element> active = new ArrayList<>();
    private final List<Element> path = new ArrayList<>();
    private Element focused;
    private Element pressed;
    private int pressedButton = -1;
    private float mouseX = Float.NaN;
    private float mouseY = Float.NaN;
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

    /** @param layout measures input text to place the caret where it is pressed */
    public EventDispatcher(Element root, LayoutEngine layout) {
        this.root = root;
        this.layout = layout;
    }

    /** The clipboard text inputs copy to and paste from. */
    public void setClipboard(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    public Element focused() {
        return focused;
    }

    /** The deepest element at {@code (x, y)}, or the root when the point hits nothing. */
    public Element hitTest(float x, float y) {
        Element hit = hit(root, x, y);
        return hit == null ? root : hit;
    }

    public boolean mouseMoved(float x, float y) {
        mouseX = x;
        mouseY = y;
        Element target = hitTest(x, y);
        setStatePath(hovered, target, Element.HOVER);
        return target != root;
    }

    /** {@code modifiers}: the GLFW modifier bits held, which mouse filters compare. */
    public boolean mousePressed(float x, float y, int button, int modifiers) {
        mouseMoved(x, y);
        Element target = hitTest(x, y);
        if (inDisabled(target)) {
            // Taken, so the host does not act on it (vanilla would drop a carried stack), but no event.
            pressed = null;
            pressedButton = -1;
            return true;
        }
        focus(focusableAncestor(target));
        if (button == 0) {
            setStatePath(active, target, Element.ACTIVE);
            if (target.textField() != null) {
                placeCaret(target, x, (modifiers & Keys.MOD_SHIFT) != 0);
            }
        }
        pressed = target;
        pressedButton = button;
        UiEvent e = UiEvent.mouse(UiEvent.Type.MOUSE_DOWN, target, x, y, button, modifiers);
        dispatch(e);
        return target != root || e.isConsumed();
    }

    public boolean mouseReleased(float x, float y, int button, int modifiers) {
        mouseMoved(x, y);
        Element target = hitTest(x, y);
        if (button == 0) {
            setStatePath(active, root, Element.ACTIVE);
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
                dispatch(click);
                consumed |= click.isConsumed();
            }
        }
        pressed = null;
        pressedButton = -1;
        return consumed;
    }

    public boolean mouseScrolled(float x, float y, double delta) {
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
        UiEvent e = UiEvent.key(UiEvent.Type.KEY_DOWN, focused != null ? focused : root, key, modifiers);
        dispatch(e);
        if (e.isConsumed()) {
            return true;
        }
        if (key == UiEvent.KEY_TAB) {
            return moveFocus((modifiers & UiEvent.MOD_SHIFT) != 0);
        }
        TextField field = focusedField();
        if (field == null || key == KEY_ESCAPE) {
            return false;
        }
        if (TextEditing.key(field, key, modifiers, clipboard, maxLength(focused))) {
            dispatch(UiEvent.of(UiEvent.Type.INPUT, focused));
        }
        return true;
    }

    public boolean keyReleased(int key, int modifiers) {
        UiEvent e = UiEvent.key(UiEvent.Type.KEY_UP, focused != null ? focused : root, key, modifiers);
        dispatch(e);
        return e.isConsumed() || focusedField() != null && key != KEY_ESCAPE && key != UiEvent.KEY_TAB;
    }

    /**
     * A typed character (a Unicode code point, already filtered by the host to what text may hold):
     * inserted at the caret of the focused input; whether one took it.
     */
    public boolean charTyped(int codePoint, int modifiers) {
        TextField field = focusedField();
        if (field == null) {
            return false;
        }
        if (TextEditing.insert(field, Character.toString(codePoint), maxLength(focused))) {
            dispatch(UiEvent.of(UiEvent.Type.INPUT, focused));
        }
        return true;
    }

    private TextField focusedField() {
        return focused == null ? null : focused.textField();
    }

    /** The {@code maxlength} attribute, or -1 when absent or not a number. */
    private static int maxLength(Element input) {
        String raw = input.attribute("maxlength");
        if (raw == null) {
            return -1;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Puts the caret of {@code input} at the character boundary nearest to {@code x}, or extends the selection there. */
    private void placeCaret(Element input, float x, boolean extend) {
        TextField field = input.textField();
        LayoutBox b = input.box();
        float size = input.computed().get(CssProperties.FONT_SIZE).value();
        float at = x - (b.x() + b.borderLeft() + b.paddingLeft()) + field.scrollX();
        String v = field.value();
        int best = 0;
        float bestDistance = Math.abs(at);
        for (int i = 0; i < v.length(); ) {
            i = Character.offsetByCodePoints(v, i, 1);
            float d = Math.abs(layout.textWidth(v.substring(0, i), size) - at);
            if (d >= bestDistance) {
                break;
            }
            best = i;
            bestDistance = d;
        }
        field.select(extend ? field.anchor() : best, best);
    }

    /**
     * Called after a layout pass or a repaint for a scroll offset change: elements may have moved under
     * a still pointer or left the tree, so hover is re-tested and detached elements lose focus and
     * press state.
     */
    public void afterLayout() {
        if (focused != null && (!isConnected(focused) || inDisabled(focused))) {
            focus(null);
        }
        if (!active.isEmpty() && !isConnected(active.get(0))) {
            setStatePath(active, root, Element.ACTIVE);
        }
        if (!Float.isNaN(mouseX)) {
            setStatePath(hovered, hitTest(mouseX, mouseY), Element.HOVER);
        }
        dispatchScrollEvents();
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

    /** {@code (x, y)} in the coordinates {@code e} is laid out in, i.e. undone by the scroll offsets above it. */
    private static Element hit(Element e, float x, float y) {
        if (e.computed() == null || e.computed().get(CssProperties.DISPLAY) == Keywords.Display.NONE) {
            return null;
        }
        LayoutBox b = e.box();
        boolean inside = x >= b.x() && y >= b.y() && x < b.x() + b.width() && y < b.y() + b.height();
        if (e.computed().get(CssProperties.OVERFLOW) != Keywords.Overflow.VISIBLE) {
            // Descendants are clipped to the padding box.
            boolean inPadding = x >= b.x() + b.borderLeft() && y >= b.y() + b.borderTop()
                    && x < b.x() + b.width() - b.borderRight() && y < b.y() + b.height() - b.borderBottom();
            if (!inPadding) {
                return inside ? e : null;
            }
        }
        float childY = y + e.scrollTop();
        List<Node> children = e.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i) instanceof Element c) {
                Element h = hit(c, x, childY);
                if (h != null) {
                    return h;
                }
            }
        }
        return inside ? e : null;
    }

    /** Scrolls the nearest wheel-scrollable element from {@code from} up that moves by {@code dy}; whether one did. */
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
            focused.setState(Element.FOCUS, false);
        }
        focused = next;
        if (next != null) {
            next.setState(Element.FOCUS, true);
        }
    }

    private boolean moveFocus(boolean backwards) {
        List<Element> order = new ArrayList<>();
        collectFocusable(root, order);
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
        for (Node n : e.children()) {
            if (n instanceof Element c) {
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
        return e.attribute("tabindex") != null || e.textField() != null;
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

    private boolean isConnected(Element e) {
        for (Element a = e; a != null; a = a.parent()) {
            if (a == root) {
                return true;
            }
        }
        return false;
    }
}
