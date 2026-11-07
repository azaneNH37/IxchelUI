package com.azane.ixchel.sample.arcane;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The arcane tree: rings of arts around the core, each learned for points once its parent is.
 * Nothing leaves the client; the tree starts over every time it opens. An art shows only once its
 * parent is learned: until then it is burnt away, and it burns in when revealed.
 */
@UiView("ixchel_sample:ui/arcane/tree.html")
final class ArcaneModel extends ViewModel {
    /** The stage in tree.css, and a node's box: lines join the centers. */
    static final int STAGE = 260;
    static final int NODE = 22;
    static final int POINTS = 7;

    /** {@code ring} 0 to 2; {@code angle} in degrees, clockwise from up. */
    record Art(String id, Item item, int ring, float angle, String parent, int cost) {
        float x() {
            return STAGE / 2f + RADII[ring] * (float) Math.sin(Math.toRadians(angle)) - NODE / 2f;
        }

        float y() {
            return STAGE / 2f - RADII[ring] * (float) Math.cos(Math.toRadians(angle)) - NODE / 2f;
        }
    }

    private static final int[] RADII = {0, 52, 104};

    static final List<Art> ARTS = List.of(
            new Art("core", Items.NETHER_STAR, 0, 0, null, 0),
            new Art("ember", Items.BLAZE_POWDER, 1, 0, "core", 1),
            new Art("tide", Items.HEART_OF_THE_SEA, 1, 90, "core", 1),
            new Art("stone", Items.AMETHYST_SHARD, 1, 180, "core", 1),
            new Art("gale", Items.FEATHER, 1, 270, "core", 1),
            new Art("inferno", Items.FIRE_CHARGE, 2, -22, "ember", 2),
            new Art("meteor", Items.MAGMA_CREAM, 2, 22, "ember", 2),
            new Art("frost", Items.PACKED_ICE, 2, 68, "tide", 2),
            new Art("mending", Items.GLISTERING_MELON_SLICE, 2, 112, "tide", 2),
            new Art("ward", Items.SHIELD, 2, 158, "stone", 2),
            new Art("golem", Items.IRON_BLOCK, 2, 202, "stone", 2),
            new Art("blink", Items.ENDER_PEARL, 2, 248, "gale", 2),
            new Art("storm", Items.LIGHTNING_ROD, 2, 292, "gale", 2));

    /** {@code state}: learned, open (its parent learned) or sealed. */
    record Node(String id, ItemStack icon, float x, float y, String state) {
    }

    /** From the parent's center to the child's; {@code lit} once the child is learned, {@code shown} once it is revealed. */
    record Edge(String id, float x, float y, float length, float angle, boolean shown, boolean lit) {
    }

    record Detail(String id, ItemStack icon, Component name, Component text, int cost, String state, boolean learnable) {
    }

    final Value<Set<String>> learned = state(Set.of("core"));
    final Value<String> selected = state("");
    final Derived<Integer> points = derive(learned, l -> POINTS - ARTS.stream().filter(a -> l.contains(a.id())).mapToInt(Art::cost).sum());
    final Derived<List<Node>> nodes = derive(learned, ArcaneModel::nodes);
    final Derived<List<Edge>> edges = derive(learned, ArcaneModel::edges);
    final Derived<Detail> detail = derive(selected, learned, points, ArcaneModel::detail);

    final Action<Node> pick = action((Node n) -> selected.set(n.state().equals("sealed") ? "" : n.id()));
    final Action<Void> learn = action(this::learn);

    private void learn() {
        Detail d = detail.get();
        if (d == null || !d.learnable()) {
            return;
        }
        Set<String> next = new HashSet<>(learned.get());
        next.add(d.id());
        learned.set(Set.copyOf(next));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.2f));
    }

    private static Art art(String id) {
        return ARTS.stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }

    private static String state(Art a, Set<String> learned) {
        if (learned.contains(a.id())) {
            return "learned";
        }
        return learned.contains(a.parent()) ? "open" : "sealed";
    }

    private static List<Node> nodes(Set<String> learned) {
        List<Node> out = new ArrayList<>(ARTS.size());
        for (Art a : ARTS) {
            out.add(new Node(a.id(), new ItemStack(a.item()), a.x(), a.y(), state(a, learned)));
        }
        return out;
    }

    private static List<Edge> edges(Set<String> learned) {
        List<Edge> out = new ArrayList<>();
        for (Art a : ARTS) {
            if (a.parent() == null) {
                continue;
            }
            Art from = art(a.parent());
            float dx = a.x() - from.x();
            float dy = a.y() - from.y();
            out.add(new Edge(a.id(), from.x() + NODE / 2f, from.y() + NODE / 2f - 1, (float) Math.hypot(dx, dy),
                    (float) Math.toDegrees(Math.atan2(dy, dx)), learned.contains(a.parent()), learned.contains(a.id())));
        }
        return out;
    }

    private static Detail detail(String id, Set<String> learned, int points) {
        if (id.isEmpty()) {
            return null;
        }
        Art a = art(id);
        String state = state(a, learned);
        return new Detail(a.id(), new ItemStack(a.item()), Component.translatable("ixchel_sample.arcane." + a.id()),
                Component.translatable("ixchel_sample.arcane." + a.id() + ".text"), a.cost(), state,
                state.equals("open") && points >= a.cost());
    }
}
