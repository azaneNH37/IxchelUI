package com.azane.ixchel.sample.status;

import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.ClientSources;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

import java.util.ArrayList;
import java.util.List;

/** The active effects with their time left, read from the local player through a source. */
final class StatusView extends ViewModel {
    /** One active effect; {@code ending} in its last ten seconds. */
    record Effect(String id, Component name, Component time, String icon, boolean ending) {
    }

    final Value<List<Effect>> effects = source(ClientSources.player(StatusView::effects, List.of()));

    private static List<Effect> effects(LocalPlayer p) {
        List<Effect> out = new ArrayList<>();
        for (MobEffectInstance e : p.getActiveEffects()) {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect());
            Component name = e.getAmplifier() == 0 ? e.getEffect().getDisplayName()
                    : e.getEffect().getDisplayName().copy()
                            .append(" ").append(Component.translatable("enchantment.level." + (e.getAmplifier() + 1)));
            out.add(new Effect(id.toString(), name, MobEffectUtil.formatDuration(e, 1f),
                    id.getNamespace() + ":textures/mob_effect/" + id.getPath() + ".png",
                    !e.isInfiniteDuration() && e.getDuration() < 200));
        }
        return out;
    }
}
