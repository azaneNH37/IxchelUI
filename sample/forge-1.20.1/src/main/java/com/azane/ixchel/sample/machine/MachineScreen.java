package com.azane.ixchel.sample.machine;

import com.azane.ixchel.mc.UiContainerScreen;
import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.registry.ModBlocks;
import com.azane.ixchel.sample.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;

/**
 * Machine panel sample (S2): the powered stonecutter's screen, a {@link UiContainerScreen} over
 * {@link MachineMenu}. Items move through the menu's vanilla slots; the machine's state arrives as a
 * replicated {@link MachineState} and settings go back as intent packets. The block, item, block
 * entity and menu are registered in {@code registry}.
 */
public final class MachineScreen {
    private MachineScreen() {
    }

    /** Client only; from client setup, after registries are ready. */
    public static void registerScreen() {
        MenuScreens.register(ModMenus.POWERED_STONECUTTER.get(), (MachineMenu menu, Inventory inv, Component title) ->
                new UiContainerScreen<>(menu, inv, title, new MachineView(menu)));
    }

    /**
     * Asks the server to place a machine two blocks east of the player when there is none, and to
     * open it ({@link OpenMachinePacket}); the sample index's entry.
     */
    public static void openNearby() {
        Network.sendToServer(new OpenMachinePacket());
    }

    /** Server side: places a machine two blocks east of {@code player} when there is none, opens it, and returns where it is. */
    public static BlockPos placeAndOpen(ServerPlayer player) {
        BlockPos pos = player.blockPosition().east(2);
        if (!player.level().getBlockState(pos).is(ModBlocks.POWERED_STONECUTTER.get())) {
            player.level().setBlockAndUpdate(pos, ModBlocks.POWERED_STONECUTTER.get().defaultBlockState());
        }
        if (player.level().getBlockEntity(pos) instanceof MachineBlockEntity be) {
            MachineBlock.open(player, be);
        }
        return pos;
    }

    /** The four machine slots (fuel, water, input, output) of the machine at {@code pos}, or {@code null} if there is none. */
    public static Container contents(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MachineBlockEntity be ? be.items : null;
    }
}
