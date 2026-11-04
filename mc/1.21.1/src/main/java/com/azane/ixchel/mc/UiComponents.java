package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.library.JavaComponent;
import com.azane.ixchel.core.runtime.UiHosting;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The Java components of each host: a component file whose view model class declares it with
 * {@link UiTemplate} and is registered here gets a new model for every instance.
 *
 * <pre>{@code
 * @UiTemplate("techmod:ui/common/energy.html")
 * final class EnergyView extends ViewModel { ... }
 *
 * UiComponents.register("techmod", EnergyView.class, EnergyView::new);
 * }</pre>
 */
public final class UiComponents {
    /** Registered during mod setup, which runs on worker threads. */
    private static final Map<String, Map<String, JavaComponent>> HOSTS = new ConcurrentHashMap<>();

    private UiComponents() {
    }

    /**
     * Makes the component file {@code type} declares a Java component in the documents of
     * {@code hostModId}. Call it during mod setup, before the host's first document loads.
     *
     * @param factory builds the model of each instance, on the render thread
     * @throws IllegalArgumentException if {@code type} does not declare exactly one concrete template
     *                                  with {@link UiTemplate}, or that file is already registered
     */
    public static void register(String hostModId, Class<? extends ViewModel> type, Supplier<? extends ViewModel> factory) {
        String id = UiTemplate.Ids.single(type);
        JavaComponent previous = HOSTS.computeIfAbsent(hostModId, k -> new ConcurrentHashMap<>())
                .putIfAbsent(id, new JavaComponent(type, factory));
        if (previous != null) {
            throw new IllegalArgumentException(id + " is already registered with " + previous.type().getName());
        }
    }

    /** What a document of {@code hostModId} gets: {@link McLibrary#LIBRARY} and the Java components the host registered. */
    static UiHosting hosting(String hostModId) {
        return new UiHosting(McLibrary.LIBRARY, Map.copyOf(HOSTS.getOrDefault(hostModId, Map.of())));
    }
}
