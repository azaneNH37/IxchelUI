package com.azane.ixchel.mc.document;

import com.azane.ixchel.core.binding.Bindings;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
     * @param files the template and every linked stylesheet under the host namespace, as paths
     *              under {@code assets/<hostModId>/} in load order; what a development watcher
     *              should watch
     */
    public record Loaded(UiDocument document, List<String> files) {
    }

    /**
     * Loads a document from a template and the stylesheets it links, measured with the vanilla
     * font; images for {@code background-size: auto} are measured from {@code resources}. A
     * {@code <link>} href of the form {@code ns:path} names that resource (the host namespace reads
     * through the development source when present, other namespaces read packed resources); any
     * other href is relative to the template's directory ({@code ../common.css} allowed). Error
     * positions name files as {@code modid:path}. Bound {@link Component}s are flattened with
     * {@link Component#getString()}; their colors and styles are dropped, CSS styles the text.
     *
     * @param model the view model {@code bind:*} attributes refer to, or {@code null}
     */
    public Loaded load(ResourceManager resources, String templatePath, TagRegistry tags, ViewModel model) {
        List<String> files = new ArrayList<>();
        files.add(templatePath);
        int slash = templatePath.lastIndexOf('/');
        String dir = slash < 0 ? "" : templatePath.substring(0, slash);
        UiDocument document = UiDocument.load(read(resources, templatePath), name(templatePath), href -> {
            int colon = href.indexOf(':');
            if (colon >= 0) {
                String ns = href.substring(0, colon);
                String path = href.substring(colon + 1);
                if (!ns.equals(hostModId)) {
                    return new UiDocument.CssSource(readPacked(resources, ResourceLocation.fromNamespaceAndPath(ns, path)), href);
                }
                return sheet(resources, files, path);
            }
            return sheet(resources, files, resolve(dir, href));
        }, tags, new McTextMeasurer(Minecraft.getInstance().font), new McImageMeasurer(resources), model, McResources::text);
        return new Loaded(document, List.copyOf(files));
    }

    private UiDocument.CssSource sheet(ResourceManager resources, List<String> files, String path) {
        String css = read(resources, path);
        files.add(path);
        return new UiDocument.CssSource(css, name(path));
    }

    /** Resolves {@code href} against the directory {@code dir} (no trailing slash; empty for the root), normalising {@code .} and {@code ..}. */
    static String resolve(String dir, String href) {
        Deque<String> parts = new ArrayDeque<>();
        if (!dir.isEmpty()) {
            for (String p : dir.split("/")) {
                parts.addLast(p);
            }
        }
        for (String p : href.split("/")) {
            if (p.isEmpty() || p.equals(".")) {
                continue;
            }
            if (p.equals("..")) {
                if (parts.isEmpty()) {
                    throw new IllegalArgumentException("href leaves the resource root: " + href);
                }
                parts.removeLast();
            } else {
                parts.addLast(p);
            }
        }
        return String.join("/", parts);
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
