package com.azane.ixchel.core.check;

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
 * Runs the selected checks one after another, one {@link Driver} step per {@link #tick}, and
 * totals their results. A selected name that is not registered fails as a check of its own.
 *
 * @param <S> the host's driver
 */
public final class Runner<S extends Driver<S>> {
    private final Map<String, Consumer<S>> registered = new LinkedHashMap<>();
    private final String selection;
    private final Function<String, S> drivers;
    private final Consumer<S> prepare;
    private final List<S> finished = new ArrayList<>();
    private Deque<String> pending;
    private S current;

    /**
     * @param selection comma-separated check names, or {@code all} for every check in registration order
     * @param drivers   creates the driver of the named check
     * @param prepare   queues the steps that run before each check's own, such as a state reset
     */
    public Runner(String selection, Function<String, S> drivers, Consumer<S> prepare) {
        this.selection = selection;
        this.drivers = drivers;
        this.prepare = prepare;
    }

    /** Adds a check; {@code body} queues its steps on the driver when the check starts. */
    public void register(String name, Consumer<S> body) {
        if (registered.putIfAbsent(name, body) != null) {
            throw new IllegalStateException("check " + name + " is registered twice");
        }
    }

    /** Advances the run by one step; whether every selected check has finished. */
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
                current.report("is registered (known: " + String.join(", ", registered.keySet()) + ")", false);
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

    public int passed() {
        return finished.stream().mapToInt(Driver::passed).sum();
    }

    public int failed() {
        return finished.stream().mapToInt(Driver::failed).sum();
    }

    /** Each finished check's results and totals, then the run's totals. */
    public String report() {
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
