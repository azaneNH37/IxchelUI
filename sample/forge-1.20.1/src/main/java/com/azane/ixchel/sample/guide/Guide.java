package com.azane.ixchel.sample.guide;

import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Guidebook sample (index only): a two-page book whose entries are the pages under
 * {@code ui/guide/entries/}, a contents spread listing them, links between entries with a Back
 * history, and page turns. Each place opens its own screen and view model; the history lives here,
 * for the client session.
 */
public final class Guide {
    static final String ENTRIES = "ui/guide/entries";
    private static final ResourceLocation CONTENTS = new ResourceLocation(IxchelSampleMod.MOD_ID, "ui/guide/contents.html");
    private static final Deque<Place> history = new ArrayDeque<>();

    /** A spread of the contents ({@code entry} {@code null}) or of an entry. */
    record Place(String entry, int spread) {
    }

    private Guide() {
    }

    /** Opens the contents with an empty history. */
    public static void open() {
        history.clear();
        show(new Place(null, 0));
    }

    /** Leaves {@code from} for the first spread of {@code entry}; Back returns to {@code from}. */
    static void follow(Place from, String entry) {
        history.push(from);
        show(new Place(entry, 0));
    }

    static boolean canGoBack() {
        return !history.isEmpty();
    }

    static void back() {
        if (!history.isEmpty()) {
            show(history.pop());
        }
    }

    /** Entry ids, from the resource listing, sorted; the contents lists them. */
    static List<String> entries() {
        String prefix = ENTRIES + "/";
        return Minecraft.getInstance().getResourceManager()
                .listResources(ENTRIES, rl -> rl.getNamespace().equals(IxchelSampleMod.MOD_ID) && rl.getPath().endsWith(".html"))
                .keySet().stream()
                .map(rl -> rl.getPath().substring(prefix.length(), rl.getPath().length() - ".html".length()))
                .sorted()
                .toList();
    }

    private static void show(Place place) {
        ResourceLocation page = place.entry() == null ? CONTENTS
                : new ResourceLocation(IxchelSampleMod.MOD_ID, ENTRIES + "/" + place.entry() + ".html");
        GuideModel view = new GuideModel(place);
        Minecraft.getInstance().setScreen(new GuideScreen(page, view));
    }
}
