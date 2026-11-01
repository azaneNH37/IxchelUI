package com.azane.ixchel.sample.machine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * What a player near a powered stonecutter sees of it, replicated to the machine screen
 * ({@code Network.MACHINE}). The item slots are not here: they go through the menu's vanilla slot
 * transactions.
 *
 * @param recipe the chosen stonecutting recipe index for the current input, or {@code -1}
 * @param sides  one mode per side, in {@code Direction} order (down, up, north, south, west, east)
 */
public record MachineState(int progress, int energy, int water, int burn, int burnTotal, int recipe,
                           Status status, Redstone redstone, List<SideMode> sides) {
    public enum Status {
        NO_INPUT(false),
        PICK_RECIPE(false),
        NO_ENERGY(true),
        NO_WATER(true),
        OUTPUT_FULL(true),
        REDSTONE_OFF(true),
        WORKING(false);

        /** Shown as a warning: the machine has a recipe but cannot run. */
        final boolean bad;

        Status(boolean bad) {
            this.bad = bad;
        }

        Component label() {
            return Component.translatable("ixchel_sample.machine.status." + name().toLowerCase(Locale.ROOT));
        }
    }

    public enum Redstone {
        IGNORED, HIGH, LOW;

        Component label() {
            return Component.translatable("ixchel_sample.machine.redstone." + name().toLowerCase(Locale.ROOT));
        }

        Redstone next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Side modes are stored and shown only; nothing pipes through them. */
    public enum SideMode {
        NONE, INPUT, OUTPUT;

        Component label() {
            return Component.translatable("ixchel_sample.machine.side." + name().toLowerCase(Locale.ROOT));
        }

        SideMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Until the first snapshot arrives. */
    static final MachineState NONE = new MachineState(0, 0, 0, 0, 0, -1, Status.NO_INPUT, Redstone.IGNORED,
            List.of(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE));

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(progress).writeVarInt(energy).writeVarInt(water).writeVarInt(burn).writeVarInt(burnTotal)
                .writeVarInt(recipe);
        buf.writeEnum(status);
        buf.writeEnum(redstone);
        sides.forEach(buf::writeEnum);
    }

    public static MachineState decode(FriendlyByteBuf buf) {
        int progress = buf.readVarInt(), energy = buf.readVarInt(), water = buf.readVarInt(), burn = buf.readVarInt(),
                burnTotal = buf.readVarInt(), recipe = buf.readVarInt();
        Status status = buf.readEnum(Status.class);
        Redstone redstone = buf.readEnum(Redstone.class);
        SideMode[] sides = new SideMode[6];
        for (int i = 0; i < sides.length; i++) {
            sides[i] = buf.readEnum(SideMode.class);
        }
        return new MachineState(progress, energy, water, burn, burnTotal, recipe, status, redstone, List.of(sides));
    }
}
