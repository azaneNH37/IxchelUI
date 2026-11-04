package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.UiView;
import net.minecraft.resources.ResourceLocation;

/** The page a view model class declares, for the hosts mounted without a page id. */
final class DeclaredView {
    private DeclaredView() {
    }

    /**
     * The one concrete page {@code type}'s {@link UiView} names.
     *
     * @throws IllegalArgumentException if {@code type} declares none, several or a pattern
     * @throws net.minecraft.ResourceLocationException if the declared id is malformed
     */
    static ResourceLocation of(Class<?> type) {
        return ResourceLocation.parse(UiView.Ids.single(type));
    }
}
