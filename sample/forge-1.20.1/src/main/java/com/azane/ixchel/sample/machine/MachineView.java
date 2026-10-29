package com.azane.ixchel.sample.machine;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.network.Replica;
import com.azane.ixchel.sample.network.Replication;
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
 * The machine screen. It watches the machine's replicated {@link MachineState} for as long as the
 * model lives and sends intents; a click shows its effect once the server's next snapshot arrives.
 * Intents set values rather than toggle them, so a repeated or late one does no harm.
 */
final class MachineView extends ViewModel {
    /** A stonecutting result; recomputed only when the input item or the choice changes. */
    record RecipeRow(int index, ItemStack icon, String name, boolean selected) {
    }

    record SideRow(Direction side, String face, String mode, boolean input, boolean output) {
    }

    /** Pixels of furnace.png's arrow and flame. */
    private static final int ARROW_WIDTH = 24;
    private static final int FLAME_HEIGHT = 14;

    private final MachineMenu menu;

    final Value<MachineState> machine;
    final Value<Item> input;
    final Value<String> waterColor;

    final Derived<String> statusText;
    final Derived<Boolean> statusBad;
    final Derived<String> energyText;
    final Derived<String> energyHeight;
    final Derived<String> waterText;
    final Derived<String> waterHeight;
    final Derived<Integer> arrowWidth;
    final Derived<Integer> flameHeight;
    final Derived<Integer> flameY;
    final Derived<List<RecipeRow>> recipes;
    final Derived<String> redstoneLabel;
    final Derived<List<SideRow>> sideRows;
    final Value<List<Integer>> inventory = state(range(MachineMenu.INVENTORY_START, MachineMenu.HOTBAR_START));
    final Value<List<Integer>> hotbar = state(range(MachineMenu.HOTBAR_START, MachineMenu.SLOT_COUNT));

    final Action<RecipeRow> pick = action(this::pick);
    final Action<Void> cycleRedstone = action(this::cycleRedstone);
    final Action<SideRow> cycleSide = action(this::cycleSide);

    MachineView(MachineMenu menu) {
        this.menu = menu;
        Replica<MachineState> replica = Replication.watch(Network.MACHINE, menu.pos);
        scope().own(replica);
        machine = source(Source.polled(() -> replica.get() == null ? MachineState.NONE : replica.get()));
        input = source(Source.polled(() -> menu.getSlot(MachineMenu.INPUT).getItem().getItem()));
        waterColor = source(Source.polled(() -> {
            Minecraft mc = Minecraft.getInstance();
            int rgb = mc.level == null ? 0x3F76E4 : BiomeColors.getAverageWaterColor(mc.level, menu.pos);
            return String.format("#%06X", rgb & 0xFFFFFF);
        }));

        statusText = derive(machine, m -> m.status().label);
        statusBad = derive(machine, m -> m.status().bad);
        energyText = derive(machine, m -> m.energy() + " / " + MachineBlockEntity.ENERGY_CAPACITY + " FE");
        energyHeight = derive(machine, m -> percent(m.energy(), MachineBlockEntity.ENERGY_CAPACITY));
        waterText = derive(machine, m -> m.water() + " / " + MachineBlockEntity.WATER_CAPACITY + " mB");
        waterHeight = derive(machine, m -> percent(m.water(), MachineBlockEntity.WATER_CAPACITY));
        arrowWidth = derive(machine, m -> m.progress() * ARROW_WIDTH / MachineBlockEntity.CUT_TICKS);
        flameHeight = derive(machine, m -> m.burnTotal() <= 0 ? 0 : (m.burn() * FLAME_HEIGHT + m.burnTotal() - 1) / m.burnTotal());
        // The fill stands on the flame's bottom, so it shows the bottom rows of the lit flame.
        flameY = derive(flameHeight, h -> h - FLAME_HEIGHT);
        Derived<Integer> recipe = derive(machine, MachineState::recipe);
        recipes = derive(input, recipe, MachineView::recipes);
        redstoneLabel = derive(machine, m -> "Redstone: " + m.redstone().label);
        sideRows = derive(derive(machine, MachineState::sides), MachineView::sideRows);
    }

    private void pick(RecipeRow r) {
        Network.sendToServer(new PickRecipePacket(menu.pos, r.index()));
    }

    private void cycleRedstone() {
        Network.sendToServer(new SetRedstonePacket(menu.pos, machine.get().redstone().next()));
    }

    private void cycleSide(SideRow s) {
        Network.sendToServer(new SetSidePacket(menu.pos, s.side(), machine.get().sides().get(s.side().get3DDataValue()).next()));
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

    private static List<SideRow> sideRows(List<MachineState.SideMode> modes) {
        List<SideRow> out = new ArrayList<>(6);
        for (Direction d : Direction.values()) {
            MachineState.SideMode m = modes.get(d.get3DDataValue());
            String face = d.getName().substring(0, 1).toUpperCase(Locale.ROOT) + d.getName().substring(1);
            out.add(new SideRow(d, face, m.label, m == MachineState.SideMode.INPUT, m == MachineState.SideMode.OUTPUT));
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
