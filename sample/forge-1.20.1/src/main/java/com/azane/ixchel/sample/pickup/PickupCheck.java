package com.azane.ixchel.sample.pickup;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.Script;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@code -Pcheck=pickup}: gives items on the server and checks that toasts appear, merge for the same
 * item, and expire.
 */
final class PickupCheck {
    private PickupCheck() {
    }

    static void init(IxchelForge.Hud hud) {
        Checks.register(PickupHud.ID, s -> s
                .run(() -> hud.show(true))
                .server(p -> {
                    p.getInventory().add(new ItemStack(Items.COBBLESTONE, 12));
                    p.getInventory().add(new ItemStack(Items.OAK_LOG, 3));
                })
                .waitUntil("two toasts", () -> count(hud, ".toast") == 2, Script.WAIT)
                .pause(10)
                .server(p -> p.getInventory().add(new ItemStack(Items.COBBLESTONE, 5)))
                .waitUntil("cobblestone merged to +17", () -> count(hud, ".toast") == 2
                        && hud.document().querySelectorAll(".text").stream()
                                .anyMatch(e -> "+17 Cobblestone".equals(e.textContent())), Script.WAIT)
                .shot("pickup-toasts")
                // Toasts show for 3 s and fade in the last 600 ms. The oak log toast is now about 0.6 s
                // old, the merged cobblestone one restarted: 1.9 s later only the oak log fades.
                .pause(38)
                .expect("the older toast fades in its last 600 ms", () -> count(hud, ".toast") == 2
                        && count(hud, ".toast.fading") == 1)
                .shot("pickup-fading")
                .waitUntil("toasts expire", () -> count(hud, ".toast") == 0, 40)
                .run(() -> hud.show(false)));
    }

    private static int count(IxchelForge.Hud hud, String selector) {
        return hud.document().querySelectorAll(selector).size();
    }
}
