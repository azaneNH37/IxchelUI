package com.azane.ixchel.core.binding;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedTest {
    static final class Fixed<T> extends ViewModel {
        final Cell<T> value;

        Fixed(Feed<String, T> feed, String key) {
            value = source(feed, key);
        }
    }

    static final class Keyed<T> extends ViewModel {
        final Value<String> key = state("a");
        final Cell<T> value;

        Keyed(Feed<String, T> feed) {
            value = source(feed, key);
        }
    }

    @AfterEach
    void removeClock() {
        Feed.tickClock(null);
    }

    private static boolean pass(ViewModel... models) {
        Feed.nextPass();
        boolean changed = false;
        for (ViewModel m : models) {
            changed |= m.poll();
        }
        return changed;
    }

    @Test
    void modelsOfOneKeyShareOneReadPerPassAndAllSeeItsChange() {
        int[] reads = new int[1];
        int[] live = {1};
        Feed<String, Integer> feed = Feed.polled(k -> {
            reads[0]++;
            return live[0];
        });
        Fixed<Integer> a = new Fixed<>(feed, "k");
        Fixed<Integer> b = new Fixed<>(feed, "k");
        assertTrue(pass(a, b));
        assertEquals(1, reads[0]);
        assertEquals(1, b.value.get());

        assertFalse(pass(a), "an equal value is no change");
        assertFalse(pass(b));
        live[0] = 2;
        Feed.nextPass();
        assertTrue(a.poll());
        assertTrue(b.poll(), "the second model sees what the first one's poll read");
        assertEquals(2, b.value.get());
    }

    @Test
    void mutableValuesAreComparedAndCopiedLikeASource() {
        List<String> live = new ArrayList<>(List.of("x"));
        Feed<String, List<String>> feed = Feed.polled(k -> live, List::copyOf, List::equals);
        Fixed<List<String>> m = new Fixed<>(feed, "k");
        pass(m);
        assertFalse(pass(m), "same content, no change");
        live.add("y");
        assertTrue(pass(m));
        assertEquals(List.of("x", "y"), m.value.get());
        live.add("z");
        assertEquals(List.of("x", "y"), m.value.get(), "bindings see the snapshot");
    }

    @Test
    void aSubscriberOfAFeedCellIsNotifiedWhenTheInstanceMoves() {
        int[] live = {1};
        Feed<String, Integer> feed = Feed.polled(k -> live[0]);
        Fixed<Integer> m = new Fixed<>(feed, "k");
        List<Integer> seen = new ArrayList<>();
        m.subscribe(m.value, () -> seen.add(m.value.get()));
        pass(m);
        live[0] = 2;
        pass(m);
        pass(m);
        assertEquals(List.of(1, 2), seen);
    }

    @Test
    void anOpenedResourceLivesFromTheFirstReferenceToTheLastRelease() {
        List<String> log = new ArrayList<>();
        Feed<String, String> feed = Feed.opened(k -> {
            log.add("open " + k);
            return () -> log.add("close " + k);
        }, r -> "v");
        Fixed<String> a = new Fixed<>(feed, "k");
        Fixed<String> b = new Fixed<>(feed, "k");
        assertEquals(List.of("open k"), log);

        a.close();
        assertEquals(List.of("open k"), log);
        b.close();
        assertEquals(List.of("open k", "close k"), log);

        Fixed<String> again = new Fixed<>(feed, "k");
        assertEquals(List.of("open k", "close k", "open k"), log, "the next first reference opens anew");
        again.close();
    }

    @Test
    void aKeyCellMovesTheReferenceWhenPolledAndANullKeyReadsNothing() {
        List<String> log = new ArrayList<>();
        Feed<String, String> feed = Feed.opened(k -> {
            log.add("open " + k);
            return () -> log.add("close " + k);
        }, r -> "v");
        Keyed<String> m = new Keyed<>(feed);
        assertTrue(log.isEmpty(), "a key cell is first read when the model is polled");
        pass(m);
        assertEquals("v", m.value.get());

        m.key.set("b");
        assertTrue(pass(m));
        assertEquals(List.of("open a", "close a", "open b"), log);

        m.key.set(null);
        assertTrue(pass(m));
        assertNull(m.value.get());
        assertEquals(List.of("open a", "close a", "open b", "close b"), log);
    }

    @Test
    void aPerTickFeedReadsOncePerTickUnderTheClockAndPerPassWithoutOne() {
        int[] reads = new int[1];
        Feed<String, Integer> perPass = Feed.polled(k -> ++reads[0]);
        Feed<String, Integer> feed = perPass.perTick();
        Fixed<Integer> m = new Fixed<>(feed, "k");
        long[] tick = {7};
        Feed.tickClock(() -> tick[0]);
        pass(m);
        pass(m);
        assertEquals(1, reads[0]);
        tick[0]++;
        pass(m);
        assertEquals(2, reads[0]);

        Feed.tickClock(null);
        pass(m);
        pass(m);
        assertEquals(4, reads[0]);
    }
}
