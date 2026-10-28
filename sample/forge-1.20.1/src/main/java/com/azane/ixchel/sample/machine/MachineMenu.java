package com.azane.ixchel.sample.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeHooks;

/**
 * The powered stonecutter's menu. The server's machine state reaches the client as {@link #data}
 * ints; the client changes it with {@code clickMenuButton} ids ({@link #BUTTON_REDSTONE} and so on),
 * the same channel vanilla's stonecutter uses for its recipe choice, so there is no network code.
 *
 * <p>Slot indices: {@link #FUEL}, {@link #WATER_SLOT}, {@link #INPUT}, {@link #OUTPUT}, then
 * {@link #INVENTORY_START}{@code ..+26} the main inventory and {@link #HOTBAR_START}{@code ..+8} the
 * hotbar.
 */
public final class MachineMenu extends AbstractContainerMenu {
    public static final int FUEL = 0;
    public static final int WATER_SLOT = 1;
    public static final int INPUT = 2;
    public static final int OUTPUT = 3;
    static final int MACHINE_SLOTS = 4;
    public static final int INVENTORY_START = MACHINE_SLOTS;
    public static final int HOTBAR_START = INVENTORY_START + 27;
    public static final int SLOT_COUNT = HOTBAR_START + 9;

    static final int PROGRESS = 0;
    static final int MAX_PROGRESS = 1;
    static final int ENERGY = 2;
    static final int ENERGY_CAPACITY = 3;
    static final int WATER = 4;
    static final int WATER_CAPACITY = 5;
    static final int RECIPE = 6;
    static final int STATUS = 7;
    static final int REDSTONE = 8;
    static final int BURN = 9;
    static final int BURN_TOTAL = 10;
    /** Six side modes, in {@code Direction} order (down, up, north, south, west, east). */
    static final int SIDES = 11;
    static final int DATA_COUNT = SIDES + 6;

    static final int STATUS_NO_INPUT = 0;
    static final int STATUS_PICK_RECIPE = 1;
    static final int STATUS_NO_ENERGY = 2;
    static final int STATUS_NO_WATER = 3;
    static final int STATUS_OUTPUT_FULL = 4;
    static final int STATUS_REDSTONE_OFF = 5;
    static final int STATUS_WORKING = 6;

    static final int REDSTONE_IGNORED = 0;
    static final int REDSTONE_HIGH = 1;
    static final int REDSTONE_LOW = 2;
    static final int REDSTONE_MODES = 3;

    /** Side modes: none, input, output. */
    static final int SIDE_MODES = 3;

    /** Button ids below this pick that recipe index. */
    static final int BUTTON_REDSTONE = 100;
    /** Plus a side index: cycles that side's mode. */
    static final int BUTTON_SIDE = 101;

    final BlockPos pos;
    final ContainerData data;
    private final Container machine;

    /** Client side, from the open packet. */
    MachineMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(MACHINE_SLOTS), new SimpleContainerData(DATA_COUNT));
    }

    MachineMenu(int id, Inventory inventory, MachineBlockEntity be) {
        this(id, inventory, be.getBlockPos(), be.items, be.data);
    }

    private MachineMenu(int id, Inventory inventory, BlockPos pos, Container machine, ContainerData data) {
        super(Machine.MENU.get(), id);
        this.pos = pos;
        this.machine = machine;
        this.data = data;
        addSlot(new Slot(machine, FUEL, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isFuel(stack);
            }
        });
        addSlot(new Slot(machine, WATER_SLOT, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.WATER_BUCKET);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(machine, INPUT, 0, 0));
        addSlot(new Slot(machine, OUTPUT, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int i = 9; i < 36; i++) {
            addSlot(new Slot(inventory, i, 0, 0));
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(inventory, i, 0, 0));
        }
        addDataSlots(data);
    }

    private static boolean isFuel(ItemStack stack) {
        return ForgeHooks.getBurnTime(stack, null) > 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < BUTTON_REDSTONE) {
            data.set(RECIPE, id);
        } else if (id == BUTTON_REDSTONE) {
            data.set(REDSTONE, (data.get(REDSTONE) + 1) % REDSTONE_MODES);
        } else if (id >= BUTTON_SIDE && id < BUTTON_SIDE + 6) {
            int i = SIDES + id - BUTTON_SIDE;
            data.set(i, (data.get(i) + 1) % SIDE_MODES);
        } else {
            return false;
        }
        return true;
    }

    /** Machine slots go to the inventory; inventory stacks go to the fuel, water or input slot that takes them. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved;
        if (index < MACHINE_SLOTS) {
            moved = moveItemStackTo(stack, INVENTORY_START, SLOT_COUNT, true);
        } else if (isFuel(stack) && !stack.is(Items.WATER_BUCKET)) {
            moved = moveItemStackTo(stack, FUEL, FUEL + 1, false);
        } else if (stack.is(Items.WATER_BUCKET)) {
            moved = moveItemStackTo(stack, WATER_SLOT, WATER_SLOT + 1, false);
        } else {
            moved = moveItemStackTo(stack, INPUT, INPUT + 1, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine.stillValid(player)
                && player.level().getBlockState(pos).is(Machine.BLOCK.get())
                && Machine.inReach(pos, player.getX(), player.getY(), player.getZ());
    }
}
