package com.azane.ixchel.core.binding;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The names a view model class offers its templates, read from the class alone: every non-static,
 * non-private field whose declared type is a {@link Cell} or {@link Action}, a subclass's field
 * hiding a superclass's of the same name. A template loaded at runtime and the offline lint see the
 * same table, so a check passes or fails in both.
 *
 * <pre>{@code
 * class QuestsView extends ViewModel {
 *     final Value<String> filter = state("");      // CELL, String, writable
 *     final Derived<List<Quest>> shown = derive(filter, ...);   // CELL, List<Quest>, read-only
 *     final Action<Quest> track = action(q -> ...); // ACTION, Quest
 * }
 * }</pre>
 */
public final class ModelShape {
    public enum Kind { CELL, ACTION }

    /**
     * One name.
     *
     * @param type     the value type of a cell ({@code Object} when raw), or the argument type of an
     *                 action ({@code Void} for none, {@code null} when raw)
     * @param writable whether templates may write the cell: its declared type is {@link Value}
     * @param owner    the class declaring the field
     */
    public record Member(String name, Kind kind, Type type, boolean writable, Class<?> owner) {
    }

    private static final ClassValue<ModelShape> SHAPES = new ClassValue<>() {
        @Override
        protected ModelShape computeValue(Class<?> type) {
            return new ModelShape(type);
        }
    };

    private final Class<?> type;
    private final Map<String, Member> members;
    /** The field of each member, read only through {@link ModelAccess}. */
    final Map<String, Field> fields;

    private ModelShape(Class<?> type) {
        this.type = type;
        Map<String, Member> m = new LinkedHashMap<>();
        Map<String, Field> f = new LinkedHashMap<>();
        for (Class<?> c = type; c != ViewModel.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                int mods = field.getModifiers();
                boolean action = field.getType() == Action.class;
                if (Modifier.isStatic(mods) || Modifier.isPrivate(mods) || m.containsKey(field.getName())
                        || !(action || Cell.class.isAssignableFrom(field.getType()))) {
                    continue;
                }
                Type generic = field.getGenericType();
                Type arg = generic instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : null;
                m.put(field.getName(), action
                        ? new Member(field.getName(), Kind.ACTION, arg, false, c)
                        : new Member(field.getName(), Kind.CELL, arg == null ? Object.class : arg,
                        Value.class.isAssignableFrom(field.getType()), c));
                f.put(field.getName(), field);
            }
        }
        this.members = Collections.unmodifiableMap(m);
        this.fields = Map.copyOf(f);
    }

    /** The shape of {@code type}, computed once per class. */
    public static ModelShape of(Class<? extends ViewModel> type) {
        return SHAPES.get(type);
    }

    public Class<?> type() {
        return type;
    }

    /** Every name, a subclass's before its superclass's. */
    public Map<String, Member> members() {
        return members;
    }
}
