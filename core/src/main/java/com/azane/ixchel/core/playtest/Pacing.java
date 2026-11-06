package com.azane.ixchel.core.playtest;

import java.util.Random;

/**
 * The rhythm of a take's gestures, in milliseconds of the take's time: the rest before a press, how
 * long a button or key stays down, the gaps between typed characters. Each value varies a little, as
 * a person's would, from a generator seeded by the playtest's name, so that recording the same
 * playtest again gives the same video.
 */
final class Pacing {
    private final Random random;

    Pacing(String playtest) {
        this.random = new Random(playtest.hashCode());
    }

    /** A move from {@code (fromX, fromY)} to a target {@code targetSize} wide; see {@link CursorPath}. */
    CursorPath travel(float fromX, float fromY, float toX, float toY, float targetSize, float viewportHeight, float slowdown) {
        return CursorPath.plan(fromX, fromY, toX, toY, targetSize, viewportHeight, slowdown, random);
    }

    private long between(long min, long max) {
        return min + (long) (random.nextDouble() * (max - min));
    }

    long beforePress() {
        return between(90, 150);
    }

    long pressHold() {
        return between(70, 105);
    }

    /** Between pressing on what is dragged and setting off. */
    long beforeDrag() {
        return between(60, 100);
    }

    long beforeDrop() {
        return between(110, 170);
    }

    long beforeScroll() {
        return between(80, 120);
    }

    long beforeKey() {
        return between(80, 140);
    }

    long keyHold() {
        return between(60, 95);
    }

    /** Before typing a character, after {@code previous} ({@code 0} for the first): longer after a space or punctuation. */
    long beforeChar(char previous) {
        long gap = between(55, 110);
        if (previous == ' ') {
            gap += between(30, 80);
        }
        if (".,;:!?".indexOf(previous) >= 0) {
            gap += between(90, 160);
        }
        return gap;
    }
}
