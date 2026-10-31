package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.mc.paint.NativePainter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** The elements the mc layer adds to the core HTML subset, and how they are drawn. */
public final class McElements {
    /** The declaration file of {@link #TAGS}, a resource next to this class. */
    private static final String DECLARATIONS = "elements.txt";

    /**
     * {@link TagRegistry#HTML} plus {@code <mc-item bind:item="...">}, declared in this package's
     * {@code elements.txt}: an item drawn like a vanilla slot item, sized in CSS. Bind an
     * {@code ItemStack} snapshot when bindings must see changes, or a {@code Supplier<ItemStack>}
     * handle read at paint time when only the picture changes; keep the handle instance stable.
     */
    public static final TagRegistry TAGS = TagRegistry.HTML.extend().declare(declarations(), DECLARATIONS).build();

    /** The painter of each native element of {@link #TAGS}. */
    public static final Map<String, NativePainter> PAINTERS = Map.of(ItemElement.TAG, ItemElement.PAINTER);

    private McElements() {
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
