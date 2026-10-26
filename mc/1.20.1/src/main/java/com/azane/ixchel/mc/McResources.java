package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.Bindings;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.core.template.TagRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads UI files from the host mod's resources ({@code assets/<hostModId>/<path>}), or from its
 * source tree when a {@link DevResources} is given.
 */
public final class McResources {
    private final String hostModId;
    private final DevResources dev;

    /** @param dev the development source, or {@code null} to read packed resources */
    public McResources(String hostModId, DevResources dev) {
        this.hostModId = hostModId;
        this.dev = dev;
    }

    /**
     * Loads a document from a template and its stylesheets, measured with the vanilla font. Error
     * positions name files as {@code modid:path}. Bound {@link Component}s are flattened with
     * {@link Component#getString()}; their colors and styles are dropped, CSS styles the text.
     *
     * @param model the view model {@code bind:*} attributes refer to, or {@code null}
     */
    public UiDocument load(ResourceManager resources, String templatePath, List<String> cssPaths, TagRegistry tags,
                           ViewModel model) {
        List<UiDocument.CssSource> sheets = new ArrayList<>(cssPaths.size());
        for (String path : cssPaths) {
            sheets.add(new UiDocument.CssSource(read(resources, path), name(path)));
        }
        return UiDocument.load(read(resources, templatePath), name(templatePath), sheets, tags,
                new McTextMeasurer(Minecraft.getInstance().font), model, McResources::text);
    }

    public String read(ResourceManager resources, String path) {
        if (dev != null) {
            return dev.read(path);
        }
        ResourceLocation id = new ResourceLocation(hostModId, path);
        try (InputStream in = resources.getResourceOrThrow(id).open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + id, e);
        }
    }

    private static String text(Object value) {
        return value instanceof Component c ? c.getString() : Bindings.PLAIN_TEXT.apply(value);
    }

    private String name(String path) {
        return hostModId + ":" + path;
    }
}
