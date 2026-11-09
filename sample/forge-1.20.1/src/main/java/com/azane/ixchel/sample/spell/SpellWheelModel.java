package com.azane.ixchel.sample.spell;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.mc.source.ClientSources;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The spell wheel: the sector the pointer points toward from the screen's center is the one
 * releasing the key casts. Everything changes every frame (the pointer, the cooldowns, the refilling
 * mana), so one polled snapshot carries it all. The sectors and runes are placed by hand around the
 * wheel's center, {@link #CENTER} pixels from its corner.
 */
@UiView("ixchel_sample:ui/spell/wheel.html")
final class SpellWheelModel extends ViewModel {
    /** Half the wheel's size in the page. */
    static final int CENTER = 110;
    private static final int SECTOR_RADIUS = 72;
    private static final int SECTOR_SIZE = 30;
    /** Pointer closer to the center than this aims at nothing. */
    private static final int DEAD_ZONE = 14;
    private static final String GLYPHS = "ixchelarcanumspellwheel";

    /**
     * A spell's sector. {@code cooldown}: the part of the cooldown left, 1 just cast to 0 ready;
     * {@code seconds} the time left as text, empty when ready.
     */
    record Sector(int index, ItemStack icon, Component name, int cost, int left, int top, boolean selected,
                  boolean affordable, float cooldown, String seconds) {
    }

    /** A rune on a ring, turned to read outward. */
    record Rune(int id, String glyph, int left, int top, int turn) {
    }

    /**
     * What the wheel shows this frame. {@code selected} is -1 aiming at nothing; {@code angle} points
     * the needle, degrees clockwise from up.
     */
    record Wheel(List<Sector> sectors, int selected, int angle, float mana, String manaText, boolean blocked) {
    }

    private final SpellBook book;
    final Cell<Wheel> wheel;
    final Value<List<Rune>> outerRunes = state(runes(24, 100, 0));
    final Value<List<Rune>> innerRunes = state(runes(14, 44, 100));
    final Value<Integer> manaMax = state(SpellBook.MANA_MAX);
    final Action<Sector> pick = action(s -> {
        cast(s.index());
        SpellWheel.close();
    });

    SpellWheelModel(SpellBook book) {
        this.book = book;
        this.wheel = source(ClientSources.polled(this::read));
    }

    /** Casts the sector pointed at; from the key's release. */
    void castPointed() {
        cast(pointed());
    }

    private void cast(int i) {
        if (i >= 0 && book.cast(i, Util.getMillis())) {
            SpellWheel.castSound();
        }
    }

    private Wheel read() {
        long now = Util.getMillis();
        int selected = pointed();
        List<Sector> sectors = new ArrayList<>(SpellBook.SPELLS.size());
        for (int i = 0; i < SpellBook.SPELLS.size(); i++) {
            SpellBook.Spell s = SpellBook.SPELLS.get(i);
            double a = Math.toRadians(i * 360.0 / SpellBook.SPELLS.size());
            long left = book.cooldownLeft(i, now);
            sectors.add(new Sector(i, new ItemStack(s.icon()), Component.translatable("ixchel_sample.spell." + s.id()),
                    s.cost(), (int) Math.round(CENTER + SECTOR_RADIUS * Math.sin(a)) - SECTOR_SIZE / 2,
                    (int) Math.round(CENTER - SECTOR_RADIUS * Math.cos(a)) - SECTOR_SIZE / 2, i == selected,
                    book.mana(now) >= s.cost(), left / (float) s.cooldownMs(),
                    left == 0 ? "" : String.format(Locale.ROOT, "%.1f", left / 1000f)));
        }
        float mana = book.mana(now);
        return new Wheel(sectors, selected, (int) Math.round(angle()), mana,
                (int) mana + " / " + SpellBook.MANA_MAX, selected >= 0 && !book.canCast(selected, now));
    }

    /** The pointer relative to the screen's center, in GUI pixels. */
    private static double[] pointer() {
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        double x = mc.mouseHandler.xpos() * w.getGuiScaledWidth() / w.getScreenWidth();
        double y = mc.mouseHandler.ypos() * w.getGuiScaledHeight() / w.getScreenHeight();
        return new double[] {x - w.getGuiScaledWidth() / 2.0, y - w.getGuiScaledHeight() / 2.0};
    }

    private static double angle() {
        double[] p = pointer();
        double deg = Math.toDegrees(Math.atan2(p[0], -p[1]));
        return deg < 0 ? deg + 360 : deg;
    }

    private static int pointed() {
        double[] p = pointer();
        if (Math.hypot(p[0], p[1]) < DEAD_ZONE) {
            return -1;
        }
        int n = SpellBook.SPELLS.size();
        return (int) Math.round(angle() / (360.0 / n)) % n;
    }

    private static List<Rune> runes(int count, int radius, int firstId) {
        List<Rune> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double deg = i * 360.0 / count;
            double a = Math.toRadians(deg);
            out.add(new Rune(firstId + i, String.valueOf(GLYPHS.charAt(i % GLYPHS.length())),
                    (int) Math.round(CENTER + radius * Math.sin(a)) - 3, (int) Math.round(CENTER - radius * Math.cos(a)) - 4,
                    (int) Math.round(deg)));
        }
        return out;
    }
}
