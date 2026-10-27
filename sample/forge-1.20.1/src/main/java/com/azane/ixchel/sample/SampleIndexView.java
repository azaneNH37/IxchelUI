package com.azane.ixchel.sample;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The sample index: one row per {@link Sample}; HUD rows show whether the HUD is on. */
final class SampleIndexView extends ViewModel {
    record Row(String id, String title, String description, ItemStack icon, String state, boolean on) {
    }

    private final List<Sample> samples;
    final Value<List<Row>> rows;
    final Action<Row> open;

    SampleIndexView(List<Sample> samples) {
        this.samples = List.copyOf(samples);
        rows = state(rows());
        open = action(row -> {
            Sample s = sample(row.id());
            if (s.hud()) {
                s.open().run();
                rows.set(rows());
            } else {
                // After the click is dispatched: opening a screen closes this one and its document.
                Minecraft.getInstance().tell(s.open());
            }
        });
    }

    private Sample sample(String id) {
        for (Sample s : samples) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        throw new IllegalArgumentException("no sample " + id);
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (Sample s : samples) {
            boolean on = s.hud() && Sample.shown(s.id());
            out.add(new Row(s.id(), s.title(), s.description(), s.icon(), s.hud() ? (on ? "HUD on" : "HUD off") : "",
                    on));
        }
        return out;
    }
}
