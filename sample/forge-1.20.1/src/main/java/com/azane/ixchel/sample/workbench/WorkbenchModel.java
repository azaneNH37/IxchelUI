package com.azane.ixchel.sample.workbench;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import com.azane.ixchel.sample.network.Network;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The workbench: the catalog's recipes filtered by category and search, the selected one's
 * materials against the inventory times the craft count, and the craft button's three phases. A
 * craft sends {@link CraftPacket} at once, then plays its assembly ({@value #BUSY_MS} ms) and a
 * flash ({@value #DONE_MS} ms) on the game's clock while the inventory sync brings the result.
 */
@UiView("ixchel_sample:ui/workbench/bench.html")
final class WorkbenchModel extends ViewModel {
    static final long BUSY_MS = 700;
    static final long DONE_MS = 700;

    /** A catalog recipe: its result, the ingredient groups it takes once, and its category. */
    private record Recipe(String id, CraftingRecipe recipe, ItemStack result, String category, List<Group> groups) {
    }

    /** Equal ingredients of one recipe merged, with how many one craft takes. */
    private record Group(Ingredient ingredient, List<ItemStack> options, int need) {
    }

    record Row(String id, ItemStack result, Component name, boolean craftable) {
    }

    record Material(int index, List<ItemStack> options, Component name, int need, int have, boolean enough) {
    }

    record Current(String id, ItemStack result, Component name, int yield, int max) {
    }

    private final List<Recipe> recipes = load();

    final Value<String> query = state("");
    final Value<String> category = state("all");
    final Value<String> selected = state(recipes.isEmpty() ? "" : recipes.get(0).id());
    final Value<Integer> times = state(1);
    private final Cell<List<ItemStack>> inventory = source(ClientSources.inventory());
    private final Cell<Long> now = source(ClientSources.polled(Util::getMillis));
    private final Value<Long> craftedAt = state(Long.MIN_VALUE / 2);

    final Derived<List<Row>> shown = derive(query, category, inventory, (q, c, inv) -> {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        List<Row> rows = new ArrayList<>();
        for (Recipe r : recipes) {
            Component name = r.result().getHoverName();
            if ((c.equals("all") || c.equals(r.category()))
                    && (needle.isEmpty() || name.getString().toLowerCase(Locale.ROOT).contains(needle))) {
                rows.add(new Row(r.id(), r.result(), name, maxTimes(r, inv) > 0));
            }
        }
        return rows;
    });

    final Derived<Current> current = derive(selected, inventory, (id, inv) -> {
        Recipe r = find(id);
        return r == null ? new Current("", ItemStack.EMPTY, Component.empty(), 0, 0)
                : new Current(r.id(), r.result(), r.result().getHoverName(), r.result().getCount(), maxTimes(r, inv));
    });

    final Derived<List<Material>> materials = derive(selected, times, inventory, (id, n, inv) -> {
        Recipe r = find(id);
        List<Material> list = new ArrayList<>();
        if (r != null) {
            for (int i = 0; i < r.groups().size(); i++) {
                Group g = r.groups().get(i);
                int have = count(g.ingredient(), inv);
                int need = g.need() * Math.max(1, n);
                list.add(new Material(i, g.options(), g.options().get(0).getHoverName(), need, have, have >= need));
            }
        }
        return list;
    });

    /** idle, busy (assembling) or done (flash). */
    final Derived<String> phase = derive(now, craftedAt, (t, at) ->
            t - at < BUSY_MS ? "busy" : t - at < BUSY_MS + DONE_MS ? "done" : "idle");

    /** Assembly progress 0–100 for the stage's charge bar. */
    final Derived<Integer> progress = derive(now, craftedAt, (t, at) ->
            t - at < BUSY_MS ? (int) ((t - at) * 100 / BUSY_MS) : t - at < BUSY_MS + DONE_MS ? 100 : 0);

    final Derived<Boolean> canCraft = derive(materials, phase, (mats, p) ->
            !mats.isEmpty() && p.equals("idle") && mats.stream().allMatch(Material::enough));

    final Action<Void> craft = action(() -> {
        Recipe r = find(selected.get());
        if (r != null && canCraft.get()) {
            Network.sendToServer(new CraftPacket(r.recipe().getId(), times.get()));
            craftedAt.set(Util.getMillis());
        }
    });

    private Recipe find(String id) {
        for (Recipe r : recipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    private static int maxTimes(Recipe r, List<ItemStack> inv) {
        int max = CraftPacket.MAX_TIMES;
        for (Group g : r.groups()) {
            max = Math.min(max, count(g.ingredient(), inv) / g.need());
        }
        return max;
    }

    private static int count(Ingredient in, List<ItemStack> inv) {
        int n = 0;
        for (ItemStack s : inv) {
            if (!s.isEmpty() && in.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static List<Recipe> load() {
        Minecraft mc = Minecraft.getInstance();
        List<CraftingRecipe> all = mc.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        List<Recipe> out = new ArrayList<>();
        for (Workbench.Entry e : Workbench.CATALOG) {
            for (CraftingRecipe c : all) {
                ItemStack result = c.getResultItem(mc.level.registryAccess());
                if (result.is(e.result())) {
                    out.add(new Recipe(c.getId().toString(), c, result.copy(), e.category(), groups(c)));
                    break;
                }
            }
        }
        return out;
    }

    private static List<Group> groups(CraftingRecipe c) {
        List<Group> groups = new ArrayList<>();
        for (Ingredient in : c.getIngredients()) {
            if (in.isEmpty()) {
                continue;
            }
            List<ItemStack> options = Arrays.stream(in.getItems()).map(ItemStack::copy).toList();
            int at = -1;
            for (int i = 0; i < groups.size() && at < 0; i++) {
                if (sameOptions(groups.get(i).options(), options)) {
                    at = i;
                }
            }
            if (at < 0) {
                groups.add(new Group(in, options, 1));
            } else {
                Group g = groups.get(at);
                groups.set(at, new Group(g.ingredient(), g.options(), g.need() + 1));
            }
        }
        return groups;
    }

    private static boolean sameOptions(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).getItem() != b.get(i).getItem()) {
                return false;
            }
        }
        return true;
    }
}
