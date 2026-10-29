package com.azane.ixchel.sample.quests;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The quest graph, read from {@code data/ixchelsample/quests.json} in the mod jar, so the client that
 * draws it and the server that checks a claim read the same file. A quest asks the player to hold
 * {@code count} of {@code item} once every quest in {@code deps} is claimed, and gives
 * {@code rewardCount} of {@code reward} when claimed.
 */
public final class Quests {
    /** One node; {@code x}, {@code y}: its top-left on the chapter's canvas, in GUI pixels. */
    public record Quest(String id, String title, int x, int y, List<String> deps, Item item, int count,
                        Item reward, int rewardCount) {
    }

    public record Chapter(String id, String title, Item icon, List<Quest> quests) {
    }

    private static List<Chapter> chapters;
    private static Map<String, Quest> byId;

    private Quests() {
    }

    /** In file order; read on first use, after the item registry is filled. */
    public static List<Chapter> chapters() {
        if (chapters == null) {
            load();
        }
        return chapters;
    }

    /** The quest {@code id} names, or {@code null}. */
    public static Quest byId(String id) {
        chapters();
        return byId.get(id);
    }

    private static void load() {
        JsonObject root;
        try (InputStream in = Quests.class.getResourceAsStream("/data/ixchelsample/quests.json")) {
            if (in == null) {
                throw new IllegalStateException("data/ixchelsample/quests.json is missing from the jar");
            }
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Chapter> out = new ArrayList<>();
        Map<String, Quest> index = new HashMap<>();
        for (JsonElement c : root.getAsJsonArray("chapters")) {
            JsonObject chapter = c.getAsJsonObject();
            List<Quest> quests = new ArrayList<>();
            for (JsonElement q : chapter.getAsJsonArray("quests")) {
                JsonObject o = q.getAsJsonObject();
                List<String> deps = new ArrayList<>();
                o.getAsJsonArray("deps").forEach(d -> deps.add(d.getAsString()));
                Quest quest = new Quest(o.get("id").getAsString(), o.get("title").getAsString(), o.get("x").getAsInt(),
                        o.get("y").getAsInt(), List.copyOf(deps), item(o.get("item").getAsString()), o.get("count").getAsInt(),
                        item(o.get("reward").getAsString()), o.get("rewardCount").getAsInt());
                quests.add(quest);
                index.put(quest.id(), quest);
            }
            out.add(new Chapter(chapter.get("id").getAsString(), chapter.get("title").getAsString(),
                    item(chapter.get("icon").getAsString()), List.copyOf(quests)));
        }
        byId = index;
        chapters = List.copyOf(out);
    }

    private static Item item(String id) {
        ResourceLocation key = new ResourceLocation(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            throw new IllegalStateException("quests.json names an unknown item " + id);
        }
        return BuiltInRegistries.ITEM.get(key);
    }
}
