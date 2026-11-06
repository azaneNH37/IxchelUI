package com.azane.ixchel.mc.playtest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An ffmpeg process making an H.264 MP4 from raw RGBA frames written to its stdin, bottom row first as
 * OpenGL reads them, by a writer thread of its own. Frames travel in a small pool of buffers: when
 * ffmpeg falls behind, {@link #acquire} waits for one to come back, which only slows the take down,
 * since its time is stepped per frame. x264 encodes the same frames to the same file.
 */
final class FfmpegEncoder {
    private static final byte[] END = new byte[0];
    private static final int POOL = 3;

    private final Process process;
    private final Path log;
    private final BlockingQueue<byte[]> free = new ArrayBlockingQueue<>(POOL);
    private final BlockingQueue<byte[]> work = new LinkedBlockingQueue<>();
    private volatile IOException failure;
    private boolean closed;

    private FfmpegEncoder(Process process, Path log, int frameBytes) {
        this.process = process;
        this.log = log;
        for (int i = 0; i < POOL; i++) {
            free.add(new byte[frameBytes]);
        }
        Thread writer = new Thread(() -> write(process.getOutputStream()), "ixchel-recording-encoder");
        writer.setDaemon(true);
        writer.start();
    }

    /**
     * Starts encoding {@code width × height} frames at the settings' frame rate into {@code mp4};
     * ffmpeg's messages go to {@code log}.
     */
    static FfmpegEncoder start(Recorder.Settings settings, int width, int height, Path mp4, Path log) throws IOException {
        String ffmpeg = settings.ffmpeg();
        int fps = settings.fps();
        Process process = launch(ffmpeg, List.of(ffmpeg, "-hide_banner", "-loglevel", "warning", "-y",
                "-f", "rawvideo", "-pix_fmt", "rgba", "-video_size", width + "x" + height, "-framerate", String.valueOf(fps),
                "-i", "pipe:0",
                "-vf", "vflip,scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", String.valueOf(settings.crf()),
                "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", "-color_range", "tv",
                "-g", String.valueOf(fps * 2), "-movflags", "+faststart", mp4.toString()), log);
        return new FfmpegEncoder(process, log, width * height * 4);
    }

    /** Makes {@code gif} from {@code mp4} at 25 fps (GIF delays are whole hundredths of a second) with one palette for the clip. */
    static void gif(String ffmpeg, Path mp4, Path gif, Path log) throws IOException {
        Process process = launch(ffmpeg, List.of(ffmpeg, "-hide_banner", "-loglevel", "warning", "-y", "-i", mp4.toString(),
                "-vf", "fps=25,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=sierra2_4a",
                gif.toString()), log);
        process.getOutputStream().close();
        awaitExit(process, log, 120);
    }

    private static Process launch(String ffmpeg, List<String> command, Path log) throws IOException {
        try {
            return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        } catch (IOException e) {
            throw new IOException("cannot start ffmpeg '" + ffmpeg + "'; put it on PATH or set -Dixchel.recording.ffmpeg", e);
        }
    }

    private void write(OutputStream stdin) {
        try {
            while (true) {
                byte[] frame = work.take();
                if (frame == END) {
                    break;
                }
                if (failure == null) {
                    try {
                        stdin.write(frame);
                    } catch (IOException e) {
                        failure = e;
                    }
                }
                free.add(frame);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            stdin.close();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            }
        }
    }

    /** A buffer for the next frame; waits while ffmpeg is behind. */
    byte[] acquire() throws IOException {
        try {
            while (true) {
                if (failure != null) {
                    throw new IOException("ffmpeg stopped taking frames, see " + log, failure);
                }
                if (!process.isAlive()) {
                    throw new IOException("ffmpeg exited with " + process.exitValue() + " during the take, see " + log);
                }
                byte[] frame = free.poll(100, TimeUnit.MILLISECONDS);
                if (frame != null) {
                    return frame;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted waiting for ffmpeg", e);
        }
    }

    /** Queues a frame {@link #acquire}d and filled. */
    void submit(byte[] frame) {
        work.add(frame);
    }

    /** Ends the video and waits for ffmpeg to finish the file. */
    void finish() throws IOException {
        close();
        awaitExit(process, log, 120);
        if (failure != null) {
            throw new IOException("ffmpeg stopped taking frames, see " + log, failure);
        }
    }

    /** Stops ffmpeg without finishing the file. */
    void kill() {
        close();
        process.destroyForcibly();
    }

    private void close() {
        if (!closed) {
            closed = true;
            work.add(END);
        }
    }

    private static void awaitExit(Process process, Path log, int seconds) throws IOException {
        try {
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("ffmpeg did not finish within " + seconds + " s, see " + log);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("interrupted waiting for ffmpeg", e);
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffmpeg exited with " + process.exitValue() + ", see " + log);
        }
        Files.deleteIfExists(log);
    }
}
