package com.azane.ixchel.sample.refit;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Stand-in for TACZ refit data: a crossbow is the gun, attachments and the laser color live in its
 * custom data. Installing consumes the attachment item from the inventory, removing gives it back.
 */
public final class Attachments {
    private static final String TAG = "ixchel_sample_refit";
    private static final String LASER = "laser";

    public record Option(String id, Item item) {
        public Component name() {
            return item.getDescription();
        }
    }

    public record Type(String id, Component name, List<Option> options) {
    }

    public static final List<Type> TYPES = List.of(
            new Type("scope", Component.literal("Scope"),
                    List.of(new Option("spyglass", Items.SPYGLASS), new Option("glass", Items.GLASS_PANE))),
            new Type("muzzle", Component.literal("Muzzle"),
                    List.of(new Option("bars", Items.IRON_BARS), new Option("blaze", Items.BLAZE_ROD))),
            new Type("grip", Component.literal("Grip"),
                    List.of(new Option("stick", Items.STICK), new Option("hook", Items.TRIPWIRE_HOOK))));

    /** Laser colors, shown as the dye of that color. */
    public static final List<Option> LASERS = List.of(
            new Option("red", Items.RED_DYE), new Option("lime", Items.LIME_DYE),
            new Option("blue", Items.BLUE_DYE), new Option("white", Items.WHITE_DYE));

    private Attachments() {
    }

    public static boolean isGun(ItemStack stack) {
        return stack.is(Items.CROSSBOW);
    }

    public static Type type(String id) {
        for (Type t : TYPES) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    public static Option option(List<Option> options, String id) {
        for (Option o : options) {
            if (o.id.equals(id)) {
                return o;
            }
        }
        return null;
    }

    /** The installed option id of {@code type}, or {@code ""}. */
    public static String installed(ItemStack gun, String type) {
        CustomData data = gun.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.isEmpty() ? "" : data.copyTag().getCompound(TAG).getString(type);
    }

    /** The laser color id, or {@code ""}. */
    public static String laser(ItemStack gun) {
        return installed(gun, LASER);
    }

    static void setInstalled(ItemStack gun, String type, String option) {
        CustomData.update(DataComponents.CUSTOM_DATA, gun, root -> {
            CompoundTag tag = root.getCompound(TAG);
            if (option.isEmpty()) {
                tag.remove(type);
            } else {
                tag.putString(type, option);
            }
            root.put(TAG, tag);
        });
    }

    static void setLaser(ItemStack gun, String color) {
        setInstalled(gun, LASER, color);
    }
}
