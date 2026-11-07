package com.azane.ixchel.sample.machine;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Feed;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.Value;
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
 * The machine screen, three pages under tabs: run (gauges, slots, recipes), config (redstone mode,
 * auto-output, priority, sides) and upgrades. It reads the machine's replicated {@link MachineState}
 * through {@link #STATE} and sends intents; a click shows its effect once the server's next snapshot
 * arrives. The config page's controls are the screen's own values, started from the settings the
 * menu opened with; each edit there sends them all ({@link SetConfigPacket}) when they
 * changed. Intents set values rather than toggle them, so a repeated or late one does no harm.
 */
@UiView("ixchel_sample:ui/machine/panel.html")
final class MachineModel extends ViewModel {
    /** A stonecutting result; recomputed only when the input item or the choice changes. */
    record RecipeRow(int index, ItemStack icon, String name, boolean selected) {
    }

    record SideRow(Direction side, Component face, Component mode, boolean input, boolean output) {
    }

    /** An upgrade card: what it does and the items that count as it, shown in turn. */
    record Upgrade(String id, List<ItemStack> items, Component name, Component effect) {
    }

    /** The replicated state of the machine at a position; watched while some model reads it. */
    private static final Feed<BlockPos, MachineState> STATE = Feed.opened(
            pos -> Replication.watch(Network.MACHINE, pos),
            replica -> replica.get() == null ? MachineState.NONE : replica.get());

    private final BlockPos pos;
    private MachineConfig sent;

    final Cell<AbstractContainerMenu> menu;
    final Cell<Slot> fuelSlot;
    final Cell<Slot> waterSlot;
    final Cell<Slot> inputSlot;
    final Cell<Slot> outputSlot;

    final Value<String> tab = state("run");
    final Cell<MachineState> machine;
    final Cell<Item> input;
    final Cell<String> waterColor;
    /** The bars' maxima. */
    final Cell<Integer> energyCapacity = state(MachineBlockEntity.ENERGY_CAPACITY);
    final Cell<Integer> waterCapacity = state(MachineBlockEntity.WATER_CAPACITY);
    final Cell<Integer> cutTicks = state(MachineBlockEntity.CUT_TICKS);

    /** The config page: the redstone mode as an index into {@link #redstoneOptions}. */
    final Value<Integer> redstone;
    final Value<Boolean> autoOutput;
    final Value<Integer> priority;
    final Cell<List<Component>> redstoneOptions = state(MachineState.Redstone.ALL.stream()
            .map(MachineState.Redstone::label).toList());
    final Cell<Integer> priorityMax = state(MachineConfig.PRIORITY_MAX);

    final Derived<Component> statusText;
    final Derived<Boolean> statusBad;
    final Derived<Boolean> running;
    final Derived<Boolean> outOfEnergy;
    final Derived<Boolean> outputFull;
    final Derived<String> energyText;
    final Derived<String> waterText;
    /** The burning fuel's share left, 0 to 1, swept round the fuel slot. */
    final Derived<Float> fuelLeft;
    final Derived<List<RecipeRow>> recipes;
    final Derived<List<SideRow>> sideRows;
    final Cell<List<Upgrade>> upgrades = state(List.of(
            upgrade("speed", Items.SUGAR, Items.REDSTONE, Items.BLAZE_POWDER),
            upgrade("efficiency", Items.GLOWSTONE_DUST, Items.LAPIS_LAZULI, Items.AMETHYST_SHARD),
            upgrade("capacity", Items.CHEST, Items.BARREL, Items.SHULKER_BOX)));

    final Action<RecipeRow> pick = action(this::pick);
    final Action<SideRow> cycleSide = action(this::cycleSide);
    final Action<Void> applyConfig = action(this::applyConfig);

    MachineModel(MachineMenu menu) {
        pos = menu.pos;
        sent = menu.config;
        this.menu = state(menu);
        fuelSlot = state(menu.getSlot(MachineMenu.FUEL));
        waterSlot = state(menu.getSlot(MachineMenu.WATER_SLOT));
        inputSlot = state(menu.getSlot(MachineMenu.INPUT));
        outputSlot = state(menu.getSlot(MachineMenu.OUTPUT));
        redstone = state(menu.config.redstone().ordinal());
        autoOutput = state(menu.config.autoOutput());
        priority = state(menu.config.priority());
        machine = source(STATE, menu.pos);
        input = source(Source.polled(() -> menu.getSlot(MachineMenu.INPUT).getItem().getItem()));
        waterColor = source(Source.polled(() -> {
            Minecraft mc = Minecraft.getInstance();
            int rgb = mc.level == null ? 0x3F76E4 : BiomeColors.getAverageWaterColor(mc.level, menu.pos);
            return String.format("#%06X", rgb & 0xFFFFFF);
        }));

        statusText = derive(machine, m -> m.status().label());
        statusBad = derive(machine, m -> m.status().bad);
        running = derive(machine, m -> m.status() == MachineState.Status.WORKING);
        outOfEnergy = derive(machine, m -> m.status() == MachineState.Status.NO_ENERGY);
        outputFull = derive(machine, m -> m.status() == MachineState.Status.OUTPUT_FULL);
        energyText = derive(machine, m -> m.energy() + " / " + MachineBlockEntity.ENERGY_CAPACITY + " FE");
        waterText = derive(machine, m -> m.water() + " / " + MachineBlockEntity.WATER_CAPACITY + " mB");
        fuelLeft = derive(machine, m -> m.burnTotal() <= 0 ? 0f : (float) m.burn() / m.burnTotal());
        Derived<Integer> recipe = derive(machine, MachineState::recipe);
        recipes = derive(input, recipe, MachineModel::recipes);
        sideRows = derive(derive(machine, MachineState::sides), MachineModel::sideRows);
    }

    private void pick(RecipeRow r) {
        Network.sendToServer(new PickRecipePacket(pos, r.index()));
    }

    private void cycleSide(SideRow s) {
        Network.sendToServer(new SetSidePacket(pos, s.side(), machine.get().sides().get(s.side().get3DDataValue()).next()));
    }

    private void applyConfig() {
        MachineConfig now = new MachineConfig(MachineState.Redstone.ALL.get(redstone.get()), autoOutput.get(), priority.get());
        if (!now.equals(sent)) {
            sent = now;
            Network.sendToServer(new SetConfigPacket(pos, now));
        }
    }

    private static Upgrade upgrade(String id, Item... items) {
        return new Upgrade(id, List.of(items).stream().map(ItemStack::new).toList(),
                Component.translatable("ixchel_sample.machine.upgrade." + id),
                Component.translatable("ixchel_sample.machine.upgrade." + id + ".effect"));
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
