package com.azane.ixchel.sample.machine;

import com.azane.ixchel.sample.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeHooks;

/**
 * The powered stonecutter's menu: only the vanilla slot transactions. The machine's state and
 * settings do not travel here; the screen watches the replicated {@link MachineState} and sends
 * intent packets.
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

    final BlockPos pos;
    private final Container machine;

    /** Client side, from the open packet. */
    public MachineMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(MACHINE_SLOTS));
    }

    MachineMenu(int id, Inventory inventory, MachineBlockEntity be) {
        this(id, inventory, be.getBlockPos(), be.items);
    }

    private MachineMenu(int id, Inventory inventory, BlockPos pos, Container machine) {
        super(ModMenus.POWERED_STONECUTTER.get(), id);
        this.pos = pos;
        this.machine = machine;
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
    }

    private static boolean isFuel(ItemStack stack) {
        return ForgeHooks.getBurnTime(stack, null) > 0;
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
        return machine.stillValid(player) && MachineBlockEntity.reachable(player, pos) != null;
    }
}
