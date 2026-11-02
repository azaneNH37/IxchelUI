package com.azane.ixchel.core.dom;

import java.util.Locale;

/**
 * An input event travelling through the tree: capture from the root down to the target, the target
 * itself, then bubble back up. Coordinates are document GUI pixels; key codes are GLFW key codes.
 */
public final class UiEvent {
    /**
     * {@code INPUT}: the value of a form control changed by the user (typing, deleting, pasting, a
     * toggle, a pick, each step of a range), dispatched like the others. {@code CHANGE}: the user
     * committed a value: a checkbox toggled, an option picked, a range released or stepped by a key,
     * a text field left or Enter pressed in it after its value changed. {@code CLOSE}: a
     * {@code <dialog>} closed by Escape. {@code SCROLL}: an element's {@link Element#scrollTop()}
     * changed; as in the DOM it runs the target's listeners only and does not bubble. {@code MOUSE_MOVE}:
     * the pointer moved; while a button is held it goes to the element that button pressed, wherever the
     * pointer is, with {@link #button()} that button, else to the element under the pointer with
     * {@code button() == -1}.
     *
     * <p>Dragging an element with {@code draggable="true"}: once the left button pressed in it has moved
     * the pointer past a threshold, {@code DRAG_START} goes to that source, then a {@code DRAG} per move
     * (carrying the movement); on release {@code DROP} goes to the element under the pointer, and
     * {@code DRAG_END} always comes last, at the source. A drag dispatches no {@code MOUSE_MOVE},
     * {@code MOUSE_UP} or {@code CLICK}. All bubble.
     */
    public enum Type {
        MOUSE_DOWN, MOUSE_UP, CLICK, MOUSE_MOVE, WHEEL, KEY_DOWN, KEY_UP, INPUT, CHANGE, CLOSE, SCROLL,
        DRAG_START, DRAG, DRAG_END, DROP;

        /** The name used in templates: {@code on:<name>}. */
        public String templateName() {
            return name().replace("_", "").toLowerCase(Locale.ROOT);
        }

        /** The type named {@code name} in templates, or {@code null}. */
        public static Type byTemplateName(String name) {
            for (Type t : values()) {
                if (t.templateName().equals(name)) {
                    return t;
                }
            }
            return null;
        }
    }

    public enum Phase { CAPTURE, TARGET, BUBBLE }

    @FunctionalInterface
    public interface Listener {
        void handle(UiEvent event);
    }

    /** GLFW key codes the core reacts to itself. */
    public static final int KEY_TAB = 258;
    /** GLFW modifier bit for Shift. */
    public static final int MOD_SHIFT = 1;

    private final Type type;
    private final Element target;
    private final float x;
    private final float y;
    private final int button;
    private final double wheelDelta;
    private final float movementX;
    private final float movementY;
    private final int key;
    private final int modifiers;
    private Element currentTarget;
    private Phase phase;
    private boolean stopped;
    private boolean consumed;

    private UiEvent(Type type, Element target, float x, float y, int button, double wheelDelta, int key, int modifiers) {
        this(type, target, x, y, button, wheelDelta, key, modifiers, 0, 0);
    }

    private UiEvent(Type type, Element target, float x, float y, int button, double wheelDelta, int key, int modifiers,
                    float movementX, float movementY) {
        this.type = type;
        this.target = target;
        this.x = x;
        this.y = y;
        this.button = button;
        this.wheelDelta = wheelDelta;
        this.key = key;
        this.modifiers = modifiers;
        this.movementX = movementX;
        this.movementY = movementY;
    }

    /** {@code button}: 0 left, 1 right, 2 middle; {@code modifiers}: GLFW modifier bits held. */
    public static UiEvent mouse(Type type, Element target, float x, float y, int button, int modifiers) {
        return new UiEvent(type, target, x, y, button, 0, -1, modifiers);
    }

    /** A {@link Type#MOUSE_MOVE} by {@code (movementX, movementY)} to {@code (x, y)}; {@code button} as {@link Type#MOUSE_MOVE} says. */
    public static UiEvent move(Element target, float x, float y, float movementX, float movementY, int button) {
        return new UiEvent(Type.MOUSE_MOVE, target, x, y, button, 0, -1, 0, movementX, movementY);
    }

    /** A drag event at {@code (x, y)}, left button; {@code movement*} as {@link #movementX()} says. */
    public static UiEvent drag(Type type, Element target, float x, float y, float movementX, float movementY) {
        return new UiEvent(type, target, x, y, 0, 0, -1, 0, movementX, movementY);
    }

    /** An event that carries nothing but its type and target: {@link Type#INPUT}, {@link Type#CHANGE}, {@link Type#CLOSE}, {@link Type#SCROLL}. */
    public static UiEvent of(Type type, Element target) {
        return new UiEvent(type, target, Float.NaN, Float.NaN, -1, 0, -1, 0);
    }

    public static UiEvent wheel(Element target, float x, float y, double delta) {
        return new UiEvent(Type.WHEEL, target, x, y, -1, delta, -1, 0);
    }

    public static UiEvent key(Type type, Element target, int key, int modifiers) {
        return new UiEvent(type, target, Float.NaN, Float.NaN, -1, 0, key, modifiers);
    }

    public Type type() {
        return type;
    }

    /** The deepest element the event is about. */
    public Element target() {
        return target;
    }

    /** The element whose listener is running. */
    public Element currentTarget() {
        return currentTarget;
    }

    public Phase phase() {
        return phase;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public int button() {
        return button;
    }

    /** Positive scrolls up, as GLFW reports it. */
    public double wheelDelta() {
        return wheelDelta;
    }

    /**
     * How far the pointer moved since the previous {@link Type#MOUSE_MOVE} or {@link Type#DRAG}, in GUI
     * pixels (the first {@code DRAG} of a drag: since the press); 0 for other events.
     */
    public float movementX() {
        return movementX;
    }

    public float movementY() {
        return movementY;
    }

    public int key() {
        return key;
    }

    public int modifiers() {
        return modifiers;
    }

    /** Listeners on further elements are skipped; the rest on the current element still run. */
    public void stopPropagation() {
        stopped = true;
    }

    public boolean isPropagationStopped() {
        return stopped;
    }

    /** Marks the event handled, so the host does not fall back to its own handling (e.g. Escape closing a screen). */
    public void consume() {
        consumed = true;
    }

    public boolean isConsumed() {
        return consumed;
    }

    /** Called by the dispatcher before running the listeners of one element. */
    public void enter(Element currentTarget, Phase phase) {
        this.currentTarget = currentTarget;
        this.phase = phase;
    }
}
