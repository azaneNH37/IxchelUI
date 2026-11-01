package com.azane.ixchel.sample.guide;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * One spread of the book at a time; links, Back and Contents go through {@link Guide}. Templates name
 * the items they draw through {@link #icons} and {@link #recipes}: an {@code <mc-item>} takes its stack
 * only from a binding. A recipe cell with several accepted items shows the next one every second.
 */
@UiTemplate({"ixchel_sample:ui/guide/contents.html", "ixchel_sample:ui/guide/entries/*.html"})
final class GuideView extends ViewModel {
    private static final long ROTATION_NANOS = 1_000_000_000L;

    record Entry(String id, String title) {
    }

    record Slot(int index, ItemStack stack) {
    }

    record Icons(ItemStack redstone, ItemStack torch, ItemStack repeater, ItemStack lamp, ItemStack piston,
                 ItemStack chest, ItemStack planks, ItemStack book) {
    }

    /** The 3×3 grids, each cell showing one of the items its ingredient accepts. */
    record Recipes(List<Slot> piston, List<Slot> chest) {
    }

    private final Guide.Place place;
    private final List<ItemStack[]> pistonCells = cells("piston");
    private final List<ItemStack[]> chestCells = cells("chest");

    final Value<List<Entry>> entries = state(Guide.entries().stream().map(id -> new Entry(id, title(id))).toList());
    final Value<Icons> icons = state(new Icons(new ItemStack(Items.REDSTONE), new ItemStack(Items.REDSTONE_TORCH),
            new ItemStack(Items.REPEATER), new ItemStack(Items.REDSTONE_LAMP), new ItemStack(Items.PISTON),
            new ItemStack(Items.CHEST), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.WRITABLE_BOOK)));
    final Cell<Long> turn = source(Source.polled(() -> System.nanoTime() / ROTATION_NANOS));
    final Derived<Recipes> recipes = derive(turn, t -> new Recipes(grid(pistonCells, t), grid(chestCells, t)));

    /** Set by {@link GuideScreen} from the document. */
    final Value<Integer> pages = state(0);
    final Derived<Integer> spreads = derive(pages, p -> Math.max(1, (p + 1) / 2));
    final Value<Integer> spread;
    final Value<Boolean> history = state(Guide.canGoBack());

    final Action<Void> prev = action(() -> turnBy(-1));
    final Action<Void> next = action(() -> turnBy(1));
    final Action<Void> back = action(Guide::back);
    final Action<Void> contents = action(this::contents);
    final Action<String> open = action(id -> Guide.follow(here(), id));

    GuideView(Guide.Place place) {
        this.place = place;
        this.spread = state(place.spread());
    }

    private void turnBy(int delta) {
        spread.set(Math.max(0, Math.min(spreads.get() - 1, spread.get() + delta)));
    }

    private void contents() {
        if (place.entry() == null) {
            spread.set(0);
        } else {
            Guide.follow(here(), null);
        }
    }

    private Guide.Place here() {
        return new Guide.Place(place.entry(), spread.get());
    }

    /** {@code redstone_basics} reads "Redstone basics". */
    private static String title(String id) {
        String words = id.replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** The nine cells of the vanilla shaped recipe {@code id}, each the items its ingredient accepts. */
    private static List<ItemStack[]> cells(String id) {
        Recipe<?> recipe = Minecraft.getInstance().getConnection().getRecipeManager()
                .byKey(new ResourceLocation(id)).orElseThrow(() -> new IllegalStateException("no recipe " + id));
        ShapedRecipe shaped = (ShapedRecipe) recipe;
        List<ItemStack[]> out = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            int x = i % 3;
            int y = i / 3;
            Ingredient in = x < shaped.getWidth() && y < shaped.getHeight()
                    ? shaped.getIngredients().get(y * shaped.getWidth() + x) : Ingredient.EMPTY;
            out.add(in.getItems());
        }
        return out;
    }

    private static List<Slot> grid(List<ItemStack[]> cells, long turn) {
        List<Slot> out = new ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            ItemStack[] options = cells.get(i);
            out.add(new Slot(i, options.length == 0 ? ItemStack.EMPTY : options[(int) (turn % options.length)]));
        }
        return out;
    }
}
