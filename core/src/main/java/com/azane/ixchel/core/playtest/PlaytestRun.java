package com.azane.ixchel.core.playtest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One run of the selected playtests, one after another, one {@link PlaytestDriver} step per
 * {@link #tick}, totalling their assertions. A selected name that is not registered fails as a
 * playtest of its own.
 *
 * @param <S> the host's driver
 */
public final class PlaytestRun<S extends PlaytestDriver<S>> {
    private final Map<String, Consumer<S>> registered = new LinkedHashMap<>();
    private final String selection;
    private final Function<String, S> drivers;
    private final Consumer<S> prepare;
    private final List<S> finished = new ArrayList<>();
    private Deque<String> pending;
    private S current;

    /**
     * @param selection comma-separated playtest names, or {@code all} for every playtest in registration order
     * @param drivers   creates the driver of the named playtest
     * @param prepare   queues the steps that run before each playtest's own, such as a state reset
     */
    public PlaytestRun(String selection, Function<String, S> drivers, Consumer<S> prepare) {
        this.selection = selection;
        this.drivers = drivers;
        this.prepare = prepare;
    }

    /** Adds a playtest; {@code body} queues its steps on the driver when the playtest starts. */
    public void register(String name, Consumer<S> body) {
        if (registered.putIfAbsent(name, body) != null) {
            throw new IllegalStateException("playtest " + name + " is registered twice");
        }
    }

    /** Advances the run by one step; whether every selected playtest has finished. */
    public boolean tick() {
        if (pending == null) {
            pending = new ArrayDeque<>(selection.equals("all") ? registered.keySet() : Arrays.asList(selection.split(",")));
        }
        if (current == null) {
            String name = pending.poll();
            if (name == null) {
                return true;
            }
            current = drivers.apply(name.trim());
            Consumer<S> body = registered.get(current.name());
            if (body == null) {
                current.record("is registered (known: " + String.join(", ", registered.keySet()) + ")", false);
            } else {
                prepare.accept(current);
                body.accept(current);
            }
        }
        if (current.tick()) {
            finished.add(current);
            current = null;
        }
        return false;
    }

    /** In a recording run, once per drawn frame: runs the current playtest's frame steps (see {@link PlaytestDriver#take}). */
    public void frame() {
        if (current != null) {
            current.frame();
        }
    }

    /** Playtests finished so far. */
    public int finished() {
        return finished.size();
    }

    /** Passed assertions of the finished playtests. */
    public int passed() {
        return finished.stream().mapToInt(PlaytestDriver::passed).sum();
    }

    /** Failed assertions of the finished playtests. */
    public int failed() {
        return finished.stream().mapToInt(PlaytestDriver::failed).sum();
    }

    /** Each finished playtest's assertions and totals, then the run's totals. */
    public String summary() {
        StringBuilder report = new StringBuilder();
        for (S s : finished) {
            report.append(s.name()).append('\n');
            for (String line : s.results()) {
                report.append("  ").append(line).append('\n');
            }
            report.append(s.name()).append(": ").append(s.passed()).append(" passed, ").append(s.failed())
                    .append(" failed\n\n");
        }
        report.append("total: ").append(passed()).append(" passed, ").append(failed()).append(" failed\n");
        return report.toString();
    }
}
