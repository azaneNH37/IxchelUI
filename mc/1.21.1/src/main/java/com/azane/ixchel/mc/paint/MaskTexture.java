package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.paint.MaskAtlas;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * A host mod's {@link MaskAtlas} and the texture it is uploaded to, shared by the host's painters:
 * white texels whose alpha is the mask. Each host has two: {@code <modid>:ixchel/mask_atlas}, sampled
 * linearly, and {@code <modid>:ixchel/mask_atlas_crisp} for {@code shape-rendering: crisp-edges},
 * sampled nearest. Render thread only.
 */
final class MaskTexture {
    private static final Map<String, MaskTexture> BY_HOST = new HashMap<>();
    private static final Map<String, MaskTexture> CRISP_BY_HOST = new HashMap<>();

    final MaskAtlas atlas;
    private final ResourceLocation location;
    private final boolean crisp;
    private DynamicTexture texture;
    /** The painter drawing now, whose pending quads a reset must draw while their masks are still there. */
    McPainter active;

    private MaskTexture(String modid, boolean crisp) {
        this.crisp = crisp;
        location = ResourceLocation.fromNamespaceAndPath(modid, crisp ? "ixchel/mask_atlas_crisp" : "ixchel/mask_atlas");
        atlas = new MaskAtlas(() -> {
            if (active != null) {
                active.flushBatch();
            }
        });
    }

    static MaskTexture forHost(String modid, boolean crisp) {
        return (crisp ? CRISP_BY_HOST : BY_HOST).computeIfAbsent(modid, id -> new MaskTexture(id, crisp));
    }

    /** Uploads the texels written since the last call, registering the texture on first use. */
    ResourceLocation upload() {
        if (texture == null) {
            texture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, MaskAtlas.SIZE, MaskAtlas.SIZE, true));
            Minecraft.getInstance().getTextureManager().register(location, texture);
        }
        if (atlas.dirty()) {
            int x0 = atlas.dirtyX();
            int y0 = atlas.dirtyY();
            int w = atlas.dirtyWidth();
            int h = atlas.dirtyHeight();
            byte[] alpha = atlas.pixels();
            NativeImage image = texture.getPixels();
            for (int y = y0; y < y0 + h; y++) {
                for (int x = x0; x < x0 + w; x++) {
                    image.setPixelRGBA(x, y, (alpha[y * MaskAtlas.SIZE + x] & 0xFF) << 24 | 0xFFFFFF);
                }
            }
            texture.bind();
            // Clamped; linear so a mask's edge texels blend as the tessellator expects, nearest when crisp.
            image.upload(0, x0, y0, x0, y0, w, h, !crisp, true, false, false);
            atlas.clean();
        }
        return location;
    }
}
