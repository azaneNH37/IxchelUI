package com.azane.ixchel.core.playtest;

import com.azane.ixchel.core.html.HtmlElements;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.core.text.TextMeasurer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytestDriverRecordingTest {
    private static final int FPS = 60;
    /** A 40×20 box centered at (220, 110) on a 480×270 GUI. */
    private static final String CSS = ".b { position: absolute; left: 200px; top: 100px; width: 40px; height: 20px; }";

    private static final TextMeasurer MONO = new TextMeasurer() {
        @Override
        public boolean hasFont(String font) {
            return true;
        }

        @Override
        public float width(String text, float size, boolean bold, String font) {
            return text.length() * 6 * size / 9;
        }

        @Override
        public float lineHeight(float size) {
            return size;
        }

        @Override
        public float baseline(float size) {
            return size * 7 / 9;
        }
    };

    /** The host's input, keeping where the cursor went and what was pressed. */
    private static final class Input implements InputPort {
        final UiDocument document = UiDocument.load("<link href=\"t.css\"/><page><div class=\"b\"></div></page>", "t.html",
                Map.of("t.css", "@stylesheet; " + CSS)::get, HtmlElements.TAGS, MONO, src -> null);
        final List<float[]> moves = new ArrayList<>();
        final List<String> presses = new ArrayList<>();
        final Recorder recorder;
        float[] pointer = {0, 0};
        long lastMoveFrame;

        Input() {
            this(null);
        }

        Input(Recorder recorder) {
            this.recorder = recorder;
            document.frame(480, 270);
        }

        @Override
        public UiDocument document() {
            return document;
        }

        @Override
        public void pointAt(double x, double y) {
            pointer = new float[]{(float) x, (float) y};
            moves.add(pointer);
            if (recorder != null) {
                lastMoveFrame = recorder.frames;
            }
        }

        @Override
        public float[] pointer() {
            return pointer.clone();
        }

        @Override
        public void mouseMoved(double x, double y) {
        }

        @Override
        public void mouseClicked(double x, double y, int button) {
            presses.add("press " + x + "," + y);
        }

        @Override
        public void mouseReleased(double x, double y, int button) {
            presses.add("release " + x + "," + y);
        }

        @Override
        public void mouseDragged(double x, double y, int button, double dx, double dy) {
        }

        @Override
        public void mouseScrolled(double x, double y, double delta) {
        }

        @Override
        public void keyPressed(int key, int modifiers) {
        }

        @Override
        public void keyReleased(int key, int modifiers) {
        }

        @Override
        public void charTyped(char c) {
        }
    }

    /** A recorder stepping its time one video frame per {@link #frame}, as the host's does in a take. */
    private static final class Recorder implements Recording {
        final List<String> events = new ArrayList<>();
        long frames;

        void frame() {
            frames++;
        }

        @Override
        public long nanos() {
            return frames * 1_000_000_000L / FPS;
        }

        @Override
        public float viewportHeight() {
            return 270;
        }

        @Override
        public void beginTake(String name, float[] region, boolean gif) {
            events.add(frames + " begin " + name + (region == null ? "" : " " + region[0] + "," + region[1] + "," + region[2] + "," + region[3]));
        }

        @Override
        public void endTake() {
            events.add(frames + " end");
        }

        @Override
        public void pressed(double x, double y, int button) {
        }
    }

    private static final class Driver extends PlaytestDriver<Driver> {
        Driver(InputPort input, Recording recording) {
            super("test", input, recording);
        }

        @Override
        protected void recorded(String what, boolean pass) {
        }

        @Override
        protected void threw(RuntimeException ex) {
            throw ex;
        }
    }

    /** Runs {@code d} as the host does in a recording run, a tick every third frame; the frames it took. */
    private static int record(Driver d, Recorder recorder) {
        for (int frame = 0; frame < 10_000; frame++) {
            recorder.frame();
            d.frame();
            if (frame % 3 == 2 && d.tick()) {
                return frame;
            }
        }
        throw new AssertionError("the playtest did not finish");
    }

    // Fails if a take, a hold or a pointer move changes a playtest that is not recorded: each step must
    // act at once, as before.
    @Test
    void withoutRecordingTakesAndHoldsChangeNothing() {
        Input input = new Input();
        Driver d = new Driver(input, null);
        d.take("t", t -> t.hold(1000).hover(".b").hold(500).click(".b"));
        assertFalse(d.tick());
        assertArrayEquals(new float[]{220, 110}, input.pointer);
        assertEquals(1, input.moves.size());
        assertTrue(d.tick());
        assertEquals(List.of("press 220.0,110.0", "release 220.0,110.0"), input.presses);
    }

    // Fails if a recorded move jumps, or ends anywhere but the element's center, or the hold after it
    // is not exactly its length of video frames.
    @Test
    void recordedMoveGlidesFrameByFrameAndHoldLastsItsFrames() {
        Recorder recorder = new Recorder();
        Input input = new Input(recorder);
        Driver d = new Driver(input, recorder);
        d.take("t", t -> t.hover(".b").hold(1000));
        record(d, recorder);
        assertTrue(input.moves.size() > 10, "moves: " + input.moves.size());
        for (int i = 1; i < input.moves.size(); i++) {
            float[] a = input.moves.get(i - 1), b = input.moves.get(i);
            assertTrue(Math.hypot(b[0] - a[0], b[1] - a[1]) < 30, "jump at move " + i);
        }
        assertArrayEquals(new float[]{220, 110}, input.pointer);
        long ended = Long.parseLong(recorder.events.get(1).split(" ")[0]);
        assertEquals(FPS, ended - input.lastMoveFrame);
    }

    // Fails if a framed take is not given its element's box with the padding, or takes nest.
    @Test
    void elementTakeGetsTheBoxWithPadding() {
        Input input = new Input();
        Recorder recorder = new Recorder();
        Driver d = new Driver(input, recorder);
        d.take("framed", TakeOptions.element(".b").padding(8), t -> t.hold(100));
        record(d, recorder);
        assertEquals("1 begin framed 192.0,92.0,56.0,36.0", recorder.events.get(0));
        Driver nested = new Driver(input, recorder);
        try {
            nested.take("outer", t -> t.take("inner", u -> u.hold(1)));
            throw new AssertionError("a take inside a take was accepted");
        } catch (IllegalStateException expected) {
            // takes do not nest
        }
    }

    // Fails if the pace is not drawn from the playtest's name, or the first move starts wherever the
    // host's cursor was left: recording again would differ.
    @Test
    void recordingTwiceMovesAndPressesTheSame() {
        List<List<float[]>> moves = new ArrayList<>();
        List<List<String>> presses = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            Input input = new Input();
            input.pointer = run == 0 ? new float[]{0, 0} : new float[]{400, 250};
            Recorder recorder = new Recorder();
            Driver d = new Driver(input, recorder);
            d.take("t", t -> t.click(".b").hold(200).hover(".b"));
            lengths.add(record(d, recorder));
            moves.add(input.moves);
            presses.add(input.presses);
        }
        assertEquals(lengths.get(0), lengths.get(1));
        assertEquals(presses.get(0), presses.get(1));
        assertEquals(moves.get(0).size(), moves.get(1).size());
        for (int i = 0; i < moves.get(0).size(); i++) {
            assertArrayEquals(moves.get(0).get(i), moves.get(1).get(i));
        }
    }
}
