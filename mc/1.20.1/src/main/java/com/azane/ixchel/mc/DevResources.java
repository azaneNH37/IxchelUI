package com.azane.ixchel.mc;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Development hot reload: UI files are read straight from the host's source tree instead of the
 * packed resources, and a change to any of them reloads on the client thread. Enabled per host by
 * the system property {@code ixchel.devResources.<modid>}, the host's resource root (the directory
 * holding {@code assets/}). Images still come from the packed resources.
 */
public final class DevResources {
    private final Path assets;

    private DevResources(Path assets) {
        this.assets = assets;
    }

    /** The dev source for {@code hostModId}, or {@code null} when its property is not set. */
    public static DevResources forHost(String hostModId) {
        String root = System.getProperty("ixchel.devResources." + hostModId);
        return root == null ? null : new DevResources(Path.of(root, "assets", hostModId));
    }

    String read(String path) {
        try {
            return Files.readString(assets.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + assets.resolve(path), e);
        }
    }

    /**
     * Watches {@code paths} (relative to {@code assets/<modid>/}) on a daemon thread and runs
     * {@code onChange} on the client thread after they change; a burst of events from one save
     * runs it once.
     */
    public void watch(List<String> paths, Runnable onChange) {
        Set<Path> files = new HashSet<>();
        Set<Path> dirs = new HashSet<>();
        for (String p : paths) {
            Path f = assets.resolve(p).toAbsolutePath().normalize();
            files.add(f);
            dirs.add(f.getParent());
        }
        WatchService service;
        try {
            service = FileSystems.getDefault().newWatchService();
            for (Path d : dirs) {
                d.register(service, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot watch " + dirs, e);
        }
        AtomicBoolean queued = new AtomicBoolean();
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    WatchKey key = service.take();
                    Path dir = (Path) key.watchable();
                    boolean relevant = false;
                    for (WatchEvent<?> e : key.pollEvents()) {
                        if (e.context() instanceof Path name && files.contains(dir.resolve(name))) {
                            relevant = true;
                        }
                    }
                    key.reset();
                    if (relevant && queued.compareAndSet(false, true)) {
                        Minecraft.getInstance().execute(() -> {
                            queued.set(false);
                            onChange.run();
                        });
                    }
                }
            } catch (InterruptedException | ClosedWatchServiceException e) {
                // watcher stopped
            }
        }, "ixchel-dev-reload");
        t.setDaemon(true);
        t.start();
    }
}
