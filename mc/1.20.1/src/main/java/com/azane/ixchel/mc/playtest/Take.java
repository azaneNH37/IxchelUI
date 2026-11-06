package com.azane.ixchel.mc.playtest;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One open take: each captured frame copies the window's frame, draws the {@link RecordingOverlay} on
 * the copy, reads the take's region back (scaled down on the GPU first when the output is smaller)
 * and hands it to ffmpeg. The window must keep its size during a take. Client thread only.
 */
final class Take {
    private final String name;
    private final Path mp4;
    private final Path gif;
    private final Path log;
    private final String ffmpeg;
    private final int windowWidth;
    private final int windowHeight;
    /** The region in window pixels, {@code {x, y, width, height}} from the top left. */
    private final int[] pixels;
    private final int outputWidth;
    private final int outputHeight;
    private final FfmpegEncoder encoder;
    private final TextureTarget copy;
    /** The region scaled to the output size, or {@code null} when the region is read as it is. */
    private final TextureTarget scaled;
    private final ByteBuffer readback;
    private Exception failure;
    private int frames;

    private Take(String name, Path dir, boolean gif, Recorder.Settings settings, int[] pixels) throws IOException {
        Window window = Minecraft.getInstance().getWindow();
        this.name = name;
        this.mp4 = dir.resolve(name + ".mp4");
        this.gif = gif ? dir.resolve(name + ".gif") : null;
        this.log = dir.resolve(name + ".ffmpeg.log");
        this.ffmpeg = settings.ffmpeg();
        this.windowWidth = window.getWidth();
        this.windowHeight = window.getHeight();
        this.pixels = pixels;
        int[] output = Framing.output(pixels[2], pixels[3], settings.scale());
        this.outputWidth = output[0];
        this.outputHeight = output[1];
        Files.createDirectories(dir);
        this.encoder = FfmpegEncoder.start(settings, outputWidth, outputHeight, mp4, log);
        this.copy = new TextureTarget(windowWidth, windowHeight, false, Minecraft.ON_OSX);
        this.scaled = outputWidth == pixels[2] && outputHeight == pixels[3] ? null
                : new TextureTarget(outputWidth, outputHeight, false, Minecraft.ON_OSX);
        this.readback = MemoryUtil.memAlloc(outputWidth * outputHeight * 4);
    }

    /**
     * Starts a take of the GUI rectangle {@code region} ({@code null}: the whole window) written to
     * {@code <dir>/<name>.mp4}.
     */
    static Take open(String name, Path dir, float[] region, boolean gif, Recorder.Settings settings) {
        Window window = Minecraft.getInstance().getWindow();
        int[] pixels = Framing.pixels(region, window.getGuiScale(), window.getWidth(), window.getHeight());
        try {
            return new Take(name, dir, gif, settings, pixels);
        } catch (IOException e) {
            throw new IllegalStateException("take " + name + " cannot start: " + e.getMessage(), e);
        }
    }

    String name() {
        return name;
    }

    /** Captures the frame just drawn, with {@code overlay} drawn over it; a failure ends the take's capturing and is reported by {@link #finish}. */
    void capture(RecordingOverlay overlay, long now, float[] pointer) {
        if (failure != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int viewX = GlStateManager.Viewport.x();
        int viewY = GlStateManager.Viewport.y();
        int viewWidth = GlStateManager.Viewport.width();
        int viewHeight = GlStateManager.Viewport.height();
        try {
            if (main.width != windowWidth || main.height != windowHeight) {
                throw new IllegalStateException("the window changed size during the take");
            }
            blit(main.frameBufferId, 0, 0, windowWidth, windowHeight, copy.frameBufferId, 0, 0, windowWidth, windowHeight, GL11.GL_NEAREST);
            copy.bindWrite(true);
            Window window = mc.getWindow();
            overlay.draw(window.getGuiScaledWidth(), window.getGuiScaledHeight(), window.getGuiScale(), now, pointer[0], pointer[1],
                    ScreenInput.openDocument());
            // OpenGL counts rows from the bottom.
            int bottom = windowHeight - pixels[1] - pixels[3];
            int read = copy.frameBufferId;
            int readX = pixels[0];
            int readY = bottom;
            if (scaled != null) {
                blit(copy.frameBufferId, pixels[0], bottom, pixels[0] + pixels[2], bottom + pixels[3],
                        scaled.frameBufferId, 0, 0, outputWidth, outputHeight, GL11.GL_LINEAR);
                read = scaled.frameBufferId;
                readX = 0;
                readY = 0;
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            readback.clear();
            GlStateManager._readPixels(readX, readY, outputWidth, outputHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readback);
            byte[] frame = encoder.acquire();
            readback.get(0, frame);
            encoder.submit(frame);
            frames++;
        } catch (IOException | RuntimeException e) {
            failure = e;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
            RenderSystem.viewport(viewX, viewY, viewWidth, viewHeight);
        }
    }

    private static void blit(int from, int x0, int y0, int x1, int y1, int to, int toX0, int toY0, int toX1, int toY1, int filter) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to);
        GlStateManager._glBlitFrameBuffer(x0, y0, x1, y1, toX0, toY0, toX1, toY1, GL11.GL_COLOR_BUFFER_BIT, filter);
    }

    /**
     * Finishes the video (and the GIF) and frees the take's targets; a summary line.
     *
     * @throws IllegalStateException when a frame could not be captured or ffmpeg failed
     */
    String finish() {
        free();
        if (failure != null) {
            encoder.kill();
            throw new IllegalStateException("take " + name + " failed after " + frames + " frames: " + failure.getMessage(), failure);
        }
        try {
            encoder.finish();
            if (gif != null) {
                FfmpegEncoder.gif(ffmpeg, mp4, gif, log);
            }
        } catch (IOException e) {
            throw new IllegalStateException("take " + name + ": " + e.getMessage(), e);
        }
        return name + ": " + frames + " frames, " + outputWidth + "x" + outputHeight + (gif != null ? ", with a GIF" : "");
    }

    /** Stops without finishing the video, as when the run ends inside the take. */
    void abort() {
        free();
        encoder.kill();
    }

    private void free() {
        copy.destroyBuffers();
        if (scaled != null) {
            scaled.destroyBuffers();
        }
        MemoryUtil.memFree(readback);
    }
}
