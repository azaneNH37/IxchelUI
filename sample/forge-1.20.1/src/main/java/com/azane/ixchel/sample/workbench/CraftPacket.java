package com.azane.ixchel.sample.workbench;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The workbench's craft request: the server crafts recipe {@code recipe} up to {@code times} times
 * from the player's inventory, each time only if every ingredient is there, and hands over the
 * results; the inventory sync carries the outcome back, so there is no reply.
 */
public record CraftPacket(ResourceLocation recipe, int times) {
    /** Most crafts one request asks for, as the stepper allows. */
    public static final int MAX_TIMES = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(recipe);
        buf.writeVarInt(times);
    }

    public static CraftPacket decode(FriendlyByteBuf buf) {
        return new CraftPacket(buf.readResourceLocation(), buf.readVarInt());
    }

    public void handle(ServerPlayer player) {
        if (!(player.server.getRecipeManager().byKey(recipe).orElse(null) instanceof CraftingRecipe crafting)) {
            return;
        }
        Inventory inv = player.getInventory();
        for (int t = 0; t < Math.min(times, MAX_TIMES) && takeOnce(inv, crafting); t++) {
            ItemStack result = crafting.getResultItem(player.server.registryAccess()).copy();
            if (!inv.add(result)) {
                player.drop(result, false);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    /** Takes one of each ingredient from the main inventory, or nothing if one is missing. */
    private static boolean takeOnce(Inventory inv, CraftingRecipe recipe) {
        int[] taken = new int[inv.items.size()];
        for (Ingredient in : recipe.getIngredients()) {
            if (in.isEmpty()) {
                continue;
            }
            int slot = -1;
            for (int i = 0; i < inv.items.size() && slot < 0; i++) {
                ItemStack s = inv.items.get(i);
                if (s.getCount() > taken[i] && in.test(s)) {
                    slot = i;
                }
            }
            if (slot < 0) {
                return false;
            }
            taken[slot]++;
        }
        for (int i = 0; i < taken.length; i++) {
            if (taken[i] > 0) {
                ItemStack s = inv.items.get(i);
                ItemStack remainder = s.hasCraftingRemainingItem() ? s.getCraftingRemainingItem() : ItemStack.EMPTY;
                s.shrink(taken[i]);
                if (!remainder.isEmpty()) {
                    remainder.setCount(taken[i]);
                    if (!inv.add(remainder)) {
                        inv.player.drop(remainder, false);
                    }
                }
            }
        }
        return true;
    }
}
