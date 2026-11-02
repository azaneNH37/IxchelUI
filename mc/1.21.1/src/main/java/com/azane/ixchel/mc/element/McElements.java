package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.template.NativeElement;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.mc.paint.NativePainter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The native elements of the mc layer and of each host. Every one is a class with its
 * {@link NativeElement} declaration: the painter of a drawn element, the filler of a lines element.
 * The built-in and the host's own go the same way, and the kit reads the same declarations.
 */
public final class McElements {
    private static final List<NativePainter<?>> PAINTERS = List.of(new ItemElement());

    /**
     * The HTML vocabulary plus the mc layer's elements:
     * <ul>
     * <li>{@code <mc-item bind:item="...">}: {@link ItemElement}.</li>
     * <li>{@code <mc-tooltip-lines bind:item="...">}: the lines of the item's vanilla tooltip as a
     * column of text, filled by {@link TooltipLines}; in an {@code <ix-tooltip>}, only while it shows.</li>
     * </ul>
     */
    public static final TagRegistry TAGS = declare(com.azane.ixchel.core.html.HtmlElements.TAGS.extend(), PAINTERS).add(com.azane.ixchel.core.html.NativeElements.declare(TooltipLines.class)).build();

    /** Registered during mod setup, which runs on worker threads. */
    private static final Map<String, List<NativePainter<?>>> HOST_PAINTERS = new ConcurrentHashMap<>();

    /** The elements of one host's documents: their types and the painter of each drawn one, by tag. */
    public record Hosted(TagRegistry tags, Map<String, NativePainter<?>> painters) {
    }

    private McElements() {
    }

    /**
     * Adds the native element {@code painter}'s class declares with {@link NativeElement} to the
     * documents of {@code hostModId}. Call it during mod setup, before the host's first document
     * loads; hosts stay apart, so two may use the same tag.
     *
     * @param painter draws the element; it runs on the render thread for every frame the element paints
     */
    public static void register(String hostModId, NativePainter<?> painter) {
        HOST_PAINTERS.computeIfAbsent(hostModId, k -> new CopyOnWriteArrayList<>()).add(painter);
    }

    /**
     * {@code base} (such as {@link #TAGS}) plus the elements {@code hostModId} registered.
     *
     * @throws IllegalArgumentException if a registered painter's declaration is invalid or its tag is taken
     */
    public static Hosted forHost(String hostModId, TagRegistry base) {
        List<NativePainter<?>> own = HOST_PAINTERS.getOrDefault(hostModId, List.of());
        Map<String, NativePainter<?>> painters = new HashMap<>();
        for (NativePainter<?> p : PAINTERS) {
            painters.put(tagOf(p), p);
        }
        for (NativePainter<?> p : own) {
            painters.put(tagOf(p), p);
        }
        return new Hosted(declare(base.extend(), own).build(), painters);
    }

    private static TagRegistry.Builder declare(TagRegistry.Builder b, List<NativePainter<?>> painters) {
        for (NativePainter<?> p : painters) {
            b.add(com.azane.ixchel.core.html.NativeElements.declare(p.getClass()));
        }
        return b;
    }

    private static String tagOf(NativePainter<?> p) {
        NativeElement d = p.getClass().getAnnotation(NativeElement.class);
        if (d == null) {
            throw new IllegalArgumentException(p.getClass().getName() + " has no @NativeElement");
        }
        return d.tag();
    }
}
