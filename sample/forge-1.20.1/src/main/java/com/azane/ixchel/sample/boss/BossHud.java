package com.azane.ixchel.sample.boss;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.mc.UiHud;
import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.network.Replica;
import com.azane.ixchel.sample.network.Replication;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Boss bar sample: a dark HUD across the top for the nearest boss ({@link BossState}): a wide bar
 * whose trail shows the damage just taken, the phase marks, the shield over the bar, the title
 * glitching into the next phase, and the boss's effects with their time left. The boss is watched
 * from joining a world to leaving it; the HUD is hidden until toggled from the sample index.
 */
public final class BossHud {
    private static UiHud hud;
    private static Replica<BossState> replica;

    private BossHud() {
    }

    /** Client only; from client setup. */
    public static void register(IEventBus modBus) {
        hud = IxchelForge.registerHud(modBus, BossModel.class, BossModel::new).show(false);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) ->
                replica = Replication.watch(Network.BOSS, 0));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            if (replica != null) {
                replica.close();
                replica = null;
            }
        });
    }

    public static UiHud hud() {
        return hud;
    }

    static BossState state() {
        BossState s = replica == null ? null : replica.get();
        return s == null ? BossState.NONE : s;
    }
}
