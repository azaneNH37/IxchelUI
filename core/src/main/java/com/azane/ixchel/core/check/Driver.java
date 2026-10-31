package com.azane.ixchel.core.check;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.runtime.UiDocument;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * One check: a queue of steps run one per {@link Pump} call, and the results they record. Element
 * steps take a selector and act, through the {@link InputPort}, at the center of the first match's
 * border box where it paints ({@link EventDispatcher#paintedCenter}). A step that throws records a
 * failure and the check goes on with the next step. Hosts extend it with their own steps
 * ({@link #then}); {@code S} is that subclass, so the builder methods chain across both.
 *
 * <pre>{@code
 * s.run(SampleIndex::open)
 *  .waitUntil("index lists every sample", () -> Script.query(".row").size() == 10, Driver.WAIT)
 *  .click(".row:nth-child(2)");
 * }</pre>
 */
public abstract class Driver<S extends Driver<S>> {
    /** Default wait limit: one second of ticks. */
    public static final int WAIT = 20;

    /** One queued step; {@link #tick} returns whether it is done, and is called again until it is. */
    @FunctionalInterface
    public interface Step {
        boolean tick();
    }

    private final String name;
    private final InputPort input;
    private final Deque<Step> steps = new ArrayDeque<>();
    private final List<String> results = new ArrayList<>();
    private int failed;

    protected Driver(String name, InputPort input) {
        this.name = name;
        this.input = input;
    }

    public final String name() {
        return name;
    }

    /** {@code PASS <what>} or {@code FAIL <what>} per result, in order. */
    public final List<String> results() {
        return results;
    }

    public final int passed() {
        return results.size() - failed;
    }

    public final int failed() {
        return failed;
    }

    /** Advances the current step; whether the queue is empty. */
    final boolean tick() {
        Step s = steps.peek();
        if (s == null) {
            return true;
        }
        boolean done;
        try {
            done = s.tick();
        } catch (RuntimeException ex) {
            threw(ex);
            report("step without exception (" + ex.getMessage() + ")", false);
            done = true;
        }
        if (done) {
            steps.poll();
        }
        return steps.isEmpty();
    }

    /** Records one result; also usable from inside a {@link #run} action. */
    public final void report(String what, boolean pass) {
        if (!pass) {
            failed++;
        }
        results.add((pass ? "PASS " : "FAIL ") + what);
        reported(what, pass);
    }

    /** Called after each result is recorded, for the host to log it and, on failure, to dump state. */
    protected abstract void reported(String what, boolean pass);

    /** Called when a step threw, before the failure is {@linkplain #report reported}. */
    protected abstract void threw(RuntimeException ex);

    /** Queues {@code step}. */
    protected final S then(Step step) {
        steps.add(step);
        return self();
    }

    @SuppressWarnings("unchecked")
    private S self() {
        return (S) this;
    }

    /** Runs {@code action} on the next tick. */
    public final S run(Runnable action) {
        return then(() -> {
            action.run();
            return true;
        });
    }

    /** Lets {@code ticks} ticks pass, for checks about elapsed game time. */
    public final S pause(int ticks) {
        int[] left = {ticks};
        return then(() -> left[0]-- <= 0);
    }

    /** Checks {@code check} once, on the next tick. */
    public final S expect(String what, BooleanSupplier check) {
        return run(() -> report(what, check.getAsBoolean()));
    }

    /** Passes as soon as {@code check} holds; fails after {@code maxTicks} ticks without it. */
    public final S waitUntil(String what, BooleanSupplier check, int maxTicks) {
        int[] left = {maxTicks};
        return then(() -> {
            if (check.getAsBoolean()) {
                report(what, true);
                return true;
            }
            if (left[0]-- <= 0) {
                report(what + " (within " + maxTicks + " ticks)", false);
                return true;
            }
            return false;
        });
    }

    /** Left click on the element {@code selector} matches. */
    public final S click(String selector) {
        return click(selector, 0);
    }

    public final S click(String selector, int button) {
        return run(() -> {
            float[] p = center(selector);
            clickAt(p[0], p[1], button);
        });
    }

    /** Left click on the {@code index}th element {@code selector} matches; the list is not scrolled. */
    public final S clickNth(String selector, int index) {
        return run(() -> {
            float[] p = EventDispatcher.paintedCenter(query(input.document(), selector).get(index));
            clickAt(p[0], p[1], 0);
        });
    }

    /** Left click on the first {@code selector} match whose text is {@code text}; the list is not scrolled. */
    public final S clickText(String selector, String text) {
        return run(() -> {
            Element e = query(input.document(), selector).stream().filter(x -> x.textContent().equals(text)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("no " + selector + " reads " + text));
            float[] p = EventDispatcher.paintedCenter(e);
            clickAt(p[0], p[1], 0);
        });
    }

    /**
     * A click with held modifiers on the element, given straight to the document: hosts read the
     * modifiers from the window, which an unattended run cannot hold, so the host does not see it.
     */
    public final S click(String selector, int button, int modifiers) {
        return run(() -> {
            float[] p = center(selector);
            UiDocument d = input.document();
            input.mouseMoved(p[0], p[1]);
            d.events().mousePressed(p[0], p[1], button, modifiers);
            d.events().mouseReleased(p[0], p[1], button, modifiers);
        });
    }

    /** Moves the pointer onto the element: the cursor the host reads, and the document's hover. */
    public final S hover(String selector) {
        return run(() -> {
            float[] p = center(selector);
            input.pointAt(p[0], p[1]);
            input.mouseMoved(p[0], p[1]);
        });
    }

    /** A wheel turn over the element; positive {@code delta} scrolls up. */
    public final S scroll(String selector, double delta) {
        return run(() -> {
            float[] p = center(selector);
            input.mouseScrolled(p[0], p[1], delta);
        });
    }

    /**
     * A left-button drag from the element's center by {@code (dx, dy)} GUI pixels in four moves, as a
     * host reports one: a move then a drag per step, the cursor following.
     */
    public final S drag(String selector, float dx, float dy) {
        return run(() -> {
            float[] p = center(selector);
            input.pointAt(p[0], p[1]);
            input.mouseMoved(p[0], p[1]);
            input.mouseClicked(p[0], p[1], 0);
            for (int i = 1; i <= 4; i++) {
                double x = p[0] + dx * i / 4;
                double y = p[1] + dy * i / 4;
                input.pointAt(x, y);
                input.mouseMoved(x, y);
                input.mouseDragged(x, y, 0, dx / 4, dy / 4);
            }
            input.mouseReleased(p[0] + dx, p[1] + dy, 0);
        });
    }

    /** A press and release of a GLFW key on the open screen. */
    public final S key(int key) {
        return key(key, 0);
    }

    /** A press and release of a GLFW key with GLFW modifier bits on the open screen. */
    public final S key(int key, int modifiers) {
        return run(() -> {
            input.keyPressed(key, modifiers);
            input.keyReleased(key, modifiers);
        });
    }

    /** Types {@code text} into the open screen, one character event per character, as the keyboard does. */
    public final S type(String text) {
        return run(() -> {
            for (char c : text.toCharArray()) {
                input.charTyped(c);
            }
        });
    }

    /** A press and release of {@code button} at {@code (x, y)} on the open screen, in GUI pixels. */
    private void clickAt(double x, double y, int button) {
        input.mouseMoved(x, y);
        input.mouseClicked(x, y, button);
        input.mouseReleased(x, y, button);
    }

    private float[] center(String selector) {
        List<Element> found = query(input.document(), selector);
        if (found.isEmpty()) {
            throw new IllegalStateException("no element matches " + selector);
        }
        return EventDispatcher.paintedCenter(found.get(0));
    }

    /** Elements of {@code d} that match {@code selector}, in document order. */
    public static List<Element> query(UiDocument d, String selector) {
        return d.querySelectorAll(selector);
    }

    /** Text of the first match in {@code d}, or an empty string. */
    public static String text(UiDocument d, String selector) {
        List<Element> found = query(d, selector);
        return found.isEmpty() ? "" : found.get(0).textContent();
    }

    /** Texts of every match in {@code d}, in document order. */
    public static List<String> texts(UiDocument d, String selector) {
        return query(d, selector).stream().map(Element::textContent).toList();
    }

    /** The attribute {@code name} of the first match in {@code d}, or an empty string. */
    public static String attribute(UiDocument d, String selector, String name) {
        String v = query(d, selector).get(0).attribute(name);
        return v == null ? "" : v;
    }

    /** The laid-out box of the first match in {@code d}. */
    public static LayoutBox box(UiDocument d, String selector) {
        return query(d, selector).get(0).box();
    }

    /**
     * In a list of key/value pairs, the text of the {@code values} element paired (by index) with the
     * {@code keys} element whose text is {@code key}, or an empty string.
     */
    public static String valueOf(List<Element> keys, List<Element> values, String key) {
        for (int i = 0; i < keys.size() && i < values.size(); i++) {
            if (keys.get(i).textContent().equals(key)) {
                return values.get(i).textContent();
            }
        }
        return "";
    }
}
