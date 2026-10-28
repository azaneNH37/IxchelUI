package com.azane.ixchel.sample.machine;

import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

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
        NO_INPUT("Idle: no input", false),
        PICK_RECIPE("Pick a recipe", false),
        NO_ENERGY("No energy", true),
        NO_WATER("No water", true),
        OUTPUT_FULL("Output full", true),
        REDSTONE_OFF("Redstone off", true),
        WORKING("Working", false);

        final String label;
        /** Shown as a warning: the machine has a recipe but cannot run. */
        final boolean bad;

        Status(String label, boolean bad) {
            this.label = label;
            this.bad = bad;
        }
    }

    public enum Redstone {
        IGNORED("Ignored"), HIGH("Needs signal"), LOW("Needs no signal");

        final String label;

        Redstone(String label) {
            this.label = label;
        }

        Redstone next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Side modes are stored and shown only; nothing pipes through them. */
    public enum SideMode {
        NONE("None"), INPUT("Input"), OUTPUT("Output");

        final String label;

        SideMode(String label) {
            this.label = label;
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
