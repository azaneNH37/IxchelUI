package com.azane.ixchel.sample.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A UI-agnostic model of {@link SampleConfig#SPEC}: its values grouped by category in spec order,
 * each with its kind, bounds, choices, comment, default and current value. Values are plain Java
 * types: {@code Boolean}, {@code Integer}, {@code Double}, {@code String} (enums by constant name),
 * {@code List<String>} and {@code Integer} {@code 0xRRGGBB} for colors. Edits go to the loaded
 * config in memory through {@link Entry#set} and reach the file on {@link #save}.
 */
public final class ConfigEntries {
    public enum Kind {
        BOOLEAN, INT, DOUBLE, ENUM, STRING, LIST, COLOR
    }

    /**
     * The values of one config section.
     *
     * @param name the section's dot-joined path, empty for values at the top level
     */
    public record Category(String name, List<Entry> entries) {
        /** The humanized last path element, e.g. {@code compass} gives "Compass". */
        public String label() {
            return humanize(name.substring(name.lastIndexOf('.') + 1));
        }
    }

    /** One config value: what it is, where it lives and its stored value. */
    public static final class Entry {
        private final List<String> path;
        private final ForgeConfigSpec.ValueSpec spec;
        private final ForgeConfigSpec.ConfigValue<?> value;
        private final Kind kind;
        private final String comment;
        private final Number min, max;
        /** The enum constants for {@link Kind#ENUM}, else empty. */
        private final Enum<?>[] constants;

        private Entry(List<String> path, ForgeConfigSpec.ValueSpec spec, ForgeConfigSpec.ConfigValue<?> value) {
            this.path = List.copyOf(path);
            this.spec = spec;
            this.value = value;
            Object def = spec.getDefault();
            this.kind = kindOf(this.path, def);
            ForgeConfigSpec.Range<?> range = spec.getRange();
            this.min = range == null ? null : (Number) range.getMin();
            this.max = range == null ? null : (Number) range.getMax();
            Enum<?>[] none = {};
            this.constants = def instanceof Enum<?> e ? e.getDeclaringClass().getEnumConstants() : none;
            this.comment = stripGenerated(spec.getComment(), range, kind);
        }

        /** The dot-joined path, e.g. {@code compass.compassRange}. */
        public String path() {
            return String.join(".", path);
        }

        /** The last path element, e.g. {@code compassRange}. */
        public String name() {
            return path.get(path.size() - 1);
        }

        /** The humanized name, e.g. {@code compassRange} gives "Compass range". */
        public String label() {
            return humanize(name());
        }

        /** The spec comment without Forge's generated range and allowed-values lines; empty when none. */
        public String comment() {
            return comment;
        }

        public Kind kind() {
            return kind;
        }

        /** The lower bound of a ranged number or color, or {@code null} when unbounded. */
        public Number min() {
            return min;
        }

        /** The upper bound of a ranged number or color, or {@code null} when unbounded. */
        public Number max() {
            return max;
        }

        /** The enum constant names for {@link Kind#ENUM}, else empty. */
        public List<String> choices() {
            return Arrays.stream(constants).map(Enum::name).toList();
        }

        public Object defaultValue() {
            return toModel(spec.getDefault());
        }

        /** The value currently held by the loaded config, including unsaved {@link #set}s. */
        public Object value() {
            return toModel(value.get());
        }

        /** Whether a change only takes effect after rejoining the world. */
        public boolean needsRestart() {
            return spec.needsWorldRestart();
        }

        /**
         * Checks a candidate in this entry's model type against the spec.
         *
         * @return {@code null} when valid, else a short message for the user
         */
        public String validate(Object candidate) {
            switch (kind) {
                case BOOLEAN -> {
                    if (!(candidate instanceof Boolean)) return "Must be true or false";
                }
                case INT, COLOR -> {
                    if (!(candidate instanceof Integer)) return kind == Kind.COLOR ? "Must be a color #RRGGBB" : "Must be a whole number";
                    String bounds = checkBounds((Integer) candidate);
                    if (bounds != null) return bounds;
                }
                case DOUBLE -> {
                    if (!(candidate instanceof Number n) || !Double.isFinite(n.doubleValue())) return "Must be a number";
                    String bounds = checkBounds(n.doubleValue());
                    if (bounds != null) return bounds;
                }
                case ENUM -> {
                    if (!(candidate instanceof String s) || constant(s) == null) {
                        return "Must be one of " + String.join(", ", choices());
                    }
                }
                case STRING -> {
                    if (!(candidate instanceof String)) return "Must be text";
                }
                case LIST -> {
                    if (!(candidate instanceof List<?> list)) return "Must be a list";
                    for (Object element : list) {
                        if (!(element instanceof String) || !spec.test(List.of(element))) return "Invalid entry: " + element;
                    }
                }
            }
            return spec.test(toStored(candidate)) ? null : "Invalid value";
        }

        /**
         * Writes a value to the loaded config in memory; {@link ConfigEntries#save} persists it.
         *
         * @throws IllegalArgumentException when {@link #validate} rejects the value
         */
        @SuppressWarnings("unchecked")
        public void set(Object v) {
            String error = validate(v);
            if (error != null) {
                throw new IllegalArgumentException(path() + ": " + error);
            }
            ((ForgeConfigSpec.ConfigValue<Object>) value).set(toStored(v));
        }

        private String checkBounds(double v) {
            if (min != null && v < min.doubleValue() || max != null && v > max.doubleValue()) {
                return "Must be between " + format(kind, min) + " and " + format(kind, max);
            }
            return null;
        }

        private Enum<?> constant(String name) {
            for (Enum<?> c : constants) {
                if (c.name().equals(name)) return c;
            }
            return null;
        }

        /** Model value to the type the config value holds. */
        private Object toStored(Object v) {
            return switch (kind) {
                case DOUBLE -> ((Number) v).doubleValue();
                case ENUM -> constant((String) v);
                case LIST -> new ArrayList<>((List<?>) v);
                default -> v;
            };
        }

        /** Stored or default value to the model type. */
        private Object toModel(Object v) {
            return switch (kind) {
                case ENUM -> v instanceof Enum<?> e ? e.name() : String.valueOf(v);
                case LIST -> ((List<?>) v).stream().map(String::valueOf).toList();
                case DOUBLE -> ((Number) v).doubleValue();
                case INT, COLOR -> ((Number) v).intValue();
                default -> v;
            };
        }
    }

    private final List<Category> categories;

    private ConfigEntries(List<Category> categories) {
        this.categories = categories;
    }

    /** Whether Forge has loaded the config file, so values can be read and written. */
    public static boolean isLoaded() {
        return SampleConfig.SPEC.isLoaded();
    }

    /**
     * Builds the model from the loaded spec.
     *
     * @throws IllegalStateException when the config is not loaded yet
     */
    public static ConfigEntries load() {
        if (!isLoaded()) {
            throw new IllegalStateException("The ixchelsample client config is not loaded yet");
        }
        List<Category> categories = new ArrayList<>();
        collect(SampleConfig.SPEC.getSpec(), new ArrayList<>(), categories);
        return new ConfigEntries(List.copyOf(categories));
    }

    /** The sections that hold values, in spec order. */
    public List<Category> categories() {
        return categories;
    }

    /** Writes the config file with every value {@link Entry#set} so far. */
    public void save() {
        SampleConfig.SPEC.save();
    }

    /**
     * Parses user text into the model type of a kind: {@code true}/{@code false}, a whole number,
     * a finite decimal, a color as {@code #RRGGBB} or {@code RRGGBB}, a comma-separated list, or
     * the text itself for strings and enum names.
     *
     * @return the value, or {@code null} when the text does not parse
     */
    public static Object parse(Kind kind, String text) {
        String t = text.trim();
        return switch (kind) {
            case BOOLEAN -> t.equalsIgnoreCase("true") ? Boolean.TRUE : t.equalsIgnoreCase("false") ? Boolean.FALSE : null;
            case INT -> {
                try {
                    yield Integer.parseInt(t);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case DOUBLE -> {
                try {
                    double d = Double.parseDouble(t);
                    yield Double.isFinite(d) ? d : null;
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case COLOR -> {
                String hex = t.startsWith("#") ? t.substring(1) : t;
                yield hex.length() == 6 && hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)
                        ? Integer.parseInt(hex, 16) : null;
            }
            case ENUM -> t;
            case STRING -> text;
            case LIST -> Arrays.stream(t.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        };
    }

    /**
     * Formats a model value as user text, the inverse of {@link #parse}: colors as {@code #RRGGBB},
     * decimals rounded to six places without trailing zeros, lists comma-separated.
     */
    public static String format(Kind kind, Object value) {
        return switch (kind) {
            case COLOR -> String.format(Locale.ROOT, "#%06X", ((Number) value).intValue() & 0xFFFFFF);
            case DOUBLE -> {
                BigDecimal d = BigDecimal.valueOf(((Number) value).doubleValue())
                        .setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
                yield (d.scale() < 1 ? d.setScale(1) : d).toPlainString();
            }
            case LIST -> String.join(", ", ((List<?>) value).stream().map(String::valueOf).toList());
            case ENUM -> value instanceof Enum<?> e ? e.name() : String.valueOf(value);
            default -> String.valueOf(value);
        };
    }

    /** Adds the values directly under {@code config} as one category, then its sub-sections. */
    private static void collect(UnmodifiableConfig config, List<String> path, List<Category> out) {
        List<Entry> entries = new ArrayList<>();
        int at = out.size();
        for (Map.Entry<String, Object> e : config.valueMap().entrySet()) {
            path.add(e.getKey());
            if (e.getValue() instanceof UnmodifiableConfig section) {
                collect(section, path, out);
            } else {
                ForgeConfigSpec.ConfigValue<?> value = SampleConfig.SPEC.getValues().get(path);
                entries.add(new Entry(path, (ForgeConfigSpec.ValueSpec) e.getValue(), value));
            }
            path.remove(path.size() - 1);
        }
        if (!entries.isEmpty()) {
            out.add(at, new Category(String.join(".", path), List.copyOf(entries)));
        }
    }

    private static Kind kindOf(List<String> path, Object def) {
        if (def instanceof Boolean) return Kind.BOOLEAN;
        if (def instanceof Integer) return SampleConfig.COLORS.contains(path) ? Kind.COLOR : Kind.INT;
        if (def instanceof Double) return Kind.DOUBLE;
        if (def instanceof Enum<?>) return Kind.ENUM;
        if (def instanceof String) return Kind.STRING;
        if (def instanceof List<?>) return Kind.LIST;
        throw new IllegalStateException("Unsupported config value type at " + String.join(".", path) + ": "
                + def.getClass().getName());
    }

    /** Drops the lines Forge appends to the comment of ranged and enum values; those are shown from the bounds and choices. */
    private static String stripGenerated(String comment, ForgeConfigSpec.Range<?> range, Kind kind) {
        if (comment == null) return "";
        List<String> lines = new ArrayList<>(Arrays.asList(comment.split("\n")));
        if (range != null) lines.remove("Range: " + range);
        if (kind == Kind.ENUM) lines.removeIf(l -> l.startsWith("Allowed Values: "));
        return String.join("\n", lines);
    }

    /** {@code compassRange} gives "Compass range". */
    private static String humanize(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' || c == '-') {
                sb.append(' ');
            } else if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(name.charAt(i - 1))) {
                sb.append(' ').append(Character.toLowerCase(c));
            } else {
                sb.append(sb.isEmpty() ? Character.toUpperCase(c) : c);
            }
        }
        return sb.toString();
    }
}
