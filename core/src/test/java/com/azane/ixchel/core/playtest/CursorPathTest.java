package com.azane.ixchel.core.playtest;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorPathTest {
    // Fails if Fitts's law is turned around: far or small targets must take longer.
    @Test
    void fartherAndSmallerTargetsTakeLonger() {
        double near = CursorPath.fitts(50, 20, 270);
        double far = CursorPath.fitts(200, 20, 270);
        double small = CursorPath.fitts(200, 6, 270);
        assertTrue(far > near, far + " vs " + near);
        assertTrue(small > far, small + " vs " + far);
    }

    // Fails if distances are judged in GUI pixels rather than on the reference window: a move would
    // take another time at another GUI scale.
    @Test
    void durationDoesNotDependOnGuiScale() {
        assertEquals(CursorPath.fitts(100, 10, 270), CursorPath.fitts(200, 20, 540), 1e-12);
    }

    // Fails if a move is not held to a person's range.
    @Test
    void durationIsHeldToTheHumanRange() {
        assertEquals(0.2, CursorPath.fitts(0, 100, 270), 1e-12);
        assertEquals(CursorPath.MAX_SECONDS, CursorPath.fitts(1e6, 1, 270), 1e-12);
    }

    // Fails if the move does not start where the pointer is, overshoots, goes back, or stops short.
    @Test
    void pathStartsAtThePointerAndLandsOnTheTargetWithoutOvershoot() {
        CursorPath path = CursorPath.plan(10, 20, 210, 120, 16, 270, 1, new Random(7));
        float[] p = new float[2];
        assertFalse(path.at(0, p));
        assertEquals(10, p[0], 1e-4);
        assertEquals(20, p[1], 1e-4);
        double dx = 200, dy = 100, length = Math.hypot(dx, dy);
        double last = -1;
        for (int i = 1; i < 100; i++) {
            assertFalse(path.at(path.seconds() * i / 100, p), "done at " + i + "%");
            double along = ((p[0] - 10) * dx + (p[1] - 20) * dy) / length;
            assertTrue(along >= last, "goes back at " + i + "%");
            assertTrue(along <= length, "overshoots at " + i + "%");
            last = along;
        }
        assertTrue(path.at(path.seconds(), p));
        assertEquals(210, p[0], 1e-4);
        assertEquals(120, p[1], 1e-4);
    }

    // Fails if the pointer moves at a constant speed rather than speeding up and settling.
    @Test
    void pointerIsFastestInTheMiddleOfTheMove() {
        CursorPath path = CursorPath.plan(0, 0, 300, 0, 16, 270, 1, new Random(1));
        float[] a = new float[2];
        float[] b = new float[2];
        double t = path.seconds();
        path.at(0, a);
        path.at(t * 0.1, b);
        double start = Math.hypot(b[0] - a[0], b[1] - a[1]);
        path.at(t * 0.45, a);
        path.at(t * 0.55, b);
        double middle = Math.hypot(b[0] - a[0], b[1] - a[1]);
        path.at(t * 0.9, a);
        path.at(t, b);
        double end = Math.hypot(b[0] - a[0], b[1] - a[1]);
        assertTrue(middle > 3 * start && middle > 3 * end, start + " " + middle + " " + end);
    }

    // Fails if a drag is not slower than a plain move, or a move of nothing takes time.
    @Test
    void slowdownStretchesAndAZeroMoveIsDoneAtOnce() {
        double plain = CursorPath.plan(0, 0, 100, 0, 16, 270, 1, new Random(3)).seconds();
        double dragged = CursorPath.plan(0, 0, 100, 0, 16, 270, 1.3f, new Random(3)).seconds();
        assertEquals(plain * 1.3, dragged, 1e-6);
        CursorPath none = CursorPath.plan(5, 5, 5, 5, 16, 270, 1, new Random(3));
        float[] p = new float[2];
        assertTrue(none.at(0, p));
        assertEquals(5, p[0]);
    }

    // Fails if the pace is not seeded by the playtest's name: a second recording would differ.
    @Test
    void paceRepeatsForTheSamePlaytestOnly() {
        Pacing a = new Pacing("themes");
        Pacing b = new Pacing("themes");
        Pacing c = new Pacing("effects");
        float[] pa = new float[2];
        float[] pb = new float[2];
        CursorPath first = a.travel(0, 0, 100, 50, 10, 270, 1);
        CursorPath second = b.travel(0, 0, 100, 50, 10, 270, 1);
        assertEquals(first.seconds(), second.seconds());
        first.at(first.seconds() / 2, pa);
        second.at(second.seconds() / 2, pb);
        assertEquals(pa[0], pb[0]);
        assertEquals(pa[1], pb[1]);
        assertEquals(a.beforePress(), b.beforePress());
        assertNotEquals(a.travel(0, 0, 100, 50, 10, 270, 1).seconds(), c.travel(0, 0, 100, 50, 10, 270, 1).seconds());
    }

    // Fails if typing does not pause longer after punctuation than inside a word.
    @Test
    void typingPausesAfterPunctuation() {
        Pacing pacing = new Pacing("typing");
        for (int i = 0; i < 50; i++) {
            long inside = pacing.beforeChar('a');
            long after = pacing.beforeChar('.');
            assertTrue(inside >= 55 && inside < 110, "inside a word " + inside);
            assertTrue(after >= 145, "after a full stop " + after);
        }
    }
}
