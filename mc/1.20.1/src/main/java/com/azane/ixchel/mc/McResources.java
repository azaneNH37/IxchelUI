package com.azane.ixchel.mc;

import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.core.template.TagRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads UI files from the host mod's resources ({@code assets/<hostModId>/<path>}). */
public final class McResources {
    private final String hostModId;

    public McResources(String hostModId) {
        this.hostModId = hostModId;
    }

    /**
     * Loads a document from a template and its stylesheets, measured with the vanilla font. Error
     * positions name files as {@code modid:path}.
     */
    public UiDocument load(ResourceManager resources, String templatePath, List<String> cssPaths, TagRegistry tags) {
        List<UiDocument.CssSource> sheets = new ArrayList<>(cssPaths.size());
        for (String path : cssPaths) {
            sheets.add(new UiDocument.CssSource(read(resources, path), name(path)));
        }
        return UiDocument.load(read(resources, templatePath), name(templatePath), sheets, tags,
                new McTextMeasurer(Minecraft.getInstance().font));
    }

    public String read(ResourceManager resources, String path) {
        ResourceLocation id = new ResourceLocation(hostModId, path);
        try (InputStream in = resources.getResourceOrThrow(id).open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + id, e);
        }
    }

    private String name(String path) {
        return hostModId + ":" + path;
    }
}
