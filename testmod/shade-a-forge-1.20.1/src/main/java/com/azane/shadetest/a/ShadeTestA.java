package com.azane.shadetest.a;

import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.mc.ClientSources;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

@Mod(ShadeTestA.MOD_ID)
public final class ShadeTestA {
    public static final String MOD_ID = "ixchelshadea";

    public ShadeTestA(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist.isClient()) {
            // Shows which copy this mod runs: the relocated name and the jar it came from.
            LogUtils.getLogger().info("[ixchel-shade] {} uses {} from {}", MOD_ID, IxchelForge.class.getName(),
                    IxchelForge.class.getProtectionDomain().getCodeSource().getLocation());
            IxchelForge.registerHud(context.getModEventBus(), MOD_ID, "hud", "ui/hud.html", List.of("ui/hud.css"),
                    HudView::new);
        }
    }

    static final class HudView extends ViewModel {
        final Value<ItemStack> held = source(ClientSources.mainHand());
        final Derived<String> count = derive(held, s -> "A x" + s.getCount());
    }
}
