package com.azane.ixchel.mc.paint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The library's own programs for filters, blend modes and backdrops, built from the GLSL next to this
 * class on first use. They are not resource-pack content, so no loader event registers them and a
 * resource reload leaves them working: vanilla's reload deletes the compiled shader objects it finds
 * in its global program table, ours included, but a linked program does not need them. That table is
 * keyed by name, so the names carry the host mod id ({@code <modid>_ixchel_<name>}) and library
 * copies of different versions do not share programs. Render thread only.
 */
final class FilterShaders {
    private static final String DIR = "shaders/";
    private static final Map<String, FilterShaders> BY_HOST = new HashMap<>();

    /** A premultiplied layer at {@code Opacity}; {@code Under} 1 composites it over white first. */
    final ShaderInstance composite;
    /** {@code ColorMatrix} and {@code ColorOffset} applied to the unpremultiplied color. */
    final ShaderInstance matrix;
    /** One direction ({@code Step}, a texel) of a Gaussian blur of {@code Sigma} texels. */
    final ShaderInstance blur;
    /** The input's coverage moved by {@code Offset} and tinted with the premultiplied {@code Color}. */
    final ShaderInstance shadow;
    /** The image as it is. */
    final ShaderInstance copy;
    /** A filtered backdrop drawn back inside a rounded box. */
    final ShaderInstance backdrop;

    private FilterShaders(String modid) {
        String prefix = modid + "_ixchel_";
        ResourceProvider classpath = id -> open(id.getPath(), prefix);
        composite = build(classpath, prefix, "composite");
        matrix = build(classpath, prefix, "matrix");
        blur = build(classpath, prefix, "blur");
        shadow = build(classpath, prefix, "shadow");
        backdrop = build(classpath, prefix, "backdrop");
        copy = build(classpath, prefix, "copy");
    }

    static FilterShaders forHost(String modid) {
        return BY_HOST.computeIfAbsent(modid, FilterShaders::new);
    }

    /**
     * Makes {@code s} current, sampling {@code texture}, with the current matrices. Set blending after
     * this, then {@link #draw}: {@code ShaderInstance.apply} applies the program's own blend mode,
     * opaque for ours, which turns blending off whenever the last mode applied was not opaque.
     */
    static void apply(ShaderInstance s, int texture) {
        s.setSampler("Sampler0", texture);
        if (s.MODEL_VIEW_MATRIX != null) {
            s.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }
        if (s.PROJECTION_MATRIX != null) {
            s.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }
        s.apply();
    }

    /** Draws {@code buffer} with {@code s}, made current by {@link #apply}, and unbinds it. */
    static void draw(ShaderInstance s, BufferBuilder.RenderedBuffer buffer) {
        BufferUploader.draw(buffer);
        s.clear();
    }

    private static ShaderInstance build(ResourceProvider classpath, String prefix, String name) {
        try {
            return new ShaderInstance(classpath, prefix + name, DefaultVertexFormat.POSITION_TEX);
        } catch (IOException e) {
            throw new UncheckedIOException("Ixchel UI: building shader " + name + " failed", e);
        }
    }

    /**
     * {@code shaders/core/<prefix><file>} as the file next to this class; a program definition gets
     * the prefix put into the program names it refers to.
     */
    private static Optional<Resource> open(String path, String prefix) {
        String file = path.substring(path.lastIndexOf('/') + 1);
        if (!file.startsWith(prefix)) {
            return Optional.empty();
        }
        String name = DIR + file.substring(prefix.length());
        byte[] bytes;
        try (InputStream in = FilterShaders.class.getResourceAsStream(name)) {
            if (in == null) {
                return Optional.empty();
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (name.endsWith(".json")) {
            bytes = new String(bytes, StandardCharsets.UTF_8).replace("{prefix}", prefix).getBytes(StandardCharsets.UTF_8);
        }
        byte[] content = bytes;
        // The pack only names the source in compile errors.
        return Optional.of(new Resource(Minecraft.getInstance().getVanillaPackResources(), () -> new ByteArrayInputStream(content)));
    }
}
