package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.paint.ImageMeasurer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Natural image sizes from the PNG header of the resource, one texel per GUI pixel, as vanilla
 * textures are drawn. Sizes, misses included, are cached for the life of the document; a resource
 * reload loads a new document.
 */
public final class McImageMeasurer implements ImageMeasurer {
    private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;
    private static final int IHDR = 0x49484452;

    private final ResourceManager resources;
    private final Map<String, Size> sizes = new HashMap<>();

    public McImageMeasurer(ResourceManager resources) {
        this.resources = resources;
    }

    @Override
    public Size naturalSize(String src) {
        if (sizes.containsKey(src)) {
            return sizes.get(src);
        }
        Size size = read(src);
        sizes.put(src, size);
        return size;
    }

    private Size read(String src) {
        ResourceLocation id = ResourceLocation.tryParse(src);
        Optional<Resource> resource = id == null ? Optional.empty() : resources.getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream raw = resource.get().open(); DataInputStream in = new DataInputStream(raw)) {
            // Signature, then the IHDR chunk: length, type, width, height.
            if (in.readLong() != PNG_SIGNATURE) {
                return null;
            }
            in.readInt();
            if (in.readInt() != IHDR) {
                return null;
            }
            int width = in.readInt();
            int height = in.readInt();
            return width > 0 && height > 0 ? new Size(width, height) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
