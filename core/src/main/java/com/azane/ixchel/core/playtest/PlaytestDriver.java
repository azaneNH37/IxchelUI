package com.azane.ixchel.core.playtest;

import com.azane.ixchel.core.api.HostApi;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.layout.Scrollbar;
import com.azane.ixchel.core.runtime.UiDocument;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * One playtest: a queue of steps run one per {@link Pump} call, and the assertions they record.
 * Element steps take a selector and act, through the {@link InputPort}, at the center of the first
 * match's border box where it paints ({@link EventDispatcher#paintedCenter}). A step that throws
 * records a failed assertion and the playtest goes on with the next step. Hosts extend it with their own steps
 * ({@link #then}); {@code S} is that subclass, so the builder methods chain across both.
 *
 * <pre>{@code
 * s.run(SampleIndex::open)
 *  .waitUntil("index lists every sample", () -> Playtest.query(".row").size() == 10, PlaytestDriver.WAIT)
 *  .click(".row:nth-child(2)");
 * }</pre>
 *
 * <p>The same script records videos. A {@linkplain #take take} marks the steps a video shows; in a
 * recording run (a {@link Recording} is given) the pointer steps inside it move as a person moves a
 * mouse, frame by frame along a curve in a time set by Fitts's law, so whatever the pointer crosses
 * is really hovered; presses, keys and typed characters keep a human rhythm, and {@link #hold} lets
 * the video run on. The pace is drawn from a generator seeded by the playtest's name, and the take's
 * time is stepped one video frame per frame, so recording the same playtest again gives the same
 * video. Outside a recording run, and outside takes, every step acts at once as before and holds do
 * nothing; assertions are the same in both.
 */
@HostApi
public abstract class PlaytestDriver<S extends PlaytestDriver<S>> {
    /** Default wait limit: one second of ticks. */
    public static final int WAIT = 20;
    /** The target size a pointer move to a bare point aims for, in GUI pixels. */
    private static final float POINT_SIZE = 8;

    /** One queued step; {@link #tick} returns whether it is done, and is called again until it is. */
    @FunctionalInterface
    public interface Step {
        boolean tick();
    }

    /** A step of a take that runs on drawn frames rather than on ticks: a pointer move, a pause, a press. */
    private interface FrameStep extends Step {
    }

    private final String name;
    private final InputPort input;
    private final Recording recording;
    private final Deque<Step> steps = new ArrayDeque<>();
    private final List<String> results = new ArrayList<>();
    /** Where this driver last put the pointer, in GUI pixels, for the deltas of a drag. */
    private final float[] pointer = new float[2];
    private Pacing pacing;
    /** Whether a take's body is queueing its steps. */
    private boolean taking;
    private int failed;
    /** Whether frame steps ran after the last drawn frame, so no frame has shown what they did yet. */
    private boolean acted;

    protected PlaytestDriver(String name, InputPort input) {
        this(name, input, null);
    }

    /**
     * @param recording the host's recorder in a recording run, or {@code null}: then takes and holds
     *                  change nothing and every step acts at once
     */
    protected PlaytestDriver(String name, InputPort input, Recording recording) {
        this.name = name;
        this.input = input;
        this.recording = recording;
    }

    public final String name() {
        return name;
    }

    /** {@code PASS <what>} or {@code FAIL <what>} per assertion, in order. */
    public final List<String> results() {
        return results;
    }

    public final int passed() {
        return results.size() - failed;
    }

    public final int failed() {
        return failed;
    }

    /**
     * Advances the current step, unless it runs on frames or frame steps have just acted: a tick step
     * then waits for the frame that shows what they did. Whether the queue is empty.
     */
    final boolean tick() {
        Step s = steps.peek();
        if (s == null) {
            return true;
        }
        if (s instanceof FrameStep || acted) {
            return false;
        }
        if (advance(s)) {
            steps.poll();
        }
        return steps.isEmpty();
    }

    /**
     * In a recording run, once per drawn frame: runs the frame steps at the head of the queue, up to
     * the first one that is not done.
     */
    final void frame() {
        acted = false;
        Step s;
        while ((s = steps.peek()) instanceof FrameStep) {
            acted = true;
            if (!advance(s)) {
                return;
            }
            steps.poll();
        }
    }

    private boolean advance(Step s) {
        try {
            return s.tick();
        } catch (RuntimeException ex) {
            threw(ex);
            record("step without exception (" + ex.getMessage() + ")", false);
            return true;
        }
    }

    /** Records one assertion; also usable from inside a {@link #run} action. */
    public final void record(String what, boolean pass) {
        if (!pass) {
            failed++;
        }
        results.add((pass ? "PASS " : "FAIL ") + what);
        recorded(what, pass);
    }

    /** Called after each assertion is recorded, for the host to log it and, on failure, to dump state. */
    protected abstract void recorded(String what, boolean pass);

    /** Called when a step threw, before the failure is {@linkplain #record recorded}. */
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

    /** Lets {@code ticks} ticks pass, for assertions about elapsed game time. */
    public final S pause(int ticks) {
        int[] left = {ticks};
        return then(() -> left[0]-- <= 0);
    }

    /** Asserts {@code condition} once, on the next tick. */
    public final S expect(String what, BooleanSupplier condition) {
        return run(() -> record(what, condition.getAsBoolean()));
    }

    /** Passes as soon as {@code condition} holds; fails after {@code maxTicks} ticks without it. */
    public final S waitUntil(String what, BooleanSupplier condition, int maxTicks) {
        int[] left = {maxTicks};
        return then(() -> {
            if (condition.getAsBoolean()) {
                record(what, true);
                return true;
            }
            if (left[0]-- <= 0) {
                record(what + " (within " + maxTicks + " ticks)", false);
                return true;
            }
            return false;
        });
    }

    /** As {@link #take(String, TakeOptions, Consumer)}, of the whole window. */
    public final S take(String name, Consumer<? super S> body) {
        return take(name, TakeOptions.window(), body);
    }

    /**
     * Queues {@code body}'s steps as the take {@code name}: in a recording run, the video
     * {@code playtests/<modid>/recordings/<name>.mp4}, from the first frame after the steps before it
     * until its last step is done. The steps are the same either way, with the pointer and key steps
     * paced as a person's and {@link #hold}s waiting when recording. Takes do not nest. A recorded take
     * starts with the pointer at {@code (0, 0)}, so its first move, and its length, do not depend on
     * where the cursor was left.
     *
     * <pre>{@code
     * s.take("tabs", TakeOptions.element(".tabs").gif(), t -> t
     *         .click(".tab:nth-child(2)")
     *         .waitUntil("the second tab shows", () -> Playtest.query(".page.second").size() == 1, Playtest.WAIT)
     *         .hold(1000));
     * }</pre>
     */
    public final S take(String name, TakeOptions options, Consumer<? super S> body) {
        if (taking) {
            throw new IllegalStateException("take " + name + " is inside another take");
        }
        if (recording != null) {
            then((FrameStep) () -> {
                point(0, 0, false);
                recording.beginTake(name, region(options), options.makesGif());
                return true;
            });
        }
        taking = true;
        try {
            body.accept(self());
        } finally {
            taking = false;
        }
        if (recording != null) {
            then((FrameStep) () -> {
                recording.endTake();
                return true;
            });
        }
        return self();
    }

    private float[] region(TakeOptions options) {
        if (options.selector() == null) {
            return null;
        }
        LayoutBox b = element(options.selector()).box();
        float p = options.paddingPx();
        return new float[]{b.x() - p, b.y() - p, b.width() + 2 * p, b.height() + 2 * p};
    }

    /** In a take of a recording run, lets {@code ms} milliseconds of the video pass; otherwise nothing. */
    public final S hold(long ms) {
        return paced() ? then(waitFor(() -> ms)) : self();
    }

    /** Whether the steps being queued move and act at a person's pace: a take's, in a recording run. */
    private boolean paced() {
        return taking && recording != null;
    }

    /** Left click on the element {@code selector} matches. */
    public final S click(String selector) {
        return click(selector, 0);
    }

    public final S click(String selector, int button) {
        if (paced()) {
            return pacedClick(() -> aim(element(selector)), button, -1);
        }
        return run(() -> {
            float[] p = center(selector);
            clickAt(p[0], p[1], button);
        });
    }

    /** Left click on the {@code index}th element {@code selector} matches; the list is not scrolled. */
    public final S clickNth(String selector, int index) {
        if (paced()) {
            return pacedClick(() -> aim(query(input.document(), selector).get(index)), 0, -1);
        }
        return run(() -> {
            float[] p = EventDispatcher.paintedCenter(query(input.document(), selector).get(index));
            clickAt(p[0], p[1], 0);
        });
    }

    /** Left click on the first {@code selector} match whose text is {@code text}; the list is not scrolled. */
    public final S clickText(String selector, String text) {
        if (paced()) {
            return pacedClick(() -> aim(withText(selector, text)), 0, -1);
        }
        return run(() -> {
            float[] p = EventDispatcher.paintedCenter(withText(selector, text));
            clickAt(p[0], p[1], 0);
        });
    }

    private Element withText(String selector, String text) {
        return query(input.document(), selector).stream().filter(x -> x.textContent().equals(text)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no " + selector + " reads " + text));
    }

    /**
     * A click with held modifiers on the element, given straight to the document: hosts read the
     * modifiers from the window, which an unattended run cannot hold, so the host does not see it.
     */
    public final S click(String selector, int button, int modifiers) {
        if (paced()) {
            return pacedClick(() -> aim(element(selector)), button, modifiers);
        }
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
        if (paced()) {
            return then(glide(() -> aim(element(selector)), false));
        }
        return run(() -> {
            float[] p = center(selector);
            input.pointAt(p[0], p[1]);
            input.mouseMoved(p[0], p[1]);
        });
    }

    /**
     * Moves the cursor the host reads to {@code point}, {@code {x, y}} in GUI pixels, worked out when
     * the step runs: for points inside an element rather than at its center.
     */
    public final S hoverAt(Supplier<float[]> point) {
        if (paced()) {
            return then(glide(() -> {
                float[] p = point.get();
                return new float[]{p[0], p[1], POINT_SIZE};
            }, false));
        }
        return run(() -> {
            float[] p = point.get();
            input.pointAt(p[0], p[1]);
        });
    }

    /** A wheel turn over the element; positive {@code delta} scrolls up. */
    public final S scroll(String selector, double delta) {
        if (paced()) {
            then(glide(() -> aim(element(selector)), false));
            then(waitFor(() -> pacing().beforeScroll()));
            return then((FrameStep) () -> {
                input.mouseScrolled(pointer[0], pointer[1], delta);
                return true;
            });
        }
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
        if (paced()) {
            return pacedDrag(() -> aim(element(selector)), from -> new float[]{from[0] + dx, from[1] + dy, 0});
        }
        return run(() -> dragFrom(center(selector), dx, dy));
    }

    /** A left-button drag from the center of {@code source}'s first match to the center of {@code target}'s. */
    public final S dragTo(String source, String target) {
        if (paced()) {
            return pacedDrag(() -> aim(element(source)), from -> aim(element(target)));
        }
        return run(() -> {
            float[] from = center(source);
            float[] to = center(target);
            dragFrom(from, to[0] - from[0], to[1] - from[1]);
        });
    }

    /** As {@link #dragTo}, onto the {@code index}th match of {@code target}; the list is not scrolled. */
    public final S dragToNth(String source, String target, int index) {
        if (paced()) {
            return pacedDrag(() -> aim(element(source)), from -> aim(query(input.document(), target).get(index)));
        }
        return run(() -> {
            float[] from = center(source);
            float[] to = EventDispatcher.paintedCenter(query(input.document(), target).get(index));
            dragFrom(from, to[0] - from[0], to[1] - from[1]);
        });
    }

    /** A left-button drag of the element's scrollbar thumb, from its center, by {@code dy} GUI pixels. */
    public final S dragScrollbar(String selector, float dy) {
        if (paced()) {
            return pacedDrag(() -> {
                Scrollbar bar = scrollbar(selector);
                float[] p = thumb(selector, bar);
                return new float[]{p[0], p[1], Math.min(bar.width(), bar.thumbHeight())};
            }, from -> new float[]{from[0], from[1] + dy, 0});
        }
        return run(() -> dragFrom(thumb(selector, scrollbar(selector)), 0, dy));
    }

    private Scrollbar scrollbar(String selector) {
        Scrollbar bar = Scrollbar.of(element(selector));
        if (bar == null) {
            throw new IllegalStateException(selector + " shows no scrollbar");
        }
        return bar;
    }

    private float[] thumb(String selector, Scrollbar bar) {
        return EventDispatcher.paintedPoint(element(selector), bar.x() + bar.width() / 2, bar.thumbY() + bar.thumbHeight() / 2);
    }

    private void dragFrom(float[] p, float dx, float dy) {
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
    }

    /** A press and release of a GLFW key on the open screen. */
    public final S key(int key) {
        return key(key, 0);
    }

    /** A press and release of a GLFW key with GLFW modifier bits on the open screen. */
    public final S key(int key, int modifiers) {
        if (paced()) {
            then(waitFor(() -> pacing().beforeKey()));
            then((FrameStep) () -> {
                input.keyPressed(key, modifiers);
                return true;
            });
            then(waitFor(() -> pacing().keyHold()));
            return then((FrameStep) () -> {
                input.keyReleased(key, modifiers);
                return true;
            });
        }
        return run(() -> {
            input.keyPressed(key, modifiers);
            input.keyReleased(key, modifiers);
        });
    }

    /** Types {@code text} into the open screen, one character event per character, as the keyboard does. */
    public final S type(String text) {
        if (paced()) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                char previous = i == 0 ? 0 : text.charAt(i - 1);
                then(waitFor(() -> pacing().beforeChar(previous)));
                then((FrameStep) () -> {
                    input.charTyped(c);
                    return true;
                });
            }
            return self();
        }
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

    /**
     * A paced click: the pointer travels to {@code aim}, rests, presses, holds and releases. With
     * {@code modifiers} of 0 or more the press goes straight to the document, as {@link #click(String, int, int)}.
     */
    private S pacedClick(Supplier<float[]> aim, int button, int modifiers) {
        then(glide(aim, false));
        then(waitFor(() -> pacing().beforePress()));
        then((FrameStep) () -> {
            if (modifiers < 0) {
                input.mouseClicked(pointer[0], pointer[1], button);
            } else {
                input.document().events().mousePressed(pointer[0], pointer[1], button, modifiers);
                recording.pressed(pointer[0], pointer[1], button);
            }
            return true;
        });
        then(waitFor(() -> pacing().pressHold()));
        return then((FrameStep) () -> {
            if (modifiers < 0) {
                input.mouseReleased(pointer[0], pointer[1], button);
            } else {
                input.document().events().mouseReleased(pointer[0], pointer[1], button, modifiers);
            }
            return true;
        });
    }

    /**
     * A paced left-button drag: the pointer travels to {@code source}, presses, carries what it holds
     * to {@code target} (given where the press was) a little slower than a plain move, and drops it.
     */
    private S pacedDrag(Supplier<float[]> source, UnaryOperator<float[]> target) {
        float[] from = new float[2];
        then(glide(source, false));
        then(waitFor(() -> pacing().beforePress()));
        then((FrameStep) () -> {
            from[0] = pointer[0];
            from[1] = pointer[1];
            input.mouseClicked(from[0], from[1], 0);
            return true;
        });
        then(waitFor(() -> pacing().beforeDrag()));
        then(glide(() -> target.apply(from), true));
        then(waitFor(() -> pacing().beforeDrop()));
        return then((FrameStep) () -> {
            input.mouseReleased(pointer[0], pointer[1], 0);
            return true;
        });
    }

    /**
     * Moves the pointer to {@code aim} ({@code {x, y, target size}}) along a {@link CursorPath}, one
     * position per frame; when the target moved on the way, travels on to where it is now, twice at
     * most. With {@code drag} each move also drags.
     */
    private FrameStep glide(Supplier<float[]> aim, boolean drag) {
        CursorPath[] path = {null};
        long[] start = {0};
        int[] corrections = {0};
        float[] at = new float[2];
        return () -> {
            if (path[0] == null) {
                float[] from = input.pointer();
                pointer[0] = from[0];
                pointer[1] = from[1];
                path[0] = travel(from[0], from[1], aim.get(), drag);
                start[0] = recording.nanos();
            }
            boolean arrived = path[0].at((recording.nanos() - start[0]) / 1e9, at);
            point(at[0], at[1], drag);
            if (!arrived) {
                return false;
            }
            float[] target = aim.get();
            if (Math.hypot(target[0] - at[0], target[1] - at[1]) > 1 && corrections[0] < 2) {
                corrections[0]++;
                path[0] = travel(at[0], at[1], target, drag);
                start[0] = recording.nanos();
                return false;
            }
            point(target[0], target[1], drag);
            return true;
        };
    }

    private CursorPath travel(float fromX, float fromY, float[] aim, boolean drag) {
        return pacing().travel(fromX, fromY, aim[0], aim[1], aim[2], recording.viewportHeight(), drag ? 1.3f : 1);
    }

    /** Puts the pointer at {@code (x, y)}: the host's cursor and the document's hover, and the drag when dragging. */
    private void point(float x, float y, boolean drag) {
        float dx = x - pointer[0];
        float dy = y - pointer[1];
        pointer[0] = x;
        pointer[1] = y;
        input.pointAt(x, y);
        input.mouseMoved(x, y);
        if (drag) {
            input.mouseDragged(x, y, 0, dx, dy);
        }
    }

    /** Waits until {@code ms} milliseconds of the recording's time passed since it first ran. */
    private FrameStep waitFor(LongSupplier ms) {
        long[] until = {0};
        boolean[] started = {false};
        return () -> {
            long now = recording.nanos();
            if (!started[0]) {
                started[0] = true;
                until[0] = now + ms.getAsLong() * 1_000_000;
            }
            return now >= until[0];
        };
    }

    private Pacing pacing() {
        if (pacing == null) {
            pacing = new Pacing(name);
        }
        return pacing;
    }

    /** Where to point at {@code e}, {@code {x, y, smaller side}}. */
    private static float[] aim(Element e) {
        float[] p = EventDispatcher.paintedCenter(e);
        LayoutBox b = e.box();
        return new float[]{p[0], p[1], Math.min(b.width(), b.height())};
    }

    private Element element(String selector) {
        List<Element> found = query(input.document(), selector);
        if (found.isEmpty()) {
            throw new IllegalStateException("no element matches " + selector);
        }
        return found.get(0);
    }

    private float[] center(String selector) {
        return EventDispatcher.paintedCenter(element(selector));
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
