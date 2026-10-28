package com.azane.ixchel.sample.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
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
 * The redstone mode can make it need a signal or its absence. Side modes are stored and shown only;
 * nothing pipes through them. Everything the screen shows travels as {@link MachineMenu} data ints
 * (synced as shorts, so every value stays below 32768).
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
    private int status;
    private int redstone;
    private final int[] sides = new int[6];
    /** The input item the recipe index was chosen for; another item clears the choice, as vanilla's stonecutter. */
    private ItemStack recipeInput = ItemStack.EMPTY;

    /** The values {@link MachineMenu} syncs, in its index order; writes come from {@link MachineMenu#clickMenuButton}. */
    final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case MachineMenu.PROGRESS -> progress;
                case MachineMenu.MAX_PROGRESS -> CUT_TICKS;
                case MachineMenu.ENERGY -> energy;
                case MachineMenu.ENERGY_CAPACITY -> ENERGY_CAPACITY;
                case MachineMenu.WATER -> water;
                case MachineMenu.WATER_CAPACITY -> WATER_CAPACITY;
                case MachineMenu.RECIPE -> recipe;
                case MachineMenu.STATUS -> status;
                case MachineMenu.REDSTONE -> redstone;
                case MachineMenu.BURN -> burn;
                case MachineMenu.BURN_TOTAL -> burnTotal;
                default -> sides[i - MachineMenu.SIDES];
            };
        }

        @Override
        public void set(int i, int value) {
            switch (i) {
                case MachineMenu.RECIPE -> recipe = value;
                case MachineMenu.REDSTONE -> redstone = value;
                default -> {
                    if (i >= MachineMenu.SIDES && i < MachineMenu.DATA_COUNT) {
                        sides[i - MachineMenu.SIDES] = value;
                    }
                }
            }
            setChanged();
        }

        @Override
        public int getCount() {
            return MachineMenu.DATA_COUNT;
        }
    };

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(Machine.BLOCK_ENTITY.get(), pos, state);
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
                burn = burnTotal = Math.min(time, Short.MAX_VALUE);
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
        int next = cut(level, pos, input);
        if (next != MachineMenu.STATUS_WORKING && progress != 0) {
            progress = 0;
            changed = true;
        }
        status = next;
        if (changed) {
            setChanged();
        }
    }

    /** Advances the cut by a tick when it can run; returns the status. */
    private int cut(Level level, BlockPos pos, ItemStack input) {
        if (input.isEmpty()) {
            return MachineMenu.STATUS_NO_INPUT;
        }
        List<StonecutterRecipe> recipes = recipes(level, input);
        if (recipe < 0 || recipe >= recipes.size()) {
            return MachineMenu.STATUS_PICK_RECIPE;
        }
        boolean signal = level.hasNeighborSignal(pos);
        if (redstone == MachineMenu.REDSTONE_HIGH && !signal || redstone == MachineMenu.REDSTONE_LOW && signal) {
            return MachineMenu.STATUS_REDSTONE_OFF;
        }
        ItemStack result = recipes.get(recipe).assemble(new SimpleContainer(input), level.registryAccess());
        ItemStack out = items.getItem(MachineMenu.OUTPUT);
        if (!out.isEmpty() && (!ItemStack.isSameItemSameTags(out, result)
                || out.getCount() + result.getCount() > out.getMaxStackSize())) {
            return MachineMenu.STATUS_OUTPUT_FULL;
        }
        if (water < WATER_PER_CUT) {
            return MachineMenu.STATUS_NO_WATER;
        }
        if (energy < FE_PER_TICK) {
            return MachineMenu.STATUS_NO_ENERGY;
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
        return MachineMenu.STATUS_WORKING;
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
        tag.putInt("Redstone", redstone);
        tag.putIntArray("Sides", sides);
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
        redstone = tag.getInt("Redstone");
        int[] s = tag.getIntArray("Sides");
        System.arraycopy(s, 0, sides, 0, Math.min(s.length, sides.length));
        recipeInput = items.getItem(MachineMenu.INPUT).copyWithCount(1);
    }
}
