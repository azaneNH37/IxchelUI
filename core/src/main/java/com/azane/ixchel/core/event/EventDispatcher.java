package com.azane.ixchel.core.event;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.UiEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns host input into tree events against the last laid-out frame: hit testing, capture / target /
 * bubble dispatch, the {@code :hover} / {@code :active} / {@code :focus} state bits, and focus.
 *
 * <p>Hit testing follows paint order: a later sibling paints on top and wins; {@code display: none}
 * subtrees are skipped and {@code overflow: hidden} clips its descendants. Text is not a target.
 * {@code :hover} and {@code :active} cover the target and its ancestors, as in CSS.
 *
 * <p>An element is focusable when it has a {@code tabindex} attribute. Pressing a mouse button focuses
 * the nearest focusable element under the pointer, or clears focus; Tab and Shift+Tab move focus in
 * document order. Key events go to the focused element.
 *
 * <p>Every input method returns whether the UI took the input: a mouse event over any element, or an
 * event a listener {@linkplain UiEvent#consume() consumed}. The host falls back to its own handling
 * otherwise. Client thread only.
 */
public final class EventDispatcher {
    private final Element root;
    private final List<Element> hovered = new ArrayList<>();
    private final List<Element> active = new ArrayList<>();
    private final List<Element> path = new ArrayList<>();
    private Element focused;
    private Element pressed;
    private int pressedButton = -1;
    private float mouseX = Float.NaN;
    private float mouseY = Float.NaN;

    public EventDispatcher(Element root) {
        this.root = root;
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

    public boolean mousePressed(float x, float y, int button) {
        mouseMoved(x, y);
        Element target = hitTest(x, y);
        focus(focusableAncestor(target));
        if (button == 0) {
            setStatePath(active, target, Element.ACTIVE);
        }
        pressed = target;
        pressedButton = button;
        UiEvent e = UiEvent.mouse(UiEvent.Type.MOUSE_DOWN, target, x, y, button);
        dispatch(e);
        return target != root || e.isConsumed();
    }

    public boolean mouseReleased(float x, float y, int button) {
        mouseMoved(x, y);
        Element target = hitTest(x, y);
        if (button == 0) {
            setStatePath(active, root, Element.ACTIVE);
        }
        UiEvent up = UiEvent.mouse(UiEvent.Type.MOUSE_UP, target, x, y, button);
        dispatch(up);
        boolean consumed = target != root || up.isConsumed();
        if (button == pressedButton && pressed != null) {
            Element clicked = commonAncestor(pressed, target);
            if (clicked != root && isConnected(clicked)) {
                UiEvent click = UiEvent.mouse(UiEvent.Type.CLICK, clicked, x, y, button);
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
        return target != root || e.isConsumed();
    }

    public boolean keyPressed(int key, int modifiers) {
        if (focused != null) {
            UiEvent e = UiEvent.key(UiEvent.Type.KEY_DOWN, focused, key, modifiers);
            dispatch(e);
            if (e.isConsumed()) {
                return true;
            }
        }
        if (key == UiEvent.KEY_TAB) {
            return moveFocus((modifiers & UiEvent.MOD_SHIFT) != 0);
        }
        return false;
    }

    public boolean keyReleased(int key, int modifiers) {
        if (focused == null) {
            return false;
        }
        UiEvent e = UiEvent.key(UiEvent.Type.KEY_UP, focused, key, modifiers);
        dispatch(e);
        return e.isConsumed();
    }

    /**
     * Called after a layout pass: elements may have moved under a still pointer or left the tree, so
     * hover is re-tested and detached elements lose focus and press state.
     */
    public void afterLayout() {
        if (focused != null && !isConnected(focused)) {
            focus(null);
        }
        if (!active.isEmpty() && !isConnected(active.get(0))) {
            setStatePath(active, root, Element.ACTIVE);
        }
        if (!Float.isNaN(mouseX)) {
            setStatePath(hovered, hitTest(mouseX, mouseY), Element.HOVER);
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

    private static Element hit(Element e, float x, float y) {
        if (e.computed() == null || e.computed().get(CssProperties.DISPLAY) == Keywords.Display.NONE) {
            return null;
        }
        LayoutBox b = e.box();
        boolean inside = x >= b.x() && y >= b.y() && x < b.x() + b.width() && y < b.y() + b.height();
        if (!inside && e.computed().get(CssProperties.OVERFLOW) == Keywords.Overflow.HIDDEN) {
            return null;
        }
        List<Node> children = e.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i) instanceof Element c) {
                Element h = hit(c, x, y);
                if (h != null) {
                    return h;
                }
            }
        }
        return inside ? e : null;
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
        if (e.attribute("tabindex") != null) {
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
            if (a.attribute("tabindex") != null) {
                return a;
            }
        }
        return null;
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

    private boolean isConnected(Element e) {
        for (Element a = e; a != null; a = a.parent()) {
            if (a == root) {
                return true;
            }
        }
        return false;
    }
}
