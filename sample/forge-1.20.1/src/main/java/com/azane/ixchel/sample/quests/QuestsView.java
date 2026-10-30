package com.azane.ixchel.sample.quests;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.EventInfo;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.network.Replica;
import com.azane.ixchel.sample.network.Replication;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The quest tree: a chapter's quests on a canvas the template pans (drag) and zooms (wheel) with a
 * {@code transform}, the lines between them as rotated thin boxes, and a detail panel over the canvas
 * for the quest clicked. A quest is locked until its prerequisites are claimed, ready once the player
 * holds what it asks for, done once claimed; the server checks a claim.
 */
final class QuestsView extends ViewModel {
    /** The size of {@code .view} in quests.css: zooming keeps its center still. */
    static final float VIEW_WIDTH = 240;
    static final float VIEW_HEIGHT = 200;
    static final float MIN_ZOOM = 0.5f;
    static final float MAX_ZOOM = 2;
    /** The size of {@code .node} in quests.css: lines join node centers. */
    private static final int NODE = 26;
    /** A press that moved further than this, in GUI pixels, was a drag and opens nothing. */
    private static final float CLICK_SLOP = 3;

    record ChapterRow(String id, String title, ItemStack icon) {
    }

    /** {@code state}: {@code locked}, {@code open}, {@code ready} or {@code done}. */
    record Node(String id, int x, int y, ItemStack icon, String state, String tooltip) {
    }

    /** A line from a prerequisite's center: {@code angle} in degrees, clockwise from the x axis. */
    record Edge(String id, float x, float y, float length, float angle, boolean done) {
    }

    record Detail(String title, ItemStack icon, String task, String reward, String state, boolean ready) {
    }

    private final Map<String, ItemStack> icons = new HashMap<>();
    private float travel;

    final Value<List<ChapterRow>> chapters;
    final Value<String> chapter;
    final Value<String> selected = state("");
    final Value<Float> panX = state(0f);
    final Value<Float> panY = state(0f);
    final Value<Float> zoom = state(1f);
    final Cell<Claimed> claimed;
    /** How many of each quest item the player holds. */
    final Cell<Map<Item, Integer>> held;
    final Derived<List<Node>> nodes;
    final Derived<List<Edge>> edges;
    /** The selected quest, {@code null} when none. */
    final Derived<Detail> detail;

    final Action<Void> press = action(() -> travel = 0);
    final Action<EventInfo> drag = action((EventInfo e) -> {
        travel += Math.abs(e.movementX()) + Math.abs(e.movementY());
        panX.set(round(panX.get() + e.movementX()));
        panY.set(round(panY.get() + e.movementY()));
    });
    final Action<EventInfo> wheel = action((EventInfo e) -> zoomBy(e.delta() > 0 ? 1.25f : 0.8f));
    final Action<Node> open = action((Node n) -> {
        if (travel <= CLICK_SLOP) {
            selected.set(n.id());
        }
    });
    final Action<Void> claim = action(() -> Network.sendToServer(new ClaimQuestPacket(selected.get())));

    QuestsView() {
        List<ChapterRow> rows = new ArrayList<>();
        Set<Item> items = new LinkedHashSet<>();
        for (Quests.Chapter c : Quests.chapters()) {
            rows.add(new ChapterRow(c.id(), c.title(), new ItemStack(c.icon())));
            for (Quests.Quest q : c.quests()) {
                icons.put(q.id(), new ItemStack(q.item()));
                items.add(q.item());
            }
        }
        chapters = state(List.copyOf(rows));
        chapter = state(rows.get(0).id());
        Replica<Claimed> replica = Replication.watch(Network.QUESTS, 0);
        scope().own(replica);
        claimed = source(Source.polled(() -> replica.get() == null ? Claimed.NONE : replica.get()));
        held = source(ClientSources.player(p -> {
            Map<Item, Integer> out = new HashMap<>();
            for (Item item : items) {
                out.put(item, p.getInventory().countItem(item));
            }
            return out;
        }, Map.of()));
        nodes = derive(chapter, claimed, held, this::nodes);
        edges = derive(chapter, claimed, QuestsView::edges);
        detail = derive(selected, claimed, held, this::detail);
    }

    private void zoomBy(float factor) {
        float z = zoom.get();
        float next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z * factor));
        float k = next / z;
        panX.set(round(VIEW_WIDTH / 2 - (VIEW_WIDTH / 2 - panX.get()) * k));
        panY.set(round(VIEW_HEIGHT / 2 - (VIEW_HEIGHT / 2 - panY.get()) * k));
        zoom.set(next);
    }

    /** To 1/100 px: the value goes into a CSS string, which takes no exponent form. */
    private static float round(float v) {
        return Math.round(v * 100) / 100f;
    }

    private static Quests.Chapter chapterOf(String id) {
        return Quests.chapters().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static String state(Quests.Quest q, Claimed claimed, Map<Item, Integer> held) {
        if (claimed.has(q.id())) {
            return "done";
        }
        if (!q.deps().stream().allMatch(claimed::has)) {
            return "locked";
        }
        return held.getOrDefault(q.item(), 0) >= q.count() ? "ready" : "open";
    }

    private List<Node> nodes(String chapter, Claimed claimed, Map<Item, Integer> held) {
        List<Node> out = new ArrayList<>();
        for (Quests.Quest q : chapterOf(chapter).quests()) {
            String state = state(q, claimed, held);
            out.add(new Node(q.id(), q.x(), q.y(), icons.get(q.id()), state, q.title() + "\n" + task(q, held)
                    + "\n" + label(state)));
        }
        return out;
    }

    private static List<Edge> edges(String chapter, Claimed claimed) {
        List<Edge> out = new ArrayList<>();
        for (Quests.Quest q : chapterOf(chapter).quests()) {
            for (String dep : q.deps()) {
                Quests.Quest from = Quests.byId(dep);
                float dx = q.x() - from.x();
                float dy = q.y() - from.y();
                out.add(new Edge(dep + ">" + q.id(), from.x() + NODE / 2f, from.y() + NODE / 2f - 1,
                        (float) Math.hypot(dx, dy), (float) Math.toDegrees(Math.atan2(dy, dx)), claimed.has(dep)));
            }
        }
        return out;
    }

    private Detail detail(String id, Claimed claimed, Map<Item, Integer> held) {
        Quests.Quest q = id.isEmpty() ? null : Quests.byId(id);
        if (q == null) {
            return null;
        }
        String state = state(q, claimed, held);
        return new Detail(q.title(), icons.get(q.id()), task(q, held),
                "Reward: " + q.rewardCount() + " × " + new ItemStack(q.reward()).getHoverName().getString(),
                label(state), state.equals("ready"));
    }

    private static String task(Quests.Quest q, Map<Item, Integer> held) {
        return "Hold " + q.count() + " × " + new ItemStack(q.item()).getHoverName().getString()
                + " (" + Math.min(q.count(), held.getOrDefault(q.item(), 0)) + "/" + q.count() + ")";
    }

    private static String label(String state) {
        return switch (state) {
            case "done" -> "Claimed";
            case "ready" -> "Ready to claim";
            case "open" -> "In progress";
            default -> "Locked: finish the quests before it";
        };
    }
}
