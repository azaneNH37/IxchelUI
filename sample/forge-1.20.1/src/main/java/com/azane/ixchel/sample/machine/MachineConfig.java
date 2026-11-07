package com.azane.ixchel.sample.machine;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The machine's settings, which the screen's config page edits: the redstone mode the machine obeys,
 * and an auto-output switch and a priority that are stored and shown only, like the side modes. The
 * menu's open packet carries them so the page's controls start from the machine's values.
 */
public record MachineConfig(MachineState.Redstone redstone, boolean autoOutput, int priority) {
    public static final int PRIORITY_MAX = 10;

    static final MachineConfig DEFAULT = new MachineConfig(MachineState.Redstone.IGNORED, false, 0);

    /** {@code priority} held within 0 to {@link #PRIORITY_MAX}. */
    public MachineConfig {
        priority = Math.max(0, Math.min(PRIORITY_MAX, priority));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(redstone);
        buf.writeBoolean(autoOutput);
        buf.writeVarInt(priority);
    }

    public static MachineConfig decode(FriendlyByteBuf buf) {
        return new MachineConfig(buf.readEnum(MachineState.Redstone.class), buf.readBoolean(), buf.readVarInt());
    }
}
