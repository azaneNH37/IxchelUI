package com.azane.ixchel.sample.config;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The config panel's view model: one {@link Row} per config value with its draft in its own
 * {@link Value}s, so the page edits rows in place (two-way bindings on row components). Drafts
 * reach the config only on {@link #save}; {@link #undo} steps back through draft changes, each frame's
 * changes as one step.
 */
@UiView("ixchel_sample:ui/config/panel.html")
final class ConfigModel extends ViewModel {
    /** Presets the list dialog offers, for the item id lists. */
    static final List<String> PRESETS = List.of("minecraft:rotten_flesh", "minecraft:poisonous_potato",
            "minecraft:spider_eye", "minecraft:dead_bush");

    /** A category in the left column. */
    record Category(String id, Component label, Derived<Boolean> active) {
    }

    /**
     * One config value. Exactly one of the kind flags is set. {@code on} is a boolean's draft,
     * {@code value} every other kind's, as text (a list joined with ", "); {@code saved} is the text
     * of the stored value.
     */
    record Row(String id, String category, Component label, Component comment, boolean restart,
               boolean bool, boolean number, boolean choice, boolean text, boolean color, boolean list,
               String min, String max, String step, List<String> choices,
               Value<Boolean> on, Value<String> value, Value<String> saved,
               Derived<Boolean> modified, Derived<Boolean> atDefault, Derived<Component> error,
               Derived<Boolean> invalid, Derived<String> swatch) {
    }

    /** One line of the list dialog. */
    record Item(int id, Value<String> text) {
    }

    private final ConfigEntries entries = ConfigEntries.load();
    private final Map<String, ConfigEntries.Entry> byId = new HashMap<>();
    private final List<Row> all = new ArrayList<>();
    private final Runnable close;
    private final Deque<Runnable> undoSteps = new ArrayDeque<>();
    private final Map<Row, String> lastSeen = new HashMap<>();
    private boolean undoing;
    private int nextItem;
    private Row editing;

    final Value<String> category;
    final Value<String> query = state("");
    final Value<List<Category>> categories;
    final Derived<List<Row>> rows;
    final Value<Boolean> canSave = state(false);
    final Value<Boolean> canUndo = state(false);
    final Value<Boolean> restartPending = state(false);
    final Value<Component> status = state(Component.empty());

    final Value<Boolean> confirming = state(false);
    final Value<Boolean> listOpen = state(false);
    final Value<Component> listTitle = state(Component.empty());
    final Value<List<Item>> items = state(List.of());
    final Value<String> preset = state("");
    /** The id of the list line being dragged, -1 while none is. */
    final Value<Integer> grabbed = state(-1);

    final Action<Category> select;
    final Action<Row> reset;
    final Action<Row> editList;
    final Action<Item> removeItem;
    /** Drops the line being dragged ({@link #grabbed}) on another. */
    final Action<Item> moveTo;
    final Action<Void> addItem;
    final Action<Void> addPreset;
    final Action<Void> applyList;
    final Action<Void> save;
    final Action<Void> undo;
    final Action<Void> cancel;
    final Action<Void> discard;

    /** @param close closes the screen; run after the event that asked for it */
    ConfigModel(Runnable close) {
        this.close = close;
        category = state(entries.categories().get(0).name());
        List<Category> cats = new ArrayList<>();
        for (ConfigEntries.Category c : entries.categories()) {
            cats.add(new Category(c.name(), c.label(), derive(category, sel -> sel.equals(c.name()))));
            for (ConfigEntries.Entry e : c.entries()) {
                byId.put(e.path(), e);
                all.add(row(c.name(), e));
            }
        }
        categories = state(List.copyOf(cats));
        rows = derive(category, query, (cat, q) -> {
            String needle = q.trim().toLowerCase(Locale.ROOT);
            return all.stream().filter(r -> needle.isEmpty() ? r.category().equals(cat)
                    : (r.label().getString() + "\n" + r.id() + "\n" + r.comment().getString()).toLowerCase(Locale.ROOT).contains(needle)).toList();
        });
        for (Row r : all) {
            lastSeen.put(r, draft(r));
            subscribe(r.bool() ? r.on() : r.value(), () -> changed(r));
        }

        select = action((Category c) -> {
            category.set(c.id());
            query.set("");
        });
        reset = action((Row r) -> {
            Object def = byId.get(r.id()).defaultValue();
            if (r.bool()) {
                r.on().set((Boolean) def);
            } else {
                r.value().set(ConfigEntries.format(byId.get(r.id()).kind(), def));
            }
        });
        editList = action((Row r) -> {
            editing = r;
            listTitle.set(r.label());
            List<Item> list = new ArrayList<>();
            for (Object s : (List<?>) ConfigEntries.parse(ConfigEntries.Kind.LIST, r.value().get())) {
                list.add(new Item(nextItem++, state((String) s)));
            }
            items.set(List.copyOf(list));
            listOpen.set(true);
        });
        removeItem = action((Item i) -> items.set(items.get().stream().filter(x -> x != i).toList()));
        moveTo = action((Item target) -> {
            List<Item> list = new ArrayList<>(items.get());
            Item moved = list.stream().filter(x -> x.id() == grabbed.get()).findFirst().orElse(null);
            if (moved == null || moved == target) {
                return;
            }
            // The dragged line takes the target's place: after it when moved down, before it when moved up.
            int at = list.indexOf(target);
            list.remove(moved);
            list.add(at, moved);
            items.set(List.copyOf(list));
        });
        addItem = action(() -> addItem(""));
        addPreset = action(() -> {
            if (!preset.get().isEmpty()) {
                addItem(preset.get());
                preset.set("");
            }
        });
        applyList = action(() -> {
            editing.value().set(String.join(", ", items.get().stream().map(i -> i.text().get().trim())
                    .filter(s -> !s.isEmpty()).toList()));
            listOpen.set(false);
        });
        save = action(this::saveAll);
        undo = action(() -> {
            if (!undoSteps.isEmpty()) {
                undoing = true;
                undoSteps.pop().run();
            }
        });
        cancel = action(() -> {
            if (all.stream().anyMatch(r -> r.modified().get())) {
                confirming.set(true);
            } else {
                closeLater();
            }
        });
        discard = action(this::closeLater);
    }

    private Row row(String category, ConfigEntries.Entry e) {
        ConfigEntries.Kind k = e.kind();
        boolean number = k == ConfigEntries.Kind.INT || k == ConfigEntries.Kind.DOUBLE;
        String stored = text(e, e.value());
        Value<Boolean> on = state(k == ConfigEntries.Kind.BOOLEAN && (Boolean) e.value());
        Value<String> value = state(stored);
        Value<String> saved = state(stored);
        Derived<Boolean> modified = k == ConfigEntries.Kind.BOOLEAN
                ? derive(on, saved, (o, s) -> !String.valueOf(o).equals(s))
                : derive(value, saved, (v, s) -> !normalize(e, v).equals(s));
        String def = text(e, e.defaultValue());
        Derived<Boolean> atDefault = k == ConfigEntries.Kind.BOOLEAN
                ? derive(on, o -> String.valueOf(o).equals(def))
                : derive(value, v -> normalize(e, v).equals(def));
        Derived<Component> error = derive(value, v -> k == ConfigEntries.Kind.BOOLEAN ? Component.empty() : validate(e, v));
        Derived<Boolean> invalid = derive(error, s -> !s.getString().isEmpty());
        Derived<String> swatch = derive(value, v -> {
            Object c = ConfigEntries.parse(ConfigEntries.Kind.COLOR, v);
            return c == null ? "transparent" : ConfigEntries.format(ConfigEntries.Kind.COLOR, c);
        });
        String min = e.min() == null ? "" : ConfigEntries.format(k, e.min());
        String max = e.max() == null ? "" : ConfigEntries.format(k, e.max());
        String step = k == ConfigEntries.Kind.DOUBLE && e.min() != null && e.max() != null
                ? ConfigEntries.format(k, (e.max().doubleValue() - e.min().doubleValue()) / 100) : "1";
        return new Row(e.path(), category, e.label(), e.comment(), e.needsRestart(),
                k == ConfigEntries.Kind.BOOLEAN, number, k == ConfigEntries.Kind.ENUM, k == ConfigEntries.Kind.STRING,
                k == ConfigEntries.Kind.COLOR, k == ConfigEntries.Kind.LIST, min, max, step, e.choices(),
                on, value, saved, modified, atDefault, error, invalid, swatch);
    }

    private static String text(ConfigEntries.Entry e, Object v) {
        return e.kind() == ConfigEntries.Kind.BOOLEAN ? String.valueOf(v) : ConfigEntries.format(e.kind(), v);
    }

    /** The draft as the stored value would format it, so "2" and "2.0" or "#ff0000" and "#FF0000" compare equal. */
    private static String normalize(ConfigEntries.Entry e, String v) {
        Object parsed = ConfigEntries.parse(e.kind(), v);
        return parsed == null || e.validate(parsed) != null ? "\0" + v : ConfigEntries.format(e.kind(), parsed);
    }

    /** An empty component when {@code v} is a valid value, else why not. */
    private static Component validate(ConfigEntries.Entry e, String v) {
        Object parsed = ConfigEntries.parse(e.kind(), v);
        if (parsed == null) {
            return ConfigEntries.error(switch (e.kind()) {
                case INT -> "integer";
                case DOUBLE -> "number";
                case COLOR -> "color";
                default -> "invalid";
            });
        }
        Component error = e.validate(parsed);
        return error == null ? Component.empty() : error;
    }

    private static String draft(Row r) {
        return r.bool() ? String.valueOf(r.on().get()) : r.value().get();
    }

    /** A row's draft changed: record the undo step and refresh the totals. */
    private void changed(Row r) {
        String before = lastSeen.put(r, draft(r));
        if (!undoing && before != null && !before.equals(draft(r))) {
            undoSteps.push(() -> {
                if (r.bool()) {
                    r.on().set(Boolean.parseBoolean(before));
                } else {
                    r.value().set(before);
                }
            });
        }
        undoing = false;
        refreshTotals();
    }

    private void refreshTotals() {
        boolean anyModified = false;
        boolean anyInvalid = false;
        boolean restart = false;
        for (Row r : all) {
            boolean m = r.modified().get();
            anyModified |= m;
            anyInvalid |= r.invalid().get();
            restart |= m && r.restart();
        }
        canSave.set(anyModified && !anyInvalid);
        canUndo.set(!undoSteps.isEmpty());
        restartPending.set(restart);
        status.set(anyInvalid ? Component.translatable("ixchel_sample.config.status.invalid")
                : anyModified ? Component.translatable("ixchel_sample.config.status.unsaved") : Component.empty());
    }

    private void addItem(String text) {
        List<Item> list = new ArrayList<>(items.get());
        list.add(new Item(nextItem++, state(text)));
        items.set(List.copyOf(list));
    }

    private void saveAll() {
        if (!canSave.get()) {
            return;
        }
        for (Row r : all) {
            ConfigEntries.Entry e = byId.get(r.id());
            Object v = r.bool() ? r.on().get() : ConfigEntries.parse(e.kind(), r.value().get());
            e.set(v);
            r.saved().set(text(e, e.value()));
        }
        entries.save();
        undoSteps.clear();
        refreshTotals();
        status.set(Component.translatable("ixchel_sample.config.status.saved"));
    }

    private void closeLater() {
        scope().post(close);
    }
}
