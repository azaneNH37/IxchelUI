package com.azane.ixchel.sample.client.index;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The sample index: one row per {@link Sample}; HUD rows show whether the HUD is on. */
final class SampleIndexView extends ViewModel {
    record Row(String id, String title, String description, ItemStack icon, String state, boolean on) {
    }

    private final List<Sample> samples;
    /** Which HUDs are shown, in sample order; settings may change them too, so this is polled. */
    final Value<List<Boolean>> shown;
    final Derived<List<Row>> rows;
    final Action<Row> open;

    SampleIndexView(List<Sample> samples) {
        this.samples = List.copyOf(samples);
        shown = source(Source.polled(() -> this.samples.stream().map(s -> s.hud() != null && s.hud().shown()).toList()));
        rows = derive(shown, this::rows);
        open = action(row -> sample(row.id()).open().run());
    }

    private Sample sample(String id) {
        for (Sample s : samples) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        throw new IllegalArgumentException("no sample " + id);
    }

    private List<Row> rows(List<Boolean> shown) {
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            boolean on = shown.get(i);
            String state = s.hud() == null ? "" : on ? "HUD on" : "HUD off";
            out.add(new Row(s.id(), s.title(), s.description(), s.icon(), state, on));
        }
        return out;
    }
}
