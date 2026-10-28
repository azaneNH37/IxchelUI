package com.azane.ixchel.sample.machine;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The machine screen. Every machine value is a menu data int read with {@link Source#polled}: the
 * host wires {@code ContainerData} itself, the library has no helper for it (#004 keeps data ports
 * to JDK types; a vanilla-typed helper would sit in the mc layer once a second host needs one).
 * Changes go back as {@code clickMenuButton} ids.
 */
final class MachineView extends ViewModel {
    record RecipeRow(int index, ItemStack icon, String name, boolean selected) {
        /** {@code icon} is left out: a stack has no equality, and the name stands for it. */
        @Override
        public boolean equals(Object o) {
            return o instanceof RecipeRow r && index == r.index && selected == r.selected && name.equals(r.name);
        }

        @Override
        public int hashCode() {
            return index * 31 + name.hashCode() + (selected ? 1 : 0);
        }
    }

    record SideRow(int index, String face, String mode, boolean input, boolean output) {
    }

    private static final String[] STATUS = {
            "Idle: no input", "Pick a recipe", "No energy", "No water", "Output full", "Redstone off", "Working"};
    private static final String[] REDSTONE = {"Ignored", "Needs signal", "Needs no signal"};
    private static final String[] SIDE_MODES = {"None", "Input", "Output"};
    /** Pixels of furnace.png's arrow and flame. */
    private static final int ARROW_WIDTH = 24;
    private static final int FLAME_HEIGHT = 14;

    private final MachineMenu menu;

    final Value<Integer> progress;
    final Value<Integer> maxProgress;
    final Value<Integer> energy;
    final Value<Integer> energyCapacity;
    final Value<Integer> water;
    final Value<Integer> waterCapacity;
    final Value<Integer> recipe;
    final Value<Integer> status;
    final Value<Integer> redstone;
    final Value<Integer> burn;
    final Value<Integer> burnTotal;
    final Value<List<Integer>> sides;
    final Value<Item> input;
    final Value<String> waterColor;
    final Value<Boolean> sidesOpen = state(false);

    final Derived<String> statusText;
    final Derived<Boolean> statusBad;
    final Derived<String> energyText;
    final Derived<String> energyHeight;
    final Derived<String> waterText;
    final Derived<String> waterHeight;
    final Derived<String> arrowWidth;
    final Derived<String> flameHeight;
    final Derived<String> flameY;
    final Derived<List<RecipeRow>> recipes;
    final Derived<Boolean> noRecipes;
    final Derived<String> redstoneLabel;
    final Derived<List<SideRow>> sideRows;
    final Value<List<Integer>> inventory = state(range(MachineMenu.INVENTORY_START, MachineMenu.HOTBAR_START));
    final Value<List<Integer>> hotbar = state(range(MachineMenu.HOTBAR_START, MachineMenu.SLOT_COUNT));

    final Action<RecipeRow> pick = action(r -> button(r.index()));
    final Action<Void> cycleRedstone = action(() -> button(MachineMenu.BUTTON_REDSTONE));
    final Action<SideRow> cycleSide = action(s -> button(MachineMenu.BUTTON_SIDE + s.index()));
    final Action<Void> toggleSides = action(() -> sidesOpen.set(!sidesOpen.get()));

    MachineView(MachineMenu menu) {
        this.menu = menu;
        progress = data(MachineMenu.PROGRESS);
        maxProgress = data(MachineMenu.MAX_PROGRESS);
        energy = data(MachineMenu.ENERGY);
        energyCapacity = data(MachineMenu.ENERGY_CAPACITY);
        water = data(MachineMenu.WATER);
        waterCapacity = data(MachineMenu.WATER_CAPACITY);
        recipe = data(MachineMenu.RECIPE);
        status = data(MachineMenu.STATUS);
        redstone = data(MachineMenu.REDSTONE);
        burn = data(MachineMenu.BURN);
        burnTotal = data(MachineMenu.BURN_TOTAL);
        sides = source(Source.polled(() -> {
            List<Integer> out = new ArrayList<>(6);
            for (int i = 0; i < 6; i++) {
                out.add(menu.data.get(MachineMenu.SIDES + i));
            }
            return out;
        }));
        input = source(Source.polled(() -> menu.getSlot(MachineMenu.INPUT).getItem().getItem()));
        waterColor = source(Source.polled(() -> {
            Minecraft mc = Minecraft.getInstance();
            int rgb = mc.level == null ? 0x3F76E4 : BiomeColors.getAverageWaterColor(mc.level, menu.pos);
            return String.format("#%06X", rgb & 0xFFFFFF);
        }));

        statusText = derive(status, s -> s >= 0 && s < STATUS.length ? STATUS[s] : "");
        statusBad = derive(status, s -> s >= MachineMenu.STATUS_NO_ENERGY && s <= MachineMenu.STATUS_REDSTONE_OFF);
        energyText = derive(energy, energyCapacity, (v, max) -> v + " / " + max + " FE");
        energyHeight = derive(energy, energyCapacity, MachineView::percent);
        waterText = derive(water, waterCapacity, (v, max) -> v + " / " + max + " mB");
        waterHeight = derive(water, waterCapacity, MachineView::percent);
        arrowWidth = derive(progress, maxProgress, (p, max) -> (max <= 0 ? 0 : p * ARROW_WIDTH / max) + "px");
        Derived<Integer> flame = derive(burn, burnTotal, (b, total) -> total <= 0 ? 0 : (b * FLAME_HEIGHT + total - 1) / total);
        flameHeight = derive(flame, h -> h + "px");
        // The fill stands on the flame's bottom, so it shows the bottom rows of the lit flame.
        flameY = derive(flame, h -> -(FLAME_HEIGHT - h) + "px");
        recipes = derive(input, recipe, MachineView::recipes);
        noRecipes = derive(recipes, List::isEmpty);
        redstoneLabel = derive(redstone, r -> "Redstone: " + (r >= 0 && r < REDSTONE.length ? REDSTONE[r] : "?"));
        sideRows = derive(sides, MachineView::sideRows);
    }

    private Value<Integer> data(int index) {
        return source(Source.polled(() -> menu.data.get(index)));
    }

    private void button(int id) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private static String percent(int value, int max) {
        return String.format(Locale.ROOT, "%.1f%%", max <= 0 ? 0 : 100f * value / max);
    }

    private static List<RecipeRow> recipes(Item item, int selected) {
        Minecraft mc = Minecraft.getInstance();
        if (item == Items.AIR || mc.level == null) {
            return List.of();
        }
        List<StonecutterRecipe> all = MachineBlockEntity.recipes(mc.level, new ItemStack(item));
        List<RecipeRow> out = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            ItemStack result = all.get(i).getResultItem(mc.level.registryAccess());
            String name = result.getHoverName().getString() + (result.getCount() > 1 ? " x" + result.getCount() : "");
            out.add(new RecipeRow(i, result, name, i == selected));
        }
        return out;
    }

    private static List<SideRow> sideRows(List<Integer> modes) {
        List<SideRow> out = new ArrayList<>(6);
        for (Direction d : Direction.values()) {
            int m = modes.get(d.get3DDataValue());
            String face = d.getName().substring(0, 1).toUpperCase(Locale.ROOT) + d.getName().substring(1);
            out.add(new SideRow(d.get3DDataValue(), face, SIDE_MODES[m % SIDE_MODES.length], m == 1, m == 2));
        }
        return out;
    }

    private static List<Integer> range(int from, int to) {
        List<Integer> out = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            out.add(i);
        }
        return out;
    }
}
