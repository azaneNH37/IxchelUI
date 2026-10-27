package com.azane.ixchel.sample.status;

import com.azane.ixchel.core.binding.Derived;
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

/**
 * The status HUD: hearts, armor and food as vanilla icon rows, the experience bar, and the active
 * effects with their time left. Every number is read from the local player through a source.
 */
final class StatusView extends ViewModel {
    /** One icon of a row of ten (hearts, armor, food), each standing for two points. */
    record Icon(int index, boolean full, boolean half) {
    }

    /** One active effect; {@code ending} in its last ten seconds. */
    record Effect(String id, Component name, Component time, String icon, boolean ending) {
    }

    final Value<Float> health = source(ClientSources.player(LocalPlayer::getHealth, 0f));
    final Value<Float> maxHealth = source(ClientSources.player(LocalPlayer::getMaxHealth, 20f));
    final Value<Integer> armor = source(ClientSources.player(LocalPlayer::getArmorValue, 0));
    final Value<Integer> food = source(ClientSources.player(p -> p.getFoodData().getFoodLevel(), 0));
    final Value<Integer> level = source(ClientSources.player(p -> p.experienceLevel, 0));
    final Value<Float> progress = source(ClientSources.player(p -> p.experienceProgress, 0f));
    final Value<List<Effect>> effects = source(ClientSources.player(StatusView::effects, List.of()));

    final Derived<List<Icon>> hearts = derive(health, maxHealth, (h, max) -> icons(Math.round(h), (int) Math.ceil(max)));
    final Derived<String> healthText = derive(health, maxHealth, (h, max) -> Math.round(h) + " / " + Math.round(max));
    final Derived<Boolean> low = derive(health, maxHealth, (h, max) -> h <= max * 0.3f);
    final Derived<List<Icon>> armorIcons = derive(armor, a -> icons(a, 20));
    final Derived<Boolean> hasArmor = derive(armor, a -> a > 0);
    final Derived<List<Icon>> foodIcons = derive(food, f -> icons(f, 20));
    final Derived<String> levelText = derive(level, String::valueOf);
    final Derived<String> progressWidth = derive(progress, p -> p * 100 + "%");
    final Derived<Boolean> hasEffects = derive(effects, e -> !e.isEmpty());

    /** {@code points} out of {@code max} as icons of two points each. */
    private static List<Icon> icons(int points, int max) {
        List<Icon> out = new ArrayList<>();
        for (int i = 0; i < (max + 1) / 2; i++) {
            int left = points - i * 2;
            out.add(new Icon(i, left >= 2, left == 1));
        }
        return out;
    }

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
