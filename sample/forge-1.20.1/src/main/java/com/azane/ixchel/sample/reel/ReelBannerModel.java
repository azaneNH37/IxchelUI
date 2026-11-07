package com.azane.ixchel.sample.reel;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The title card shown, as a list of at most one: a new card has a new key, so the old one plays its
 * exit while the new one enters. The card leaves {@value #HOLD_MS} ms after it was shown, on the
 * game's clock, which a recording steps frame by frame.
 */
@UiView("ixchel_sample:ui/reel/banner.html")
final class ReelBannerModel extends ViewModel {
    static final long HOLD_MS = 2600;

    record Card(int key, Component title, Component subtitle, String counter, String theme) {
    }

    private Card card;
    private long shownAt;
    private int nextKey;

    final Cell<List<Card>> cards = source(ClientSources.polled(this::current));

    void show(String id, int n, int total, String theme) {
        card = new Card(nextKey++, Component.translatable("ixchel_sample.reel." + id + ".title"),
                Component.translatable("ixchel_sample.reel." + id + ".subtitle"),
                String.format("%02d / %02d", n, total), theme);
        shownAt = Util.getMillis();
    }

    private List<Card> current() {
        return card != null && Util.getMillis() - shownAt < HOLD_MS ? List.of(card) : List.of();
    }
}
