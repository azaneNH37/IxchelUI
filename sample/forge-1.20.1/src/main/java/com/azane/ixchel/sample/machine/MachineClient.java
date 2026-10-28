package com.azane.ixchel.sample.machine;

import com.azane.ixchel.mc.UiContainerScreen;
import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.Sample;
import com.azane.ixchel.sample.check.Script;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.List;

/**
 * Machine panel sample (S2): the powered stonecutter's screen, a {@link UiContainerScreen} over
 * {@link MachineMenu}. Server state arrives as menu data, the screen answers with
 * {@code clickMenuButton} ids.
 */
public final class MachineClient {
    static final String ID = "machine";

    private MachineClient() {
    }

    /** Client only. */
    public static void init(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> MenuScreens.register(Machine.MENU.get(),
                (MachineMenu menu, Inventory inv, Component title) -> new UiContainerScreen<>(menu, inv, title,
                        IxchelSampleMod.MOD_ID, "ui/machine.html", List.of("ui/machine.css"), new MachineView(menu)))));
        MachineCheck.init();
    }

    /**
     * Places a machine two blocks east of the player when there is none, then opens it; on the
     * integrated server, as the sample index is a development tool.
     */
    static void openNearby() {
        Script.onServer(MachineClient::placeAndOpen);
    }

    static void placeAndOpen(ServerPlayer p) {
        BlockPos pos = p.blockPosition().east(2);
        if (!p.level().getBlockState(pos).is(Machine.BLOCK.get())) {
            p.level().setBlockAndUpdate(pos, Machine.BLOCK.get().defaultBlockState());
        }
        if (p.level().getBlockEntity(pos) instanceof MachineBlockEntity be) {
            MachineBlock.open(p, be);
        }
    }

    public static Sample sample() {
        return Sample.screen(ID, "Machine panel", "A powered stonecutter (Functional Blocks tab): fuel makes FE, a "
                + "water bucket fills the tank, pick a recipe; redstone mode and side configuration. Opening it here "
                + "places one next to you.", new ItemStack(Machine.ITEM.get()), MachineClient::openNearby);
    }
}
