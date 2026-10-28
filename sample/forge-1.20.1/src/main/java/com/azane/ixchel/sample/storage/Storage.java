package com.azane.ixchel.sample.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server side: the item handlers of the block entities within {@link #RADIUS} blocks of a point, read
 * as one storage. Each call scans again, so a chest placed or emptied meanwhile counts at once.
 * A chest counts as its own 27 slots: through its capability, each half of a double chest would
 * report both halves.
 */
final class Storage {
    static final int RADIUS = 8;

    /** Items that stack together: same item, same tag. */
    record Key(Item item, CompoundTag tag) {
        static Key of(ItemStack stack) {
            return new Key(stack.getItem(), stack.getTag());
        }

        boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() == item && Objects.equals(stack.getTag(), tag);
        }

        ItemStack stack(int count) {
            ItemStack s = new ItemStack(item, count);
            s.setTag(tag == null ? null : tag.copy());
            return s;
        }
    }

    private final List<IItemHandler> handlers;

    private Storage(List<IItemHandler> handlers) {
        this.handlers = handlers;
    }

    static Storage around(ServerLevel level, BlockPos center) {
        List<IItemHandler> handlers = new ArrayList<>();
        int r2 = RADIUS * RADIUS;
        for (int cx = SectionPos.blockToSectionCoord(center.getX() - RADIUS);
             cx <= SectionPos.blockToSectionCoord(center.getX() + RADIUS); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(center.getZ() - RADIUS);
                 cz <= SectionPos.blockToSectionCoord(center.getZ() + RADIUS); cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.isRemoved() || be.getBlockPos().distSqr(center) > r2) {
                        continue;
                    }
                    if (be instanceof ChestBlockEntity chest) {
                        handlers.add(new InvWrapper(chest));
                    } else {
                        be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(handlers::add);
                    }
                }
            }
        }
        return new Storage(handlers);
    }

    /** Every kind of stack in the storage and how many there are. */
    Map<Key, Long> counts() {
        Map<Key, Long> out = new HashMap<>();
        for (IItemHandler h : handlers) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty()) {
                    out.merge(Key.of(s), (long) s.getCount(), Long::sum);
                }
            }
        }
        return out;
    }

    /** Takes up to {@code count} of {@code key} out; the result may be smaller or empty. */
    ItemStack extract(Key key, int count) {
        int taken = 0;
        for (IItemHandler h : handlers) {
            for (int i = 0; i < h.getSlots() && taken < count; i++) {
                if (key.matches(h.getStackInSlot(i))) {
                    taken += h.extractItem(i, count - taken, false).getCount();
                }
            }
        }
        return taken == 0 ? ItemStack.EMPTY : key.stack(taken);
    }

    /** Puts {@code stack} in, onto matching stacks first; returns what did not fit. */
    ItemStack insert(ItemStack stack) {
        ItemStack rest = stack;
        for (IItemHandler h : handlers) {
            if (rest.isEmpty()) {
                break;
            }
            rest = ItemHandlerHelper.insertItemStacked(h, rest, false);
        }
        return rest;
    }
}
