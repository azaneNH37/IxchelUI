package com.azane.ixchel.sample.pickup;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.check.Script;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Run with {@code -Pbench=pickup}: gives items on the server and checks that toasts appear, merge
 * for the same item, and expire.
 */
final class PickupCheck {
    private PickupCheck() {
    }

    static void init(IxchelForge.Hud hud) {
        Script s = Script.forBench(PickupHud.ID);
        if (s == null) {
            return;
        }
        s.step(40, () -> hud.show(true));
        s.step(10, () -> Script.onServer(p -> {
            p.getInventory().add(new ItemStack(Items.COBBLESTONE, 12));
            p.getInventory().add(new ItemStack(Items.OAK_LOG, 3));
        }));
        s.step(10, () -> Script.onServer(p -> p.getInventory().add(new ItemStack(Items.COBBLESTONE, 5))));
        s.step(10, () -> {
            s.expect("two toasts, cobblestone merged to +17", () -> count(hud, ".toast") == 2
                    && hud.document().querySelectorAll(".text").stream()
                            .anyMatch(e -> "+17 Cobblestone".equals(e.textContent())));
            Script.shot("pickup-toasts");
        });
        // Oak log shown at tick 50 fades from 2.4 s to 3 s; cobblestone restarted at tick 60.
        s.step(32, () -> {
            s.expect("the older toast fades in its last 600 ms", () -> count(hud, ".toast") == 2
                    && count(hud, ".toast.fading") == 1);
            Script.shot("pickup-fading");
        });
        s.step(38, () -> s.expect("toasts expire", () -> count(hud, ".toast") == 0));
        s.step(5, () -> hud.show(false));
        s.start();
    }

    private static int count(IxchelForge.Hud hud, String selector) {
        return hud.document().querySelectorAll(selector).size();
    }
}
