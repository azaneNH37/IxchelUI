package com.azane.ixchel.sample.showcase;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;

/** The showcase's controls and the theme shown; picking another theme opens its page. */
@UiView("ixchel_sample:ui/showcase/themes/*.html")
final class ShowcaseModel extends ViewModel {
    final Value<String> theme;
    final Value<String> field = state("");
    final Value<Boolean> checked = state(true);
    final Value<String> level = state("4");
    final Value<String> choice = state("a");
    final Value<Boolean> dialog = state(false);
    final Action<String> pick;

    ShowcaseModel(String theme) {
        this.theme = state(theme);
        pick = action((String t) -> ShowcaseScreen.open(t));
    }
}
