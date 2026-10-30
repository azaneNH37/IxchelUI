package com.azane.ixchel.sample.character;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * The character panel: four tabs over the player's inventory menu. Equipment holds the paper doll,
 * armor, offhand and the 2×2 crafting grid; attributes, effects and statistics are read from the local
 * player every frame. Statistics are asked of the server when their tab opens; the reply updates the
 * player's stats counter, which the next frame reads.
 */
final class CharacterView extends ViewModel {
    record Tab(String id, String label) {
    }

    /** One attribute; {@code description} is the row's tooltip. */
    record AttributeRow(String name, String value, String description) {
    }

    /**
     * One active effect. {@code ending} in its last ten seconds, when {@code dim} turns on and off
     * every half second so the row blinks.
     */
    record EffectRow(String id, Component name, Component time, String icon, boolean ending, boolean dim) {
    }

    record StatRow(String name, String value) {
    }

    private static final String[][] TABS = {
            {"equipment", "Equipment"}, {"attributes", "Attributes"}, {"effects", "Effects"}, {"stats", "Stats"}};

    private static final Object[][] ATTRIBUTES = {
            {Attributes.MAX_HEALTH, "Health you have when fully healed, in half hearts."},
            {Attributes.ARMOR, "Reduces damage from most attacks; each point is half an armor icon."},
            {Attributes.ARMOR_TOUGHNESS, "Keeps armor effective against strong hits."},
            {Attributes.ATTACK_DAMAGE, "Damage of a fully charged melee hit with what you hold."},
            {Attributes.ATTACK_SPEED, "Full-strength attacks per second."},
            {Attributes.MOVEMENT_SPEED, "Walking speed; 0.1 is the default."},
            {Attributes.LUCK, "Improves loot table rolls, such as fishing."},
    };

    private static final Object[][] STATS = {
            {Stats.PLAY_TIME, "Time played"}, {Stats.DEATHS, "Deaths"}, {Stats.MOB_KILLS, "Mobs killed"},
            {Stats.WALK_ONE_CM, "Distance walked"}, {Stats.JUMP, "Jumps"}, {Stats.DAMAGE_DEALT, "Damage dealt"},
    };

    final Value<String> tab = state("equipment");
    final Value<List<Tab>> tabs = state(tabs());
    final Value<List<Integer>> inventory = state(range(InventoryMenu.INV_SLOT_START, InventoryMenu.INV_SLOT_END));
    final Value<List<Integer>> hotbar = state(range(InventoryMenu.USE_ROW_SLOT_START, InventoryMenu.USE_ROW_SLOT_END));

    /** A stable handle: the painter reads the player at paint time, so the binding never changes. */
    final Value<Supplier<LivingEntity>> doll = state(() -> Minecraft.getInstance().player);
    final Cell<List<AttributeRow>> attributes = source(ClientSources.player(CharacterView::attributes, List.of()));
    final Cell<List<EffectRow>> effects = source(ClientSources.player(CharacterView::effects, List.of()));
    final Cell<List<StatRow>> stats = source(ClientSources.player(CharacterView::stats, List.of()));

    final Action<Tab> select = action(t -> {
        tab.set(t.id());
        if (t.id().equals("stats")) {
            ClientPacketListener c = Minecraft.getInstance().getConnection();
            if (c != null) {
                c.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
            }
        }
    });

    private static List<Tab> tabs() {
        List<Tab> out = new ArrayList<>(TABS.length);
        for (String[] t : TABS) {
            out.add(new Tab(t[0], t[1]));
        }
        return out;
    }

    private static List<Integer> range(int from, int to) {
        return IntStream.range(from, to).boxed().toList();
    }

    private static List<AttributeRow> attributes(LocalPlayer p) {
        List<AttributeRow> out = new ArrayList<>(ATTRIBUTES.length);
        for (Object[] a : ATTRIBUTES) {
            Attribute attribute = (Attribute) a[0];
            double v = p.getAttributeValue(attribute);
            out.add(new AttributeRow(Component.translatable(attribute.getDescriptionId()).getString(),
                    String.format(Locale.ROOT, v == Math.rint(v) ? "%.0f" : "%.2f", v), (String) a[1]));
        }
        return out;
    }

    private static List<EffectRow> effects(LocalPlayer p) {
        List<EffectRow> out = new ArrayList<>();
        for (MobEffectInstance e : p.getActiveEffects()) {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect());
            Component name = e.getAmplifier() == 0 ? e.getEffect().getDisplayName()
                    : e.getEffect().getDisplayName().copy()
                            .append(" ").append(Component.translatable("enchantment.level." + (e.getAmplifier() + 1)));
            boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200;
            out.add(new EffectRow(id.toString(), name, MobEffectUtil.formatDuration(e, 1f),
                    id.getNamespace() + ":textures/mob_effect/" + id.getPath() + ".png",
                    ending, ending && e.getDuration() / 10 % 2 == 0));
        }
        return out;
    }

    private static List<StatRow> stats(LocalPlayer p) {
        List<StatRow> out = new ArrayList<>(STATS.length);
        for (Object[] s : STATS) {
            Stat<ResourceLocation> stat = Stats.CUSTOM.get((ResourceLocation) s[0]);
            out.add(new StatRow((String) s[1], stat.format(p.getStats().getValue(stat))));
        }
        return out;
    }
}
