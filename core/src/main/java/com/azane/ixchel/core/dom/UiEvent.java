package com.azane.ixchel.core.dom;

import java.util.Locale;

/**
 * An input event travelling through the tree: capture from the root down to the target, the target
 * itself, then bubble back up. Coordinates are document GUI pixels; key codes are GLFW key codes.
 */
public final class UiEvent {
    public enum Type {
        MOUSE_DOWN, MOUSE_UP, CLICK, WHEEL, KEY_DOWN, KEY_UP;

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
    private final int key;
    private final int modifiers;
    private Element currentTarget;
    private Phase phase;
    private boolean stopped;
    private boolean consumed;

    private UiEvent(Type type, Element target, float x, float y, int button, double wheelDelta, int key, int modifiers) {
        this.type = type;
        this.target = target;
        this.x = x;
        this.y = y;
        this.button = button;
        this.wheelDelta = wheelDelta;
        this.key = key;
        this.modifiers = modifiers;
    }

    /** {@code button}: 0 left, 1 right, 2 middle. */
    public static UiEvent mouse(Type type, Element target, float x, float y, int button) {
        return new UiEvent(type, target, x, y, button, 0, -1, 0);
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
