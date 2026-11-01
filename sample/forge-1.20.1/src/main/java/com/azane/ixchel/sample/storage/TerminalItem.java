package com.azane.ixchel.sample.storage;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** The storage terminal: use it to see every item handler within 8 blocks as one storage. */
public final class TerminalItem extends Item {
    public TerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer p) {
            open(p, hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /** @param hand the hand holding the terminal, or null to open it without one */
    static void open(ServerPlayer player, InteractionHand hand) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inventory, p) -> TerminalMenu.server(id, inventory, player, hand),
                Component.translatable("item.ixchel_sample.storage_terminal")));
    }
}
