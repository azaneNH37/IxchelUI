package com.azane.ixchel.sample.machine;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Feed;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.network.Replication;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * The machine screen. It reads the machine's replicated {@link MachineState} through {@link #STATE}
 * and sends intents; a click shows its effect once the server's next snapshot arrives. Intents set
 * values rather than toggle them, so a repeated or late one does no harm.
 */
@UiView("ixchel_sample:ui/machine/panel.html")
final class MachineModel extends ViewModel {
    /** A stonecutting result; recomputed only when the input item or the choice changes. */
    record RecipeRow(int index, ItemStack icon, String name, boolean selected) {
    }

    record SideRow(Direction side, Component face, Component mode, boolean input, boolean output) {
    }

    /** The replicated state of the machine at a position; watched while some model reads it. */
    private static final Feed<BlockPos, MachineState> STATE = Feed.opened(
            pos -> Replication.watch(Network.MACHINE, pos),
            replica -> replica.get() == null ? MachineState.NONE : replica.get());

    /** Pixels of furnace.png's arrow and flame. */
    private static final int ARROW_WIDTH = 24;
    private static final int FLAME_HEIGHT = 14;

    private final BlockPos pos;

    final Cell<AbstractContainerMenu> menu;
    final Cell<Slot> fuelSlot;
    final Cell<Slot> waterSlot;
    final Cell<Slot> inputSlot;
    final Cell<Slot> outputSlot;

    final Cell<MachineState> machine;
    final Cell<Item> input;
    final Cell<String> waterColor;
    /** The bars' maxima. */
    final Cell<Integer> energyCapacity = state(MachineBlockEntity.ENERGY_CAPACITY);
    final Cell<Integer> waterCapacity = state(MachineBlockEntity.WATER_CAPACITY);

    final Derived<Component> statusText;
    final Derived<Boolean> statusBad;
    final Derived<String> energyText;
    final Derived<String> waterText;
    final Derived<Integer> arrowWidth;
    final Derived<Integer> flameHeight;
    final Derived<Integer> flameY;
    final Derived<List<RecipeRow>> recipes;
    final Derived<Component> redstoneLabel;
    final Derived<List<SideRow>> sideRows;

    final Action<RecipeRow> pick = action(this::pick);
    final Action<Void> cycleRedstone = action(this::cycleRedstone);
    final Action<SideRow> cycleSide = action(this::cycleSide);

    MachineModel(MachineMenu menu) {
        pos = menu.pos;
        this.menu = state(menu);
        fuelSlot = state(menu.getSlot(MachineMenu.FUEL));
        waterSlot = state(menu.getSlot(MachineMenu.WATER_SLOT));
        inputSlot = state(menu.getSlot(MachineMenu.INPUT));
        outputSlot = state(menu.getSlot(MachineMenu.OUTPUT));
        machine = source(STATE, menu.pos);
        input = source(Source.polled(() -> menu.getSlot(MachineMenu.INPUT).getItem().getItem()));
        waterColor = source(Source.polled(() -> {
            Minecraft mc = Minecraft.getInstance();
            int rgb = mc.level == null ? 0x3F76E4 : BiomeColors.getAverageWaterColor(mc.level, menu.pos);
            return String.format("#%06X", rgb & 0xFFFFFF);
        }));

        statusText = derive(machine, m -> m.status().label());
        statusBad = derive(machine, m -> m.status().bad);
        energyText = derive(machine, m -> m.energy() + " / " + MachineBlockEntity.ENERGY_CAPACITY + " FE");
        waterText = derive(machine, m -> m.water() + " / " + MachineBlockEntity.WATER_CAPACITY + " mB");
        arrowWidth = derive(machine, m -> m.progress() * ARROW_WIDTH / MachineBlockEntity.CUT_TICKS);
        flameHeight = derive(machine, m -> m.burnTotal() <= 0 ? 0 : (m.burn() * FLAME_HEIGHT + m.burnTotal() - 1) / m.burnTotal());
        // The fill stands on the flame's bottom, so it shows the bottom rows of the lit flame.
        flameY = derive(flameHeight, h -> h - FLAME_HEIGHT);
        Derived<Integer> recipe = derive(machine, MachineState::recipe);
        recipes = derive(input, recipe, MachineModel::recipes);
        redstoneLabel = derive(machine, m -> Component.translatable("ixchel_sample.machine.redstone", m.redstone().label()));
        sideRows = derive(derive(machine, MachineState::sides), MachineModel::sideRows);
    }

    private void pick(RecipeRow r) {
        Network.sendToServer(new PickRecipePacket(pos, r.index()));
    }

    private void cycleRedstone() {
        Network.sendToServer(new SetRedstonePacket(pos, machine.get().redstone().next()));
    }

    private void cycleSide(SideRow s) {
        Network.sendToServer(new SetSidePacket(pos, s.side(), machine.get().sides().get(s.side().get3DDataValue()).next()));
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
            out.add(new SideRow(d, Component.translatable("ixchel_sample.machine.face." + d.getName()), m.label(),
                    m == MachineState.SideMode.INPUT, m == MachineState.SideMode.OUTPUT));
        }
        return out;
    }
}
