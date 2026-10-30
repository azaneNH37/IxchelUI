package com.azane.ixchel.mc.document;

import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.mc.paint.McImageMeasurer;
import com.azane.ixchel.mc.paint.McTextMeasurer;
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
final class McResources {
    private final String hostModId;
    private final DevResources dev;

    /** @param dev the development source, or {@code null} to read packed resources */
    public McResources(String hostModId, DevResources dev) {
        this.hostModId = hostModId;
        this.dev = dev;
    }

    /**
     * A loaded document and the files it was read from.
     *
     * @param files the template and every file it links, directly or through components, under the
     *              host namespace, as paths under {@code assets/<hostModId>/} in load order; what a
     *              development watcher should watch
     */
    public record Loaded(UiDocument document, List<String> files) {
    }

    /**
     * Loads a document from a template and the files it links, measured with the vanilla font;
     * images for {@code background-size: auto} are measured from {@code resources}. A {@code <link>}
     * href of the form {@code ns:path} names that resource (the host namespace reads through the
     * development source when present, other namespaces read packed resources); any other href is
     * relative to the linking file's directory ({@code ../common.css} allowed). Error positions name
     * files as {@code modid:path}. Bound {@link Component}s are flattened with
     * {@link Component#getString()}; their colors and styles are dropped, CSS styles the text.
     *
     * @param model the view model {@code bind:*} attributes refer to, or {@code null}
     */
    public Loaded load(ResourceManager resources, String templatePath, TagRegistry tags, ViewModel model) {
        UiDocument document = UiDocument.load(read(resources, templatePath), name(templatePath), id -> {
            int colon = id.indexOf(':');
            String ns = id.substring(0, colon);
            String path = id.substring(colon + 1);
            return ns.equals(hostModId) ? read(resources, path) : readPacked(resources, ResourceLocation.fromNamespaceAndPath(ns, path));
        }, tags, new McTextMeasurer(Minecraft.getInstance().font), new McImageMeasurer(resources), model, McResources::text);
        String host = name("");
        List<String> files = new ArrayList<>();
        for (String id : document.files()) {
            if (id.startsWith(host)) {
                files.add(id.substring(host.length()));
            }
        }
        return new Loaded(document, List.copyOf(files));
    }

    public String read(ResourceManager resources, String path) {
        if (dev != null) {
            return dev.read(path);
        }
        return readPacked(resources, ResourceLocation.fromNamespaceAndPath(hostModId, path));
    }

    private static String readPacked(ResourceManager resources, ResourceLocation id) {
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
