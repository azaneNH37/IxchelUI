package com.azane.ixchel.mc;

import com.azane.ixchel.core.api.HostApi;
import com.azane.ixchel.core.runtime.UiHosting;
import com.azane.ixchel.core.runtime.UiRegistry;
import com.azane.ixchel.mc.element.McElements;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registries the documents of this layer see: {@link UiRegistry#CORE}, {@link #MC} and the one
 * their host mod {@linkplain #register registered}. Hosts stay apart, so two may use the same tag.
 *
 * <pre>{@code
 * UiRegistries.register(UiRegistry.host("techmod")
 *         .element(new GaugeElement())
 *         .component(EnergyModel.class, EnergyModel::new));
 * }</pre>
 */
@HostApi
public final class UiRegistries {
    /**
     * This layer's: {@code @ixchel/mc/x} is {@code ui/x} next to this class;
     * {@code <mc-item bind:item>}
     * and the components {@code <mc-tooltip-lines>}. Its initialization touches
     * no game state, so tools can read it.
     */
    public static final UiRegistry MC = McElements.into(UiRegistry.library().root("mc/", UiRegistries.class, "ui/"));

    /** Registered during mod setup, which runs on worker threads. */
    private static final Map<String, UiRegistry> HOSTS = new ConcurrentHashMap<>();
    private static final Map<String, UiHosting> HOSTINGS = new ConcurrentHashMap<>();

    private UiRegistries() {
    }

    /**
     * Adds {@code host} to the documents of its mod. Call it once during mod setup, before the
     * host's first document loads.
     *
     * @throws IllegalArgumentException if {@code host} is a library's or its mod already registered one
     */
    public static void register(UiRegistry host) {
        if (host.host() == null) {
            throw new IllegalArgumentException("only a host registry is registered; libraries are built in");
        }
        if (HOSTS.putIfAbsent(host.host(), host) != null) {
            throw new IllegalArgumentException(host.host() + " already registered its registry");
        }
    }

    /**
     * What a document of {@code hostModId} sees, built on first use.
     *
     * @throws IllegalArgumentException if the host's registry conflicts with the core's or this layer's
     */
    public static UiHosting hosting(String hostModId) {
        return HOSTINGS.computeIfAbsent(hostModId, id -> {
            UiRegistry host = HOSTS.get(id);
            return host == null ? UiHosting.of(UiRegistry.CORE, MC) : UiHosting.of(UiRegistry.CORE, MC, host);
        });
    }
}
