package com.azane.ixchel.mc.paint;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The font file of an {@code @font-face}, a {@code .ttf} or {@code .otf}, rasterized here with
 * stb_truetype into the host's mask atlas instead of through the game's font manager: no font id is
 * registered, no resource pack is needed, and ModernUI, which takes over the game's text drawing,
 * leaves these glyphs alone, so a page lays out and draws them alike with and without it.
 *
 * <p>A face sits on the vanilla font's grid. At a crisp size whose glyph pixel is {@code p} screen
 * pixels, a line is {@code 9p} pixels: the face is scaled so its ascent and descent span them, its
 * baseline is {@code 7p} below the line's top, and each glyph is rasterized at that pixel size, so it
 * lands on whole screen pixels. Advances are rounded to whole pixels; there is no kerning. Faces are
 * loaded once per file id and kept for the game's life. Render thread only.
 */
final class Face {
    private static final Map<String, Optional<Face>> LOADED = new HashMap<>();

    /** Kept: {@link #info} reads the font from it. */
    @SuppressWarnings("unused")
    private final ByteBuffer data;
    private final STBTTFontinfo info;
    /** Each code point's glyph index, 0 for none. */
    private final Map<Integer, Integer> glyphs = new HashMap<>();
    /** Each glyph at each pixel size, by {@link #key}: advance, then its bitmap's x0, y0, x1, y1 from the pen on the baseline. */
    private final Map<Long, int[]> metrics = new HashMap<>();

    private Face(ByteBuffer data, STBTTFontinfo info) {
        this.data = data;
        this.info = info;
    }

    /** Whether the font id {@code id} names a font file rather than a Minecraft font. */
    static boolean isFace(String id) {
        return id.endsWith(".ttf") || id.endsWith(".otf");
    }

    /**
     * The face of the file {@code id}, read through {@code bytes} the first time; {@code null} when the
     * file is missing or not a font, which is remembered too.
     */
    static Face load(String id, Supplier<byte[]> bytes) {
        return LOADED.computeIfAbsent(id, k -> {
            byte[] b;
            try {
                b = bytes.get();
            } catch (RuntimeException e) {
                return Optional.empty();
            }
            if (b == null) {
                return Optional.empty();
            }
            ByteBuffer data = BufferUtils.createByteBuffer(b.length).put(b).flip();
            STBTTFontinfo info = STBTTFontinfo.create();
            if (!STBTruetype.stbtt_InitFont(info, data)) {
                return Optional.empty();
            }
            return Optional.of(new Face(data, info));
        }).orElse(null);
    }

    /** The face {@link #load} loaded for {@code id}; {@code null} if it has not or could not. */
    static Face get(String id) {
        Optional<Face> face = LOADED.get(id);
        return face == null ? null : face.orElse(null);
    }

    /** The glyph of {@code codePoint}; 0 when the face has none, and the text falls back to the default font. */
    int glyph(int codePoint) {
        Integer g = glyphs.get(codePoint);
        if (g == null) {
            g = STBTruetype.stbtt_FindGlyphIndex(info, codePoint);
            glyphs.put(codePoint, g);
        }
        return g;
    }

    /** The end of the run from {@code start} that the face has no glyphs for. */
    int fallbackEnd(String text, int start) {
        int i = start;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (glyph(cp) != 0) {
                break;
            }
            i += Character.charCount(cp);
        }
        return i;
    }

    /**
     * {@code glyph} on a line of {@code px} screen pixels: its advance, then the box of its bitmap,
     * {@code x0, y0, x1, y1}, from the pen on the baseline, y down.
     */
    int[] metrics(int glyph, int px) {
        return metrics.computeIfAbsent(key(glyph, px), k -> {
            float scale = STBTruetype.stbtt_ScaleForPixelHeight(info, px);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer advance = stack.mallocInt(1);
                IntBuffer bearing = stack.mallocInt(1);
                STBTruetype.stbtt_GetGlyphHMetrics(info, glyph, advance, bearing);
                IntBuffer x0 = stack.mallocInt(1);
                IntBuffer y0 = stack.mallocInt(1);
                IntBuffer x1 = stack.mallocInt(1);
                IntBuffer y1 = stack.mallocInt(1);
                STBTruetype.stbtt_GetGlyphBitmapBox(info, glyph, scale, scale, x0, y0, x1, y1);
                return new int[]{Math.round(advance.get(0) * scale), x0.get(0), y0.get(0), x1.get(0), y1.get(0)};
            }
        });
    }

    /** Rasterizes {@code glyph} at {@code px} into {@code out}, {@code width} by {@code height}, its {@link #metrics} box. */
    void bake(int glyph, int px, float[] out, int width, int height) {
        float scale = STBTruetype.stbtt_ScaleForPixelHeight(info, px);
        // Up to 256 by 256: more than the thread's MemoryStack holds.
        ByteBuffer bitmap = MemoryUtil.memAlloc(width * height);
        try {
            STBTruetype.stbtt_MakeGlyphBitmap(info, bitmap, width, height, width, scale, scale, glyph);
            for (int i = 0; i < width * height; i++) {
                out[i] = (bitmap.get(i) & 0xFF) / 255f;
            }
        } finally {
            MemoryUtil.memFree(bitmap);
        }
    }

    /** The mask atlas key of {@code glyph} at {@code px}. */
    Object atlasKey(int glyph, int px) {
        return new AtlasKey(this, glyph, px);
    }

    private static long key(int glyph, int px) {
        return (long) glyph << 32 | px;
    }

    private record AtlasKey(Face face, int glyph, int px) {
    }
}
