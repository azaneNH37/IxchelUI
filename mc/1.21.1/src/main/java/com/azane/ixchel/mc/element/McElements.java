package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.mc.paint.NativePainter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The elements the mc layer adds to the core HTML subset, and how they are drawn. */
public final class McElements {
    /** The declaration file of {@link #TAGS}, a resource next to this class. */
    private static final String DECLARATIONS = "elements.txt";

    /**
     * {@link TagRegistry#HTML} plus the elements declared in this package's {@code elements.txt}:
     * <ul>
     * <li>{@code <mc-item bind:item="...">}: an item drawn like a vanilla slot item, sized in CSS. Bind an
     * {@code ItemStack} snapshot when bindings must see changes, or a {@code Supplier<ItemStack>}
     * handle read at paint time when only the picture changes; keep the handle instance stable.</li>
     * <li>{@code <mc-tooltip-lines bind:item="...">}: the lines of the item's vanilla tooltip as a
     * column of text, filled by {@link TooltipLines}; in an {@code <ix-tooltip>}, only while it shows.</li>
     * </ul>
     */
    public static final TagRegistry TAGS = TagRegistry.HTML.extend().declare(declarations(), DECLARATIONS).build();

    /** The painter of each native element of {@link #TAGS}. */
    public static final Map<String, NativePainter> PAINTERS = Map.of(ItemElement.TAG, ItemElement.PAINTER);
    /** The file under {@code assets/<hostModId>/} declaring the host's own native elements. */
    public static final String HOST_DECLARATIONS = "ui/elements.txt";
    /** Registered during mod setup, which runs on worker threads. */
    private static final Map<String, Map<String, NativePainter>> HOST_PAINTERS = new ConcurrentHashMap<>();

    private McElements() {
    }

    /**
     * Sets the painter of a native element the host declares in {@code assets/<hostModId>/ui/elements.txt},
     * in the format of {@link TagRegistry.Builder#declare}. Call it before the host's first document
     * loads. Painters stay apart per host mod id, so two hosts may use the same tag.
     *
     * @param painter draws the element; it runs on the render thread for every frame the element paints
     */
    public static void registerPainter(String hostModId, String tag, NativePainter painter) {
        HOST_PAINTERS.computeIfAbsent(hostModId, k -> new ConcurrentHashMap<>()).put(tag, painter);
    }

    /** {@link #PAINTERS} plus the painters {@code hostModId} registered. */
    public static Map<String, NativePainter> painters(String hostModId) {
        Map<String, NativePainter> all = new HashMap<>(PAINTERS);
        all.putAll(HOST_PAINTERS.getOrDefault(hostModId, Map.of()));
        return all;
    }

    /** The text of {@link #DECLARATIONS}, shipped with this class; missing is a packaging bug. */
    private static String declarations() {
        try (InputStream in = McElements.class.getResourceAsStream(DECLARATIONS)) {
            if (in == null) {
                throw new IllegalStateException("missing " + DECLARATIONS + " next to " + McElements.class.getName());
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
