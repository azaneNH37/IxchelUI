package com.azane.ixchel.sample.machine;

import com.azane.ixchel.sample.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;

import java.util.List;

/**
 * The powered stonecutter's state, ticked on the server. Burning vanilla fuel makes FE; a water bucket
 * fills the tank; each cut takes {@link #CUT_TICKS} ticks of {@link #FE_PER_TICK} FE and
 * {@link #WATER_PER_CUT} mB of water and runs the selected vanilla stonecutting recipe for the input.
 * The redstone mode can make it need a signal or its absence. Players within reach see it as a
 * replicated {@link MachineState} ({@link #view}) and change it through intent packets, which land
 * in {@link #pickRecipe}, {@link #setRedstone} and {@link #setSide}.
 */
public final class MachineBlockEntity extends BlockEntity implements MenuProvider {
    static final int ENERGY_CAPACITY = 10_000;
    static final int WATER_CAPACITY = 4_000;
    static final int FE_PER_BURN_TICK = 20;
    static final int FE_PER_TICK = 10;
    static final int WATER_PER_CUT = 100;
    static final int CUT_TICKS = 60;

    final SimpleContainer items = new SimpleContainer(MachineMenu.MACHINE_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            MachineBlockEntity.this.setChanged();
        }
    };
    private int energy;
    private int water;
    private int burn;
    private int burnTotal;
    private int progress;
    private int recipe = -1;
    private MachineState.Status status = MachineState.Status.NO_INPUT;
    private MachineState.Redstone redstone = MachineState.Redstone.IGNORED;
    private final MachineState.SideMode[] sides = {MachineState.SideMode.NONE, MachineState.SideMode.NONE,
            MachineState.SideMode.NONE, MachineState.SideMode.NONE, MachineState.SideMode.NONE, MachineState.SideMode.NONE};
    /** The input item the recipe index was chosen for; another item clears the choice, as vanilla's stonecutter. */
    private ItemStack recipeInput = ItemStack.EMPTY;

    /** The machine at {@code pos} when {@code player} is within eight blocks of its center, as vanilla containers; else {@code null}. */
    static MachineBlockEntity reachable(Player player, BlockPos pos) {
        return pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()) <= 64
                && player.level().getBlockEntity(pos) instanceof MachineBlockEntity be ? be : null;
    }

    /** What {@code player} sees of the machine at {@code pos}: its state while within reach. */
    public static MachineState view(ServerPlayer player, BlockPos pos) {
        MachineBlockEntity be = reachable(player, pos);
        return be == null ? null : new MachineState(be.progress, be.energy, be.water, be.burn, be.burnTotal, be.recipe,
                be.status, be.redstone, List.of(be.sides));
    }

    /** Ignored unless {@code index} is a recipe for the current input, or {@code -1}. */
    void pickRecipe(int index) {
        if (index >= -1 && index < recipes(level, items.getItem(MachineMenu.INPUT)).size()) {
            recipe = index;
            setChanged();
        }
    }

    void setRedstone(MachineState.Redstone mode) {
        redstone = mode;
        setChanged();
    }

    void setSide(Direction side, MachineState.SideMode mode) {
        sides[side.get3DDataValue()] = mode;
        setChanged();
    }

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWERED_STONECUTTER.get(), pos, state);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        be.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        boolean changed = false;
        if (burn == 0 && energy < ENERGY_CAPACITY) {
            ItemStack fuel = items.getItem(MachineMenu.FUEL);
            int time = fuel.isEmpty() ? 0 : ForgeHooks.getBurnTime(fuel, null);
            if (time > 0) {
                burn = burnTotal = time;
                ItemStack remainder = fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if (fuel.isEmpty()) {
                    items.setItem(MachineMenu.FUEL, remainder);
                }
                changed = true;
            }
        }
        if (burn > 0) {
            burn--;
            energy = Math.min(ENERGY_CAPACITY, energy + FE_PER_BURN_TICK);
            changed = true;
        }
        ItemStack bucket = items.getItem(MachineMenu.WATER_SLOT);
        if (bucket.is(Items.WATER_BUCKET) && water + 1000 <= WATER_CAPACITY) {
            water += 1000;
            items.setItem(MachineMenu.WATER_SLOT, new ItemStack(Items.BUCKET));
            changed = true;
        }

        ItemStack input = items.getItem(MachineMenu.INPUT);
        if (!ItemStack.isSameItem(input, recipeInput)) {
            recipeInput = input.copyWithCount(1);
            recipe = -1;
            progress = 0;
        }
        MachineState.Status next = cut(level, pos, input);
        if (next != MachineState.Status.WORKING && progress != 0) {
            progress = 0;
            changed = true;
        }
        status = next;
        if (changed) {
            setChanged();
        }
    }

    /** Advances the cut by a tick when it can run; returns the status. */
    private MachineState.Status cut(Level level, BlockPos pos, ItemStack input) {
        if (input.isEmpty()) {
            return MachineState.Status.NO_INPUT;
        }
        List<StonecutterRecipe> recipes = recipes(level, input);
        if (recipe < 0 || recipe >= recipes.size()) {
            return MachineState.Status.PICK_RECIPE;
        }
        boolean signal = level.hasNeighborSignal(pos);
        if (redstone == MachineState.Redstone.HIGH && !signal || redstone == MachineState.Redstone.LOW && signal) {
            return MachineState.Status.REDSTONE_OFF;
        }
        ItemStack result = recipes.get(recipe).assemble(new SimpleContainer(input), level.registryAccess());
        ItemStack out = items.getItem(MachineMenu.OUTPUT);
        if (!out.isEmpty() && (!ItemStack.isSameItemSameTags(out, result)
                || out.getCount() + result.getCount() > out.getMaxStackSize())) {
            return MachineState.Status.OUTPUT_FULL;
        }
        if (water < WATER_PER_CUT) {
            return MachineState.Status.NO_WATER;
        }
        if (energy < FE_PER_TICK) {
            return MachineState.Status.NO_ENERGY;
        }
        energy -= FE_PER_TICK;
        if (++progress >= CUT_TICKS) {
            progress = 0;
            water -= WATER_PER_CUT;
            input.shrink(1);
            if (out.isEmpty()) {
                items.setItem(MachineMenu.OUTPUT, result);
            } else {
                out.grow(result.getCount());
            }
            items.setChanged();
        }
        return MachineState.Status.WORKING;
    }

    /** Vanilla's stonecutting recipes for {@code input}, in the order its stonecutter lists them. */
    static List<StonecutterRecipe> recipes(Level level, ItemStack input) {
        return level.getRecipeManager().getRecipesFor(RecipeType.STONECUTTING, new SimpleContainer(input), level);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MachineMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.createTag());
        tag.putInt("Energy", energy);
        tag.putInt("Water", water);
        tag.putInt("Burn", burn);
        tag.putInt("BurnTotal", burnTotal);
        tag.putInt("Progress", progress);
        tag.putInt("Recipe", recipe);
        tag.putInt("Redstone", redstone.ordinal());
        int[] s = new int[sides.length];
        for (int i = 0; i < s.length; i++) {
            s[i] = sides[i].ordinal();
        }
        tag.putIntArray("Sides", s);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.fromTag(tag.getList("Items", Tag.TAG_COMPOUND));
        energy = tag.getInt("Energy");
        water = tag.getInt("Water");
        burn = tag.getInt("Burn");
        burnTotal = tag.getInt("BurnTotal");
        progress = tag.getInt("Progress");
        recipe = tag.contains("Recipe") ? tag.getInt("Recipe") : -1;
        redstone = MachineState.Redstone.values()[Math.floorMod(tag.getInt("Redstone"), MachineState.Redstone.values().length)];
        int[] s = tag.getIntArray("Sides");
        for (int i = 0; i < Math.min(s.length, sides.length); i++) {
            sides[i] = MachineState.SideMode.values()[Math.floorMod(s[i], MachineState.SideMode.values().length)];
        }
        recipeInput = items.getItem(MachineMenu.INPUT).copyWithCount(1);
    }
}
